package com.pokedexrewards.core

import com.pokedexrewards.PokedexRewards
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Cache dos resgates dos jogadores online, em cima de um [ClaimStorage].
 *
 * O menu le do cache para nao bater no banco a cada abertura. O resgate em si
 * nunca passa pelo cache: vai direto no [ClaimStorage.tryClaim], que e a unica
 * coisa que garante que o premio sai uma vez so na rede inteira.
 */
class ClaimStore(val storage: ClaimStorage) {

    private val cache = ConcurrentHashMap<UUID, MutableSet<Int>>()

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PokedexRewards Claims IO").apply { isDaemon = true }
    }

    /** Carrega fora da thread principal, para o login nao esperar o banco. */
    fun preloadAsync(playerId: UUID) {
        io.execute {
            runCatching { cache[playerId] = storage.load(playerId) }
                .onFailure { PokedexRewards.LOGGER.error("Falha ao pre-carregar resgates: {}", it.message) }
        }
    }

    fun unload(playerId: UUID) {
        cache.remove(playerId)
    }

    fun claimedTiers(playerId: UUID): Set<Int> =
        cache.getOrPut(playerId) { storage.load(playerId) }

    fun hasClaimed(playerId: UUID, tierPercent: Int): Boolean =
        claimedTiers(playerId).contains(tierPercent)

    fun tryClaim(playerId: UUID, tierPercent: Int): ClaimOutcome {
        val outcome = storage.tryClaim(playerId, tierPercent)
        when (outcome) {
            ClaimOutcome.CLAIMED -> cache.getOrPut(playerId) { mutableSetOf() }.add(tierPercent)
            // Outro servidor registrou primeiro: o cache daqui esta velho.
            ClaimOutcome.ALREADY_CLAIMED -> cache[playerId] = storage.load(playerId)
            ClaimOutcome.STORAGE_ERROR -> Unit
        }
        return outcome
    }

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
