package com.pokedexrewards.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

/** Recompensas por faixa da Pokedex. */
interface ClaimStorage {
    fun loadTierClaims(playerId: UUID): MutableSet<Int>

    /** So retorna CLAIMED se foi ESTE servidor que registrou. */
    fun tryClaimTier(playerId: UUID, tierPercent: Int): ClaimOutcome
}

/** Progresso e resgate das missoes diarias, semanais e mensais. */
interface MissionStorage {
    fun loadMissionProgress(playerId: UUID): MutableMap<String, Int>

    fun loadMissionClaims(playerId: UUID): MutableSet<String>

    /**
     * Soma [amount] no progresso. Chamado a cada captura, entao roda fora da
     * thread principal e nao devolve nada: quem manda na exibicao e o cache.
     */
    fun incrementMission(playerId: UUID, missionKey: String, amount: Int)

    fun tryClaimMission(playerId: UUID, missionKey: String): ClaimOutcome
}

/** Diario de capturas: onde e quando o jogador pegou cada especie. */
interface CaptureStorage {
    fun loadCaptures(playerId: UUID): MutableMap<String, CaptureRecord>

    fun recordCapture(playerId: UUID, speciesId: String, record: CaptureRecord)
}

/**
 * Tudo que este mod guarda por jogador.
 *
 * Os `tryClaim*` precisam ser atomicos. Numa rede com varias instancias, checar
 * antes e gravar depois abre janela para a recompensa sair duas vezes — por isso
 * quem decide e o armazenamento, nao o cache.
 */
interface RewardsStorage : ClaimStorage, MissionStorage, CaptureStorage {

    /** Texto curto pro log dizendo onde os dados estao indo. */
    val description: String

    /** Se os dados valem para a rede toda ou so para este mundo. */
    val isShared: Boolean

    fun reset(playerId: UUID)

    fun flush() {}

    fun close() {}
}

// ---------------------------------------------------------------- arquivo local

/**
 * Arquivo na pasta do mundo. Serve para servidor unico.
 *
 * Nao use em rede: cada mundo tem o seu arquivo, entao o jogador consegue
 * resgatar o mesmo premio em cada servidor.
 */
class JsonRewardsStorage(server: MinecraftServer) : RewardsStorage {

    // LevelResource.ROOT e ".", entao sem normalize o caminho sai com um "./"
    // sobrando no meio e fica feio no log.
    private val file: Path = server.getWorldPath(LevelResource.ROOT)
        .resolve("pokedexrewards")
        .resolve("claims.json")
        .normalize()

    private class Entry {
        val tiers = mutableSetOf<Int>()
        val missions = mutableMapOf<String, Int>()
        val missionClaims = mutableSetOf<String>()
        val captures = mutableMapOf<String, CaptureRecord>()
    }

    private val players = HashMap<UUID, Entry>()

    private var lastProgressWrite = 0L
    private var progressPending = false

    override val description = "arquivo local ($file)"
    override val isShared = false

    init {
        readFile()
    }

    private fun entryOf(playerId: UUID): Entry = players.getOrPut(playerId) { Entry() }

    override fun loadTierClaims(playerId: UUID): MutableSet<Int> =
        players[playerId]?.tiers?.toMutableSet() ?: mutableSetOf()

    @Synchronized
    override fun tryClaimTier(playerId: UUID, tierPercent: Int): ClaimOutcome {
        val entry = entryOf(playerId)
        if (!entry.tiers.add(tierPercent)) return ClaimOutcome.ALREADY_CLAIMED
        return if (writeFile()) ClaimOutcome.CLAIMED else {
            entry.tiers.remove(tierPercent)
            ClaimOutcome.STORAGE_ERROR
        }
    }

    override fun loadMissionProgress(playerId: UUID): MutableMap<String, Int> =
        players[playerId]?.missions?.toMutableMap() ?: mutableMapOf()

    override fun loadMissionClaims(playerId: UUID): MutableSet<String> =
        players[playerId]?.missionClaims?.toMutableSet() ?: mutableSetOf()

