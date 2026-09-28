package com.pokedexrewards.missions

import com.cobblemon.mod.common.pokemon.Pokemon
import com.pokedexrewards.PokedexRewards
import com.pokedexrewards.core.ClaimOutcome
import com.pokedexrewards.util.Sounds
import net.minecraft.server.level.ServerPlayer
import java.time.ZoneId

enum class MissionState { IN_PROGRESS, COMPLETE, CLAIMED }

sealed interface MissionClaimResult {
    data object Incomplete : MissionClaimResult
    data object AlreadyClaimed : MissionClaimResult
    data object StorageError : MissionClaimResult
    data class Success(val mission: Mission) : MissionClaimResult
}

object MissionService {

    /** Nomes em portugues dos tipos, para a missao nao sair em ingles. */
    private val TYPE_NAMES = mapOf(
        "normal" to "Normal", "fire" to "Fogo", "water" to "Agua", "grass" to "Planta",
        "electric" to "Eletrico", "ice" to "Gelo", "fighting" to "Lutador", "poison" to "Venenoso",
        "ground" to "Terrestre", "flying" to "Voador", "psychic" to "Psiquico", "bug" to "Inseto",
        "rock" to "Pedra", "ghost" to "Fantasma", "dragon" to "Dragao", "dark" to "Sombrio",
        "steel" to "Metalico", "fairy" to "Fada"
    )

    fun zone(): ZoneId {
        val raw = PokedexRewards.config.missions.timezone
        if (raw.isBlank()) return ZoneId.systemDefault()
        return runCatching { ZoneId.of(raw) }.getOrElse {
            PokedexRewards.LOGGER.warn("Fuso '{}' invalido na config, usando o da maquina.", raw)
            ZoneId.systemDefault()
        }
    }

    fun activeMissions(scope: MissionScope): List<Mission> {
        if (!PokedexRewards.config.missions.enabled) return emptyList()
        return MissionGenerator.missionsFor(scope, zone())
    }

    fun periodId(scope: MissionScope): String = MissionPeriods.currentId(scope, zone())

    fun keyOf(mission: Mission): String = mission.key(periodId(mission.scope))

    fun progressOf(player: ServerPlayer, mission: Mission): Int =
        PokedexRewards.claims.missionProgress(player.uuid, keyOf(mission))

    fun stateOf(player: ServerPlayer, mission: Mission): MissionState {
        val key = keyOf(mission)
        if (PokedexRewards.claims.hasClaimedMission(player.uuid, key)) return MissionState.CLAIMED
        val progress = PokedexRewards.claims.missionProgress(player.uuid, key)
        return if (progress >= mission.target) MissionState.COMPLETE else MissionState.IN_PROGRESS
    }

    /** Texto da missao, ja com o tipo traduzido quando for CATCH_TYPE. */
    fun describe(mission: Mission): String = when (mission.kind) {
        MissionKind.CATCH_ANY -> "Capture ${mission.target} Pokemon"
        MissionKind.CATCH_SHINY -> "Capture ${mission.target} Pokemon shiny"
        MissionKind.CATCH_LEGENDARY -> "Capture ${mission.target} Pokemon lendario"
        MissionKind.CATCH_NEW_SPECIES -> "Capture ${mission.target} especie(s) que voce nunca pegou"
        MissionKind.CATCH_TYPE -> "Capture ${mission.target} Pokemon do tipo ${typeName(mission.param)}"
    }

    fun typeName(raw: String?): String {
        if (raw == null) return "?"
        return TYPE_NAMES[raw.lowercase()] ?: raw.replaceFirstChar { it.uppercase() }
    }

    fun icon(mission: Mission): String = when (mission.kind) {
        MissionKind.CATCH_ANY -> "cobblemon:poke_ball"
        MissionKind.CATCH_SHINY -> "cobblemon:ultra_ball"
        MissionKind.CATCH_LEGENDARY -> "cobblemon:master_ball"
        MissionKind.CATCH_NEW_SPECIES -> "minecraft:writable_book"
        MissionKind.CATCH_TYPE -> "cobblemon:great_ball"
    }

