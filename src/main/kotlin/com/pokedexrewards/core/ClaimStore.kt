package com.pokedexrewards.core

import com.pokedexrewards.PokedexRewards
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Cache dos dados dos jogadores online, em cima de um [RewardsStorage].
 *
 * Os menus leem do cache para nao bater no banco a cada abertura. Resgate nunca
 * passa pelo cache: vai direto no storage, que e a unica coisa que garante que o
 * premio sai uma vez so na rede inteira.
 */
class ClaimStore(val storage: RewardsStorage) {

    private class Cached {
        var tiers: MutableSet<Int> = mutableSetOf()
        var missionProgress: MutableMap<String, Int> = mutableMapOf()
        var missionClaims: MutableSet<String> = mutableSetOf()
        var captures: MutableMap<String, CaptureRecord> = mutableMapOf()
    }

    private val cache = ConcurrentHashMap<UUID, Cached>()

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PokedexRewards Storage IO").apply { isDaemon = true }
    }

    // ------------------------------------------------------------ ciclo de vida

    /** Carrega fora da thread principal, para o login nao esperar o banco. */
    fun preloadAsync(playerId: UUID) {
        io.execute {
            runCatching { cache[playerId] = fetch(playerId) }
                .onFailure { PokedexRewards.LOGGER.error("Falha ao pre-carregar dados: {}", it.message) }
        }
    }

    fun unload(playerId: UUID) {
        cache.remove(playerId)
    }

    private fun fetch(playerId: UUID) = Cached().apply {
        tiers = storage.loadTierClaims(playerId)
        missionProgress = storage.loadMissionProgress(playerId)
        missionClaims = storage.loadMissionClaims(playerId)
        captures = storage.loadCaptures(playerId)
    }

    private fun cached(playerId: UUID): Cached = cache.getOrPut(playerId) { fetch(playerId) }

    // ------------------------------------------------------------ faixas da pokedex

    fun claimedTiers(playerId: UUID): Set<Int> = cached(playerId).tiers

    fun hasClaimed(playerId: UUID, tierPercent: Int): Boolean = claimedTiers(playerId).contains(tierPercent)

    fun tryClaim(playerId: UUID, tierPercent: Int): ClaimOutcome {
        val outcome = storage.tryClaimTier(playerId, tierPercent)
        when (outcome) {
            ClaimOutcome.CLAIMED -> cached(playerId).tiers.add(tierPercent)
            // Outra instancia registrou primeiro: o cache daqui esta velho.
            ClaimOutcome.ALREADY_CLAIMED -> cache[playerId] = fetch(playerId)
            ClaimOutcome.STORAGE_ERROR -> Unit
        }
        return outcome
    }

    // ------------------------------------------------------------ missoes

    fun missionProgress(playerId: UUID, missionKey: String): Int =
        cached(playerId).missionProgress[missionKey] ?: 0

    fun hasClaimedMission(playerId: UUID, missionKey: String): Boolean =
        cached(playerId).missionClaims.contains(missionKey)

    /**
     * Soma no cache na hora e manda o banco em segundo plano.
     *
     * Isso roda a cada captura, e uma ida ao Mongo na thread principal daria
     * tranco no servidor. O premio nao corre risco de sair duas vezes porque
     * quem decide o resgate e o [RewardsStorage.tryClaimMission], que e atomico.
     * O pior caso aqui e perder progresso se o banco falhar, nunca duplicar.
     */
    fun addMissionProgress(playerId: UUID, missionKey: String, amount: Int): Int {
        val entry = cached(playerId)
        val total = (entry.missionProgress[missionKey] ?: 0) + amount
        entry.missionProgress[missionKey] = total
        io.execute {
            runCatching { storage.incrementMission(playerId, missionKey, amount) }
                .onFailure { PokedexRewards.LOGGER.error("Falha ao gravar progresso: {}", it.message) }
        }
        return total
    }

    fun tryClaimMission(playerId: UUID, missionKey: String): ClaimOutcome {
        val outcome = storage.tryClaimMission(playerId, missionKey)
        when (outcome) {
            ClaimOutcome.CLAIMED -> cached(playerId).missionClaims.add(missionKey)
            ClaimOutcome.ALREADY_CLAIMED -> cache[playerId] = fetch(playerId)
            ClaimOutcome.STORAGE_ERROR -> Unit
        }
        return outcome
    }

    // ------------------------------------------------------------ diario de capturas

    fun captures(playerId: UUID): Map<String, CaptureRecord> = cached(playerId).captures

    fun captureOf(playerId: UUID, speciesId: String): CaptureRecord? = cached(playerId).captures[speciesId]

    fun recordCapture(playerId: UUID, speciesId: String, biome: String, dimension: String, server: String) {
        val entry = cached(playerId)
        val previous = entry.captures[speciesId]
        val record = CaptureRecord(
            time = System.currentTimeMillis(),
            biome = biome,
            dimension = dimension,
            server = server,
            count = (previous?.count ?: 0) + 1
        )
        entry.captures[speciesId] = record
        // Gravar no banco pode demorar; a captura em si nao precisa esperar.
        io.execute { storage.recordCapture(playerId, speciesId, record) }
    }

    // ------------------------------------------------------------ admin

    fun reset(playerId: UUID) {
        storage.reset(playerId)
        cache.remove(playerId)
    }

    fun shutdown() {
        io.shutdown()
        storage.flush()
        storage.close()
    }
}