    @Synchronized
    override fun incrementMission(playerId: UUID, missionKey: String, amount: Int) {
        val entry = entryOf(playerId)
        entry.missions[missionKey] = (entry.missions[missionKey] ?: 0) + amount
        // Progresso muda a cada captura. Reescrever o arquivo inteiro toda vez
        // seria caro com muita gente online, entao aqui vai com intervalo.
        // Resgate continua gravando na hora, que e o que nao pode se perder.
        val now = System.currentTimeMillis()
        if (now - lastProgressWrite >= PROGRESS_WRITE_INTERVAL_MS) {
            lastProgressWrite = now
            writeFile()
        } else {
            progressPending = true
        }
    }

    @Synchronized
    override fun tryClaimMission(playerId: UUID, missionKey: String): ClaimOutcome {
        val entry = entryOf(playerId)
        if (!entry.missionClaims.add(missionKey)) return ClaimOutcome.ALREADY_CLAIMED
        return if (writeFile()) ClaimOutcome.CLAIMED else {
            entry.missionClaims.remove(missionKey)
            ClaimOutcome.STORAGE_ERROR
        }
    }

    override fun loadCaptures(playerId: UUID): MutableMap<String, CaptureRecord> =
        players[playerId]?.captures?.toMutableMap() ?: mutableMapOf()

    @Synchronized
    override fun recordCapture(playerId: UUID, speciesId: String, record: CaptureRecord) {
        entryOf(playerId).captures[speciesId] = record
        writeFile()
    }

    @Synchronized
    override fun reset(playerId: UUID) {
        players.remove(playerId)
        writeFile()
    }

    @Synchronized
    override fun flush() {
        progressPending = false
        writeFile()
    }

    /** Se ficou progresso pendente do intervalo, grava. */
    @Synchronized
    fun flushPendingProgress() {
        if (!progressPending) return
        progressPending = false
        lastProgressWrite = System.currentTimeMillis()
        writeFile()
    }

    // ------------------------------------------------------------ arquivo

    private fun readFile() {
        if (Files.notExists(file)) return
        try {
            val root = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }
            if (!root.isJsonObject) return
            root.asJsonObject.entrySet().forEach { (key, value) ->
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
                val entry = Entry()

                // Formato antigo: {"uuid": [10, 20]}. Mantido para nao perder
                // os resgates de quem ja estava rodando a versao anterior.
                if (value.isJsonArray) {
                    value.asJsonArray.forEach { tier -> entry.tiers.add(tier.asInt) }
                    players[uuid] = entry
                    return@forEach
                }
                if (!value.isJsonObject) return@forEach
                val obj = value.asJsonObject

                obj.getAsJsonArray("tiers")?.forEach { entry.tiers.add(it.asInt) }
                obj.getAsJsonObject("missions")?.entrySet()?.forEach { (missionKey, progress) ->
                    entry.missions[missionKey] = progress.asInt
                }
                obj.getAsJsonArray("missionClaims")?.forEach { entry.missionClaims.add(it.asString) }
                obj.getAsJsonObject("captures")?.entrySet()?.forEach { (species, raw) ->
                    if (!raw.isJsonObject) return@forEach
                    val record = raw.asJsonObject
                    entry.captures[species] = CaptureRecord(
                        time = record.get("time")?.asLong ?: 0L,
                        biome = record.get("biome")?.asString ?: "",
                        dimension = record.get("dimension")?.asString ?: "",
                        server = record.get("server")?.asString ?: "",
                        count = record.get("count")?.asInt ?: 1
                    )
                }
                players[uuid] = entry
            }
        } catch (e: Exception) {
            LOGGER.error("Nao consegui ler claims.json, comecando vazio. Erro: {}", e.message)
        }
    }

    private fun writeFile(): Boolean = try {
        file.parent?.let { Files.createDirectories(it) }
        val root = JsonObject()
        players.forEach { (uuid, entry) ->
            val obj = JsonObject()
            obj.add("tiers", GSON.toJsonTree(entry.tiers.sorted()))
            obj.add("missions", GSON.toJsonTree(entry.missions))
            obj.add("missionClaims", GSON.toJsonTree(entry.missionClaims.sorted()))
            obj.add("captures", GSON.toJsonTree(entry.captures))
            root.add(uuid.toString(), obj)
        }
        val tmp = file.resolveSibling("claims.json.tmp")
        Files.writeString(tmp, GSON.toJson(root))
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
        val LOGGER = LoggerFactory.getLogger("PokedexRewards/Storage")
        val GSON = GsonBuilder().setPrettyPrinting().create()
        const val PROGRESS_WRITE_INTERVAL_MS = 5_000L
    }
}

