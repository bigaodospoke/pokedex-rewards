package com.pokedexrewards.core

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.pokedexrewards.PokedexRewards
import com.pokedexrewards.missions.MissionService
import net.minecraft.server.level.ServerPlayer

/**
 * Escuta as capturas do Cobblemon e usa uma so para duas coisas: somar progresso
 * nas missoes e gravar onde/quando o jogador pegou aquela especie.
 */
object CaptureTracker {

    fun register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            runCatching { handle(event.player, event.pokemon) }
                .onFailure { PokedexRewards.LOGGER.error("Falha ao processar captura: {}", it.message) }
        }
        PokedexRewards.LOGGER.info("Escutando as capturas do Cobblemon.")
    }

    private fun handle(player: ServerPlayer, pokemon: Pokemon) {
        if (!PokedexRewards.isStoreReady) return

        val speciesId = pokemon.species.resourceIdentifier.toString()

        // Antes de gravar: se ainda nao esta no diario, essa especie e nova pro
        // jogador. Assim a missao de "especie nova" nao depende da ordem em que
        // o Cobblemon atualiza a Pokedex dele.
        val isNewSpecies = PokedexRewards.claims.captureOf(player.uuid, speciesId) == null

        if (PokedexRewards.config.captureLog.enabled) {
            PokedexRewards.claims.recordCapture(
                playerId = player.uuid,
                speciesId = speciesId,
                biome = biomeIdOf(player),
                dimension = player.level().dimension().location().toString(),
                server = serverName(player)
            )
        }

        MissionService.onCapture(player, pokemon, isNewSpecies)
    }

    private fun biomeIdOf(player: ServerPlayer): String =
        player.level().getBiome(player.blockPosition())
            .unwrapKey()
            .map { it.location().toString() }
            .orElse("desconhecido")

    private fun serverName(player: ServerPlayer): String {
        val configured = PokedexRewards.config.captureLog.serverName
        if (configured.isNotBlank()) return configured
        return player.server?.worldData?.levelName ?: "servidor"
    }
}
