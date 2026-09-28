package com.pokedexrewards.gui

import com.pokedexrewards.PokedexRewards
import com.pokedexrewards.config.Metric
import com.pokedexrewards.config.RewardTier
import com.pokedexrewards.core.ClaimResult
import com.pokedexrewards.core.DexProgress
import com.pokedexrewards.core.Progress
import com.pokedexrewards.core.RewardService
import com.pokedexrewards.core.TierState
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
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Menu do /poke.
 *
 * Layout (6 linhas):
 *   linha 0  ->  cabecalho: cabeca do jogador + resumo da Pokedex
 *   linha 1  ->  separador
 *   linha 2  ->  tiers de 10% a 90%
 *   linha 3  ->  separador
 *   linha 4  ->  tier final (100%), sozinho e centralizado
 *   linha 5  ->  ajuda, resgatar tudo, fechar
 */
class RewardsGui(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {

    private val cfg get() = PokedexRewards.config

    init {
        setTitle(Chat.of(cfg.gui.title))
        render()
    }

    fun render() {
        val progress = Progress.of(viewer, cfg.metric)
        val tiers = cfg.tiers.sortedBy { it.percent }

        fillBackground()
        setSlot(STATUS_SLOT, statusElement(progress, tiers))

        val finalTier = tiers.lastOrNull()
        val normalTiers = if (tiers.size > 1) tiers.dropLast(1) else emptyList()

        val slots = layoutSlots(normalTiers.size)
        if (normalTiers.size > slots.size) {
            PokedexRewards.LOGGER.warn(
                "Tem {} tiers configurados mas so cabem {} + 1 no menu; os extras nao vao aparecer.",
                tiers.size, slots.size
            )
        }
        normalTiers.take(slots.size).forEachIndexed { index, tier ->
            setSlot(slots[index], tierElement(tier, progress))
        }
        if (finalTier != null) setSlot(FINAL_SLOT, tierElement(finalTier, progress))

        setSlot(INFO_SLOT, infoElement())
        setSlot(CLAIM_ALL_SLOT, claimAllElement(progress, tiers))
        setSlot(CLOSE_SLOT, closeElement())

        if (cfg.missions.enabled) setSlot(MISSIONS_SLOT, missionsElement())
        if (cfg.captureLog.enabled) setSlot(CAPTURE_LOG_SLOT, captureLogElement())
    }

    private fun missionsElement(): GuiElementInterface {
        val ready = MissionScope.entries.sumOf { scope ->
            MissionService.activeMissions(scope).count { MissionService.stateOf(viewer, it) == MissionState.COMPLETE }
        }

        val builder = GuiElementBuilder(Items.CLOCK)
            .setName(Chat.of("&a&lMISSOES"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("&7Diarias, semanais e mensais."))
            .addLoreLine(Chat.of("&7Todas envolvem capturar Pokemon."))
            .addLoreLine(Component.empty())

        if (ready > 0) {
            builder.glow()
                .setCount(ready.coerceIn(1, 64))
                .addLoreLine(Chat.of("&a$ready missao(oes) pronta(s) pra resgatar!"))
        } else {
            builder.addLoreLine(Chat.of("&8Nenhuma completa agora."))
        }

        builder.addLoreLine(Chat.of("&e▶ Clique para abrir"))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> MissionsGui(viewer).open() })
        return builder.build()
    }

    private fun captureLogElement(): GuiElementInterface {
        val captures = PokedexRewards.claims.captures(viewer.uuid)
        return GuiElementBuilder(Items.MAP)
            .setName(Chat.of("&b&lDIARIO DE CAPTURAS"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("&7Onde e quando voce pegou"))
            .addLoreLine(Chat.of("&7cada Pokemon."))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("&7Especies registradas: &f${captures.size}"))
            .addLoreLine(Chat.of("&e▶ Clique para abrir"))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> CaptureLogGui(viewer).open() })
            .build()
    }

    // ----------------------------------------------------------- elementos

    private fun fillBackground() {
        val filler = GuiElementBuilder(itemOf(cfg.gui.filler))
            .setName(Component.empty())
            .hideDefaultTooltip()
            .build()
        val separator = GuiElementBuilder(itemOf(cfg.gui.separator))
            .setName(Component.empty())
            .hideDefaultTooltip()
            .build()

        for (slot in 0 until virtualSize) {
            val row = slot / 9
            setSlot(slot, if (row == 1 || row == 3) separator else filler)
        }
    }

    private fun statusElement(progress: DexProgress, tiers: List<RewardTier>): GuiElementInterface {
        val claimedCount = tiers.count { PokedexRewards.claims.hasClaimed(viewer.uuid, it.percent) }
        val nextTier = tiers.firstOrNull { !progress.hasReached(it.percent) }

        val builder = GuiElementBuilder(Items.PLAYER_HEAD)
            .setSkullOwner(viewer.gameProfile, viewer.server)
            .setName(Chat.of("&b&lPOKEDEX DE " + viewer.gameProfile.name.uppercase(Locale.ROOT)))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  " + progressBar(progress.percent) + " &b" + format(progress.percent) + "%"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &7Registrados: &f${progress.counted} &7de &f${progress.total}"))
            .addLoreLine(Chat.of("  &7Vistos: &f${progress.seen}    &7Capturados: &f${progress.caught}"))
            .addLoreLine(Chat.of("  &7Premios pegos: &f$claimedCount&7/&f${tiers.size}"))

        if (nextTier != null) {
            builder.addLoreLine(Component.empty())
                .addLoreLine(Chat.of("  &7Proximo premio: &e${nextTier.percent}%"))
                .addLoreLine(Chat.of("  &7Faltam &c${progress.missingFor(nextTier.percent)} &7Pokemon"))
        } else {
            builder.addLoreLine(Component.empty())
                .addLoreLine(Chat.of("  &6★ &eVoce liberou todos os niveis!"))
        }

        return builder.build()
    }

    private fun tierElement(tier: RewardTier, progress: DexProgress): GuiElementInterface {
        val state = RewardService.stateOf(viewer, tier, progress)
        val icon = if (state == TierState.LOCKED && cfg.gui.useLockedIcon) cfg.gui.lockedIcon else tier.icon

        val builder = GuiElementBuilder(itemOf(icon))
            .setCount((tier.percent / 10).coerceIn(1, 64))
            .setName(Chat.of(tier.title))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &7Precisa de &f${progress.requirementFor(tier.percent)} &7Pokemon"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &6Recompensas:"))

        tier.rewards.forEach { line -> builder.addLoreLine(Chat.of("   &8• $line")) }
        builder.addLoreLine(Component.empty())

        when (state) {
            TierState.LOCKED -> builder
                .addLoreLine(Chat.of("  &c✖ Bloqueado"))
                .addLoreLine(Chat.of("  &7Faltam &c${progress.missingFor(tier.percent)} &7Pokemon"))

            TierState.AVAILABLE -> builder
                .glow()
                .addLoreLine(Chat.of("  &a✔ Liberado!"))
                .addLoreLine(Chat.of("  &e▶ Clique para resgatar"))

            TierState.CLAIMED -> builder
                .addLoreLine(Chat.of("  &8✔ Ja resgatado"))
        }

        builder.setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> onTierClick(tier) })
        return builder.build()
    }

    private fun claimAllElement(progress: DexProgress, tiers: List<RewardTier>): GuiElementInterface {
        val available = tiers.count { RewardService.stateOf(viewer, it, progress) == TierState.AVAILABLE }

        if (available == 0) {
            return GuiElementBuilder(Items.GRAY_DYE)
                .setName(Chat.of("&8Resgatar tudo"))
                .addLoreLine(Chat.of("&7Nenhum premio liberado agora."))
                .build()
        }

        return GuiElementBuilder(Items.CHEST)
            .glow()
            .setCount(available.coerceIn(1, 64))
            .setName(Chat.of("&a&lRESGATAR TUDO"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("&7Voce tem &a$available &7premio(s) liberado(s)."))
            .addLoreLine(Chat.of("&e▶ Clique para pegar todos"))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> onClaimAllClick() })
            .build()
    }

    private fun infoElement(): GuiElementInterface {
        val metricName = if (cfg.metric == Metric.SEEN) "vistos" else "capturados"
        return GuiElementBuilder(Items.BOOK)
            .setName(Chat.of("&e&lCOMO FUNCIONA"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("&7A cada faixa da Pokedex voce libera"))
            .addLoreLine(Chat.of("&7um premio para resgatar aqui."))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("&7Contam os Pokemon &f$metricName&7."))
            .addLoreLine(Chat.of("&7Cada premio so pode ser pego &fuma vez&7."))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("&8Bora capturar, treinador."))
            .build()
    }

    private fun closeElement(): GuiElementInterface =
        GuiElementBuilder(Items.BARRIER)
            .setName(Chat.of("&c&lFECHAR"))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> close() })
            .build()

    // ----------------------------------------------------------- cliques

    private fun onTierClick(tier: RewardTier) {
        val progress = Progress.of(viewer, cfg.metric)
        when (RewardService.claim(viewer, tier, progress)) {
            is ClaimResult.Success -> {
                PokedexRewards.tell(viewer, cfg.messages.claimed, "tier" to tier.percent.toString())
                render()
            }

            ClaimResult.AlreadyClaimed -> {
                deny(cfg.messages.alreadyClaimed, tier)
                render()
            }

            ClaimResult.StorageError -> deny(cfg.messages.storageError, tier)

            ClaimResult.Locked -> deny(cfg.messages.locked, tier)
        }
    }

    private fun onClaimAllClick() {
        val progress = Progress.of(viewer, cfg.metric)
        val claimed = RewardService.claimAll(viewer, progress)
        if (claimed.isEmpty()) {
            deny(cfg.messages.nothingToClaim, null)
            return
        }
        PokedexRewards.tell(viewer, cfg.messages.claimedAll, "count" to claimed.size.toString())
        render()
    }

    private fun deny(message: String, tier: RewardTier?) {
        Sounds.play(viewer, cfg.gui.errorSound, 0.6f, 1.0f)
        PokedexRewards.tell(viewer, message, "tier" to (tier?.percent?.toString() ?: "0"))
    }

    // ----------------------------------------------------------- helpers

    private fun progressBar(percent: Double): String {
        val length = cfg.gui.barLength.coerceIn(5, 40)
        val filled = ((percent / 100.0) * length).roundToInt().coerceIn(0, length)
        return cfg.gui.barFilled.repeat(filled) + cfg.gui.barEmpty.repeat(length - filled)
    }

    private fun format(percent: Double): String = String.format(Locale.US, "%.1f", percent)

    private fun itemOf(id: String): Item =
        ResourceLocation.tryParse(id)
            ?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
            ?: Items.BARRIER

    /** Distribui [count] tiers centralizados nas linhas 2 e 3. */
    private fun layoutSlots(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val rows = ceil(count / 9.0).toInt().coerceAtMost(TIER_ROWS.size)
        val slots = mutableListOf<Int>()
        var remaining = count
        for (row in 0 until rows) {
            val inRow = minOf(remaining, 9)
            val start = TIER_ROWS[row] + (9 - inRow) / 2
            for (col in 0 until inRow) slots += start + col
            remaining -= inRow
        }
        return slots
    }

    private companion object {
        const val STATUS_SLOT = 4
        const val FINAL_SLOT = 40
        const val INFO_SLOT = 45
        const val CLAIM_ALL_SLOT = 49
        const val CLOSE_SLOT = 53
        const val MISSIONS_SLOT = 47
        const val CAPTURE_LOG_SLOT = 51

        /** Primeiro slot de cada linha usada pelos tiers normais. */
        val TIER_ROWS = intArrayOf(18, 27)
    }
}
