package com.pokedexrewards.gui

import com.pokedexrewards.PokedexRewards
import com.pokedexrewards.missions.Mission
import com.pokedexrewards.missions.MissionClaimResult
import com.pokedexrewards.missions.MissionPeriods
import com.pokedexrewards.missions.MissionScope
import com.pokedexrewards.missions.MissionService
import com.pokedexrewards.missions.MissionState
import com.pokedexrewards.util.Chat
import com.pokedexrewards.util.Sounds
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/**
 * Menu das missoes.
 *
 * Uma linha por escopo: diarias em cima, semanais no meio, mensais embaixo.
 * A esquerda de cada linha fica o cabecalho dizendo quando vira.
 */
class MissionsGui(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {

    private val cfg get() = PokedexRewards.config

    init {
        setTitle(Chat.of("&8Pokedex &7» &fMissoes"))
        render()
    }

    fun render() {
        val filler = GuiElementBuilder(itemOf(cfg.gui.filler))
            .setName(Component.empty())
            .hideDefaultTooltip()
            .build()
        for (slot in 0 until virtualSize) setSlot(slot, filler)

        if (!cfg.missions.enabled) {
            setSlot(22, GuiElementBuilder(Items.BARRIER).setName(Chat.of(cfg.messages.missionsDisabled)).build())
            setSlot(CLOSE_SLOT, closeElement())
            setSlot(BACK_SLOT, backElement())
            return
        }

        SCOPES.forEachIndexed { index, scope ->
            val rowStart = ROW_STARTS[index]
            setSlot(rowStart, headerElement(scope))

            val missions = MissionService.activeMissions(scope)
            missions.take(7).forEachIndexed { missionIndex, mission ->
                setSlot(rowStart + 1 + missionIndex, missionElement(mission))
            }
            setSlot(rowStart + 8, claimScopeElement(scope, missions))
        }

        setSlot(BACK_SLOT, backElement())
        setSlot(CLOSE_SLOT, closeElement())
    }

    // ---------------------------------------------------------------- elementos

    private fun headerElement(scope: MissionScope): GuiElementInterface {
        val missions = MissionService.activeMissions(scope)
        val done = missions.count { MissionService.stateOf(viewer, it) != MissionState.IN_PROGRESS }
        val (item, color) = when (scope) {
            MissionScope.DAILY -> Items.CLOCK to "&a"
            MissionScope.WEEKLY -> Items.COMPASS to "&b"
            MissionScope.MONTHLY -> Items.NETHER_STAR to "&6"
        }

        val builder = GuiElementBuilder(item)
            .setName(Chat.of("$color&lMISSOES ${scope.configName.uppercase()}S"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &7Completas: &f$done&7/&f${missions.size}"))
            .addLoreLine(Chat.of("  &7Periodo: &f${MissionService.periodId(scope)}"))
            .addLoreLine(Chat.of("  &8${MissionPeriods.resetLabel(scope, MissionService.zone())}"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &6Premio de cada missao:"))

        cfg.missions.forScope(scope).rewardsDisplay.forEach {
            builder.addLoreLine(Chat.of("   &8• $it"))
        }
        return builder.build()
    }

    private fun missionElement(mission: Mission): GuiElementInterface {
        val state = MissionService.stateOf(viewer, mission)
        val progress = MissionService.progressOf(viewer, mission)
        val shown = progress.coerceAtMost(mission.target)

        val builder = GuiElementBuilder(itemOf(MissionService.icon(mission)))
            .setName(Chat.of(nameColor(state) + MissionService.describe(mission)))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &f${MissionService.bar(progress, mission.target)} &7$shown&8/&7${mission.target}"))
            .addLoreLine(Component.empty())

        when (state) {
            MissionState.IN_PROGRESS -> builder
                .addLoreLine(Chat.of("  &7Em andamento"))
                .addLoreLine(Chat.of("  &8Faltam ${mission.target - shown}"))

            MissionState.COMPLETE -> builder
                .glow()
                .addLoreLine(Chat.of("  &a✔ Completa!"))
                .addLoreLine(Chat.of("  &e▶ Clique para resgatar"))

            MissionState.CLAIMED -> builder
                .addLoreLine(Chat.of("  &8✔ Premio ja resgatado"))
        }

        builder.setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> onMissionClick(mission) })
        return builder.build()
    }

    private fun claimScopeElement(scope: MissionScope, missions: List<Mission>): GuiElementInterface {
        val ready = missions.count { MissionService.stateOf(viewer, it) == MissionState.COMPLETE }

        if (ready == 0) {
            return GuiElementBuilder(Items.GRAY_DYE)
                .setName(Chat.of("&8Resgatar ${scope.configName}s"))
                .addLoreLine(Chat.of("&7Nenhuma completa ainda."))
                .build()
        }

        return GuiElementBuilder(Items.CHEST)
            .glow()
            .setCount(ready.coerceIn(1, 64))
            .setName(Chat.of("&a&lRESGATAR &f($ready)"))
            .addLoreLine(Chat.of("&7Pega todas as ${scope.configName}s completas."))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> onClaimScope(scope) })
            .build()
    }

    private fun backElement(): GuiElementInterface =
        GuiElementBuilder(Items.ARROW)
            .setName(Chat.of("&f◀ Voltar"))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> RewardsGui(viewer).open() })
            .build()

    private fun closeElement(): GuiElementInterface =
        GuiElementBuilder(Items.BARRIER)
            .setName(Chat.of("&c&lFECHAR"))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> close() })
            .build()

    // ---------------------------------------------------------------- cliques

    private fun onMissionClick(mission: Mission) {
        when (MissionService.claim(viewer, mission)) {
            is MissionClaimResult.Success -> {
                PokedexRewards.tell(viewer, cfg.messages.missionClaimed, "mission" to MissionService.describe(mission))
                render()
            }

            MissionClaimResult.Incomplete -> deny(cfg.messages.missionIncomplete)

            MissionClaimResult.AlreadyClaimed -> {
                deny(cfg.messages.missionAlreadyClaimed)
                render()
            }

            MissionClaimResult.StorageError -> deny(cfg.messages.storageError)
        }
    }

    private fun onClaimScope(scope: MissionScope) {
        val done = MissionService.claimAll(viewer, scope)
        if (done.isEmpty()) {
            deny(cfg.messages.nothingToClaim)
            return
        }
        PokedexRewards.tell(viewer, cfg.messages.claimedAll, "count" to done.size.toString())
        render()
    }

    private fun deny(message: String) {
        Sounds.play(viewer, cfg.gui.errorSound, 0.6f, 1.0f)
        PokedexRewards.tell(viewer, message)
    }

    // ---------------------------------------------------------------- helpers

    private fun nameColor(state: MissionState): String = when (state) {
        MissionState.IN_PROGRESS -> "&f"
        MissionState.COMPLETE -> "&a&l"
        MissionState.CLAIMED -> "&8"
    }

    private fun itemOf(id: String): Item =
        ResourceLocation.tryParse(id)
            ?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
            ?: Items.BARRIER

    private companion object {
        val SCOPES = listOf(MissionScope.DAILY, MissionScope.WEEKLY, MissionScope.MONTHLY)

        /** Primeiro slot da linha de cada escopo: tres linhas seguidas. */
        val ROW_STARTS = intArrayOf(9, 18, 27)

        const val BACK_SLOT = 45
        const val CLOSE_SLOT = 53
    }
}