    // ------------------------------------------------------------ progresso

    /**
     * Chamado a cada captura. Soma em todas as missoes ativas que o Pokemon
     * atende, nos tres escopos.
     */
    fun onCapture(player: ServerPlayer, pokemon: Pokemon, isNewSpeciesForPlayer: Boolean) {
        if (!PokedexRewards.config.missions.enabled) return

        MissionScope.entries.forEach { scope ->
            activeMissions(scope).forEach { mission ->
                if (!mission.matches(pokemon, isNewSpeciesForPlayer)) return@forEach

                val key = keyOf(mission)
                // Ja completou: nao precisa continuar somando.
                if (PokedexRewards.claims.missionProgress(player.uuid, key) >= mission.target) return@forEach

                val total = PokedexRewards.claims.addMissionProgress(player.uuid, key, 1)

                if (total >= mission.target && PokedexRewards.config.missions.notifyOnComplete) {
                    PokedexRewards.tell(
                        player,
                        PokedexRewards.config.messages.missionComplete,
                        "mission" to describe(mission)
                    )
                    Sounds.play(player, PokedexRewards.config.gui.claimSound, 0.5f, 1.6f)
                }
            }
        }
    }

    // ------------------------------------------------------------ resgate

    fun claim(player: ServerPlayer, mission: Mission): MissionClaimResult {
        val key = keyOf(mission)

        if (PokedexRewards.claims.hasClaimedMission(player.uuid, key)) return MissionClaimResult.AlreadyClaimed
        if (PokedexRewards.claims.missionProgress(player.uuid, key) < mission.target) {
            return MissionClaimResult.Incomplete
        }

        return when (PokedexRewards.claims.tryClaimMission(player.uuid, key)) {
            ClaimOutcome.ALREADY_CLAIMED -> MissionClaimResult.AlreadyClaimed
            ClaimOutcome.STORAGE_ERROR -> MissionClaimResult.StorageError
            ClaimOutcome.CLAIMED -> {
                runRewardCommands(player, mission)
                Sounds.play(player, PokedexRewards.config.gui.claimSound, 0.7f, 1.2f)
                MissionClaimResult.Success(mission)
            }
        }
    }

    fun claimAll(player: ServerPlayer, scope: MissionScope): List<Mission> {
        val done = mutableListOf<Mission>()
        activeMissions(scope).forEach { mission ->
            if (claim(player, mission) is MissionClaimResult.Success) done += mission
        }
        return done
    }

    private fun runRewardCommands(player: ServerPlayer, mission: Mission) {
        val config = PokedexRewards.config.missions.forScope(mission.scope)
        val server = player.server ?: return
        val source = server.createCommandSourceStack()
            .withEntity(player)
            .withLevel(player.serverLevel())
            .withPosition(player.position())

        config.commands.forEach { raw ->
            val command = raw
                .replace("%player%", player.gameProfile.name)
                .replace("%uuid%", player.uuid.toString())
                .removePrefix("/")
            if (command.isBlank()) return@forEach
            try {
                server.commands.performPrefixedCommand(source, command)
            } catch (e: Exception) {
                PokedexRewards.LOGGER.error(
                    "Falha ao executar '{}' na missao {}: {}", command, mission.kind, e.message
                )
            }
        }
    }

    /** Barra de progresso usada no menu das missoes. */
    fun bar(progress: Int, target: Int, length: Int = 10): String {
        if (target <= 0) return ""
        val filled = ((progress.toDouble() / target) * length).toInt().coerceIn(0, length)
        val gui = PokedexRewards.config.gui
        return gui.barFilled.repeat(filled) + gui.barEmpty.repeat(length - filled)
    }
}
