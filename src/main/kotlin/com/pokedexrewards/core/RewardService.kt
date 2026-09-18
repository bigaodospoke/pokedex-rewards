package com.pokedexrewards.core

import com.pokedexrewards.PokedexRewards
import com.pokedexrewards.config.RewardTier
import com.pokedexrewards.util.Chat
import net.minecraft.server.level.ServerPlayer
import com.pokedexrewards.util.Sounds

enum class TierState { LOCKED, AVAILABLE, CLAIMED }

sealed interface ClaimResult {
    data object Locked : ClaimResult
    data object AlreadyClaimed : ClaimResult
    data class Success(val tier: RewardTier) : ClaimResult
}

object RewardService {

    fun stateOf(player: ServerPlayer, tier: RewardTier, progress: DexProgress): TierState = when {
        PokedexRewards.claims.hasClaimed(player.uuid, tier.percent) -> TierState.CLAIMED
        progress.hasReached(tier.percent) -> TierState.AVAILABLE
        else -> TierState.LOCKED
    }

    fun claim(player: ServerPlayer, tier: RewardTier, progress: DexProgress): ClaimResult {
        if (PokedexRewards.claims.hasClaimed(player.uuid, tier.percent)) return ClaimResult.AlreadyClaimed
        if (!progress.hasReached(tier.percent)) return ClaimResult.Locked

        // Marca antes de rodar os comandos: se algum comando falhar, o jogador
        // fala com a staff. Se marcasse depois, um erro no meio da lista daria
        // para resgatar de novo e duplicar o que ja tinha caido.
        PokedexRewards.claims.markClaimed(player.uuid, tier.percent)
        runCommands(player, tier)
        announce(player, tier)

        Sounds.play(player, PokedexRewards.config.gui.claimSound, 0.7f, 1.2f)
        return ClaimResult.Success(tier)
    }

    /** Resgata tudo que estiver liberado, do menor tier para o maior. */
    fun claimAll(player: ServerPlayer, progress: DexProgress): List<RewardTier> {
        val claimed = mutableListOf<RewardTier>()
        PokedexRewards.config.tiers
            .sortedBy { it.percent }
            .forEach { tier ->
                if (claim(player, tier, progress) is ClaimResult.Success) claimed += tier
            }
        return claimed
    }

    private fun runCommands(player: ServerPlayer, tier: RewardTier) {
        val server = player.server ?: return
        val source = server.createCommandSourceStack()
            .withEntity(player)
            .withLevel(player.serverLevel())
            .withPosition(player.position())

        tier.commands.forEach { raw ->
            val command = raw
                .replace("%player%", player.gameProfile.name)
                .replace("%uuid%", player.uuid.toString())
                .replace("%tier%", tier.percent.toString())
                .removePrefix("/")

            if (command.isBlank()) return@forEach
            try {
                server.commands.performPrefixedCommand(source, command)
            } catch (e: Exception) {
                PokedexRewards.LOGGER.error(
                    "Falha ao executar '{}' na recompensa de {}%: {}", command, tier.percent, e.message
                )
            }
        }
    }

    private fun announce(player: ServerPlayer, tier: RewardTier) {
        val broadcast = tier.broadcast?.takeIf { it.isNotBlank() } ?: return
        val server = player.server ?: return
        server.playerList.broadcastSystemMessage(
            Chat.of(
                PokedexRewards.config.messages.prefix + broadcast,
                "player" to player.gameProfile.name,
                "tier" to tier.percent.toString()
            ),
            false
        )
    }
}
