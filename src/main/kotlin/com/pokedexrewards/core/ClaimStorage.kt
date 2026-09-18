package com.pokedexrewards.core

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.bson.Document
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ClaimOutcome { CLAIMED, ALREADY_CLAIMED, STORAGE_ERROR }

/**
 * Onde os resgates ficam guardados.
 *
 * [tryClaim] e o ponto critico: ele precisa ser atomico. Numa rede com varios
 * servidores, dois deles podem tentar registrar o mesmo premio ao mesmo tempo,
 * e so um pode ganhar. Checar antes e gravar depois nao serve — abre janela
 * para o premio sair duas vezes.
 */
interface ClaimStorage {

    /** Texto curto pro log dizendo onde os resgates estao indo. */
    val description: String

    /** Se os resgates valem para a rede toda ou so para este mundo. */
    val isShared: Boolean

    fun load(playerId: UUID): MutableSet<Int>

    /** Registra o resgate. So retorna CLAIMED se foi ESTE servidor que registrou. */
    fun tryClaim(playerId: UUID, tierPercent: Int): ClaimOutcome

    fun reset(playerId: UUID)

    fun flush() {}

    fun close() {}
}

/**
 * Arquivo na pasta do mundo. Serve para servidor unico.
 *
 * Nao use em rede: cada mundo tem o seu arquivo, entao o jogador consegue
 * resgatar o mesmo premio em cada servidor.
 */
class JsonClaimStorage(server: MinecraftServer) : ClaimStorage {

    private val file: Path = server.getWorldPath(LevelResource.ROOT)
        .resolve("pokedexrewards")
        .resolve("claims.json")

    private val claims = HashMap<UUID, MutableSet<Int>>()

    override val description = "arquivo local ($file)"
    override val isShared = false

    init {
        readFile()
    }

    private fun readFile() {
        if (Files.notExists(file)) return
        try {
            val type = object : TypeToken<Map<String, List<Int>>>() {}.type
            val raw: Map<String, List<Int>> =
                Files.newBufferedReader(file).use { GSON.fromJson(it, type) } ?: emptyMap()
            raw.forEach { (key, tiers) ->
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
                claims[uuid] = tiers.toMutableSet()
            }
        } catch (e: Exception) {
            LOGGER.error("Nao consegui ler claims.json, comecando vazio. Erro: {}", e.message)
        }
    }

    override fun load(playerId: UUID): MutableSet<Int> = (claims[playerId] ?: emptySet<Int>()).toMutableSet()

    @Synchronized
    override fun tryClaim(playerId: UUID, tierPercent: Int): ClaimOutcome {
        val tiers = claims.getOrPut(playerId) { mutableSetOf() }
        if (!tiers.add(tierPercent)) return ClaimOutcome.ALREADY_CLAIMED
        return if (writeFile()) ClaimOutcome.CLAIMED else {
            tiers.remove(tierPercent)
            ClaimOutcome.STORAGE_ERROR
        }
    }

    @Synchronized
    override fun reset(playerId: UUID) {
        claims.remove(playerId)
        writeFile()
    }

    override fun flush() {
        writeFile()
    }

    private fun writeFile(): Boolean = try {
        file.parent?.let { Files.createDirectories(it) }
        val raw = claims.entries.associate { (uuid, tiers) -> uuid.toString() to tiers.sorted() }
        val tmp = file.resolveSibling("claims.json.tmp")
        Files.writeString(tmp, GSON.toJson(raw))
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (e: Exception) {
        LOGGER.error("Nao consegui salvar claims.json: {}", e.message)
        false
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("PokedexRewards/Claims")
        val GSON = GsonBuilder().setPrettyPrinting().create()
    }
}

/**
 * MongoDB compartilhado entre todos os servidores da rede.
 *
 * Usa o mesmo banco que o Cobblemon ja usa quando `storageFormat` esta em
 * "mongodb", entao nao precisa de infraestrutura nova.
 */
class MongoClaimStorage(
    connectionString: String,
    databaseName: String,
    collectionName: String
) : ClaimStorage {

    private val client: MongoClient
    private val collection: MongoCollection<Document>

    override val description = "MongoDB ($databaseName.$collectionName)"
    override val isShared = true

    init {
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            // Sem isso o driver espera 30s antes de desistir, e o clique no menu
            // trava a thread principal do servidor esse tempo todo.
            .applyToClusterSettings { it.serverSelectionTimeout(5, TimeUnit.SECONDS) }
            .build()
        client = MongoClients.create(settings)
        collection = client.getDatabase(databaseName).getCollection(collectionName)
    }

    override fun load(playerId: UUID): MutableSet<Int> = try {
        val doc = collection.find(Filters.eq(ID, playerId.toString())).first()
        val raw = doc?.get(FIELD, List::class.java)
        raw?.filterIsInstance<Number>()?.map { it.toInt() }?.toMutableSet() ?: mutableSetOf()
    } catch (e: Exception) {
        LOGGER.error("Falha ao ler os resgates de {} no Mongo: {}", playerId, e.message)
        mutableSetOf()
    }

    /**
     * Uma operacao atomica so. `$addToSet` nao duplica, e `modifiedCount` conta
     * 1 apenas quando o valor realmente entrou — que e exatamente a pergunta
     * "fui eu quem registrou?". Se dois servidores chamarem junto, so um recebe
     * modifiedCount 1.
     */
    override fun tryClaim(playerId: UUID, tierPercent: Int): ClaimOutcome = try {
        val result = collection.updateOne(
            Filters.eq(ID, playerId.toString()),
            Updates.addToSet(FIELD, tierPercent),
            UpdateOptions().upsert(true)
        )
        when {
            !result.wasAcknowledged() -> ClaimOutcome.STORAGE_ERROR
            result.upsertedId != null -> ClaimOutcome.CLAIMED
            result.modifiedCount > 0L -> ClaimOutcome.CLAIMED
            else -> ClaimOutcome.ALREADY_CLAIMED
        }
    } catch (e: Exception) {
        LOGGER.error("Falha ao registrar o resgate de {}% para {}: {}", tierPercent, playerId, e.message)
        ClaimOutcome.STORAGE_ERROR
    }

    override fun reset(playerId: UUID) {
        try {
            collection.deleteOne(Filters.eq(ID, playerId.toString()))
        } catch (e: Exception) {
            LOGGER.error("Falha ao resetar os resgates de {}: {}", playerId, e.message)
        }
    }

    override fun close() {
        runCatching { client.close() }
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("PokedexRewards/Claims")
        const val ID = "_id"
        const val FIELD = "claimed"
    }
}

/**
 * Usado quando o Mongo foi pedido mas nao deu para conectar.
 *
 * Recusa todo resgate de proposito. Cair para o arquivo local aqui seria pior:
 * silenciosamente devolveria o bug de resgatar o mesmo premio em cada servidor,
 * sem ninguem perceber.
 */
class DisabledClaimStorage(private val reason: String) : ClaimStorage {

    override val description = "DESABILITADO ($reason)"
    override val isShared = false

    override fun load(playerId: UUID): MutableSet<Int> = mutableSetOf()

    override fun tryClaim(playerId: UUID, tierPercent: Int) = ClaimOutcome.STORAGE_ERROR

    override fun reset(playerId: UUID) {}
}