// ---------------------------------------------------------------- mongodb

/**
 * MongoDB compartilhado entre todos os servidores da rede.
 *
 * Usa o mesmo banco que o Cobblemon ja usa quando `storageFormat` esta em
 * "mongodb", entao nao precisa de infraestrutura nova.
 */
class MongoRewardsStorage(
    connectionString: String,
    databaseName: String,
    collectionName: String
) : RewardsStorage {

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

    private fun documentOf(playerId: UUID): Document? =
        collection.find(Filters.eq(ID, playerId.toString())).first()

    override fun loadTierClaims(playerId: UUID): MutableSet<Int> = try {
        val raw = documentOf(playerId)?.get(TIERS, List::class.java)
        raw?.filterIsInstance<Number>()?.map { it.toInt() }?.toMutableSet() ?: mutableSetOf()
    } catch (e: Exception) {
        LOGGER.error("Falha ao ler resgates de {}: {}", playerId, e.message)
        mutableSetOf()
    }

    /**
     * Uma operacao atomica so. `$addToSet` nao duplica, e `modifiedCount` conta
     * 1 apenas quando o valor realmente entrou — que e exatamente a pergunta
     * "fui eu quem registrou?". Se duas instancias chamarem junto, so uma leva.
     */
    override fun tryClaimTier(playerId: UUID, tierPercent: Int): ClaimOutcome =
        atomicAddToSet(playerId, TIERS, tierPercent, "resgate de $tierPercent%")

    override fun loadMissionProgress(playerId: UUID): MutableMap<String, Int> = try {
        val raw = documentOf(playerId)?.get(MISSIONS, Document::class.java)
        val out = mutableMapOf<String, Int>()
        raw?.forEach { (key, value) -> if (value is Number) out[key] = value.toInt() }
        out
    } catch (e: Exception) {
        LOGGER.error("Falha ao ler progresso de missoes de {}: {}", playerId, e.message)
        mutableMapOf()
    }

    override fun loadMissionClaims(playerId: UUID): MutableSet<String> = try {
        val raw = documentOf(playerId)?.get(MISSION_CLAIMS, List::class.java)
        raw?.filterIsInstance<String>()?.toMutableSet() ?: mutableSetOf()
    } catch (e: Exception) {
        LOGGER.error("Falha ao ler missoes resgatadas de {}: {}", playerId, e.message)
        mutableSetOf()
    }

    /**
     * `$inc` tambem e atomico, entao duas instancias somando ao mesmo tempo nao
     * perdem captura — o que aconteceria num ler-somar-gravar. Uma ida ao banco
     * so, sem ler de volta.
     */
    override fun incrementMission(playerId: UUID, missionKey: String, amount: Int) {
        try {
            collection.updateOne(
                Filters.eq(ID, playerId.toString()),
                Updates.inc("$MISSIONS.$missionKey", amount),
                UpdateOptions().upsert(true)
            )
        } catch (e: Exception) {
            LOGGER.error("Falha ao somar progresso de '{}' para {}: {}", missionKey, playerId, e.message)
        }
    }

    override fun tryClaimMission(playerId: UUID, missionKey: String): ClaimOutcome =
        atomicAddToSet(playerId, MISSION_CLAIMS, missionKey, "missao $missionKey")

    override fun loadCaptures(playerId: UUID): MutableMap<String, CaptureRecord> = try {
        val raw = documentOf(playerId)?.get(CAPTURES, Document::class.java)
        val out = mutableMapOf<String, CaptureRecord>()
        raw?.forEach { (species, value) ->
            if (value is Document) {
                out[species] = CaptureRecord(
                    time = (value.get("time") as? Number)?.toLong() ?: 0L,
                    biome = value.getString("biome") ?: "",
                    dimension = value.getString("dimension") ?: "",
                    server = value.getString("server") ?: "",
                    count = (value.get("count") as? Number)?.toInt() ?: 1
                )
            }
        }
        out
    } catch (e: Exception) {
        LOGGER.error("Falha ao ler diario de capturas de {}: {}", playerId, e.message)
        mutableMapOf()
    }

    override fun recordCapture(playerId: UUID, speciesId: String, record: CaptureRecord) {
        // O id da especie tem ':' (cobblemon:pikachu) e o Mongo nao aceita '.'
        // em nome de campo, mas ':' pode. Ainda assim escapo qualquer '.' por
        // seguranca com datapacks de terceiros.
        val field = speciesId.replace('.', '_')
        try {
            val doc = Document()
                .append("time", record.time)
                .append("biome", record.biome)
                .append("dimension", record.dimension)
                .append("server", record.server)
                .append("count", record.count)
            collection.updateOne(
                Filters.eq(ID, playerId.toString()),
                Updates.set("$CAPTURES.$field", doc),
                UpdateOptions().upsert(true)
            )
        } catch (e: Exception) {
            LOGGER.error("Falha ao gravar captura de {} para {}: {}", speciesId, playerId, e.message)
        }
    }

    override fun reset(playerId: UUID) {
        try {
            collection.deleteOne(Filters.eq(ID, playerId.toString()))
        } catch (e: Exception) {
            LOGGER.error("Falha ao resetar {}: {}", playerId, e.message)
        }
    }

    override fun close() {
        runCatching { client.close() }
    }

    private fun atomicAddToSet(playerId: UUID, field: String, value: Any, what: String): ClaimOutcome = try {
        val result = collection.updateOne(
            Filters.eq(ID, playerId.toString()),
            Updates.addToSet(field, value),
            UpdateOptions().upsert(true)
        )
        when {
            !result.wasAcknowledged() -> ClaimOutcome.STORAGE_ERROR
            result.upsertedId != null -> ClaimOutcome.CLAIMED
            result.modifiedCount > 0L -> ClaimOutcome.CLAIMED
            else -> ClaimOutcome.ALREADY_CLAIMED
        }
    } catch (e: Exception) {
        LOGGER.error("Falha ao registrar {} para {}: {}", what, playerId, e.message)
        ClaimOutcome.STORAGE_ERROR
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("PokedexRewards/Storage")
        const val ID = "_id"
        const val TIERS = "claimed"
        const val MISSIONS = "missions"
        const val MISSION_CLAIMS = "claimedMissions"
        const val CAPTURES = "captures"
    }
}

