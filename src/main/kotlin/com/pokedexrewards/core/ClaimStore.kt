package com.pokedexrewards.core

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Guarda quais tiers cada jogador ja resgatou, em
 * `<mundo>/pokedexrewards/claims.json`.
 *
 * Fica junto do mundo (e nao em config/) de proposito: resetar o mundo
 * reseta os resgates, e backup do mundo leva os resgates junto.
 */
class ClaimStore(server: MinecraftServer) {

    private val file: Path = server.getWorldPath(LevelResource.ROOT)
        .resolve("pokedexrewards")
        .resolve("claims.json")

    private val claims = HashMap<UUID, MutableSet<Int>>()

    fun load() {
        claims.clear()
        if (Files.notExists(file)) return
        try {
            val type = object : TypeToken<Map<String, List<Int>>>() {}.type
            val raw: Map<String, List<Int>> = Files.newBufferedReader(file).use { GSON.fromJson(it, type) }
                ?: emptyMap()
            raw.forEach { (key, tiers) ->
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
                claims[uuid] = tiers.toMutableSet()
            }
            LOGGER.info("Resgates carregados para {} jogador(es).", claims.size)
        } catch (e: Exception) {
            LOGGER.error("Nao consegui ler claims.json, comecando vazio. Erro: {}", e.message)
        }
    }

    fun save() {
        try {
            file.parent?.let { Files.createDirectories(it) }
            val raw = claims.entries.associate { (uuid, tiers) -> uuid.toString() to tiers.sorted() }
            val tmp = file.resolveSibling("claims.json.tmp")
            Files.writeString(tmp, GSON.toJson(raw))
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            LOGGER.error("Nao consegui salvar claims.json: {}", e.message)
        }
    }

    fun claimedTiers(uuid: UUID): Set<Int> = claims[uuid] ?: emptySet()

    fun hasClaimed(uuid: UUID, tierPercent: Int): Boolean = claimedTiers(uuid).contains(tierPercent)

    fun markClaimed(uuid: UUID, tierPercent: Int) {
        claims.getOrPut(uuid) { mutableSetOf() }.add(tierPercent)
        save()
    }

    fun reset(uuid: UUID) {
        claims.remove(uuid)
        save()
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("PokedexRewards/Claims")
        val GSON = GsonBuilder().setPrettyPrinting().create()
    }
}