// ---------------------------------------------------------------- desabilitado

/**
 * Usado quando o Mongo foi pedido mas nao deu para conectar.
 *
 * Recusa todo resgate de proposito. Cair para o arquivo local aqui seria pior:
 * silenciosamente devolveria o bug de resgatar o mesmo premio em cada servidor,
 * sem ninguem perceber.
 */
class DisabledRewardsStorage(reason: String) : RewardsStorage {

    override val description = "DESABILITADO ($reason)"
    override val isShared = false

    override fun loadTierClaims(playerId: UUID) = mutableSetOf<Int>()
    override fun tryClaimTier(playerId: UUID, tierPercent: Int) = ClaimOutcome.STORAGE_ERROR
    override fun loadMissionProgress(playerId: UUID) = mutableMapOf<String, Int>()
    override fun loadMissionClaims(playerId: UUID) = mutableSetOf<String>()
    override fun incrementMission(playerId: UUID, missionKey: String, amount: Int) {}
    override fun tryClaimMission(playerId: UUID, missionKey: String) = ClaimOutcome.STORAGE_ERROR
    override fun loadCaptures(playerId: UUID) = mutableMapOf<String, CaptureRecord>()
    override fun recordCapture(playerId: UUID, speciesId: String, record: CaptureRecord) {}
    override fun reset(playerId: UUID) {}
}
