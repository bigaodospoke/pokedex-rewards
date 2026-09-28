package com.pokedexrewards.gui

import com.pokedexrewards.PokedexRewards
import com.pokedexrewards.core.CaptureRecord
import com.pokedexrewards.util.Chat
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Diario de capturas, no estilo Pokemon GO: para cada especie, onde e quando o
 * jogador pegou pela ultima vez, e quantas vezes ja pegou.
 *
 * Ordenado da captura mais recente para a mais antiga, paginado.
 */
class CaptureLogGui(
    private val viewer: ServerPlayer,
    private var page: Int = 0
) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {

    private val cfg get() = PokedexRewards.config

    private val entries: List<Pair<String, CaptureRecord>> =
        PokedexRewards.claims.captures(viewer.uuid)
            .entries
            .sortedByDescending { it.value.time }
            .map { it.key to it.value }

    init {
        setTitle(Chat.of("&8Pokedex &7» &fDiario de Capturas"))
        render()
    }

    fun render() {
        val filler = GuiElementBuilder(itemOf(cfg.gui.filler))
            .setName(Component.empty())
            .hideDefaultTooltip()
            .build()
        for (slot in 0 until virtualSize) setSlot(slot, filler)

        val totalPages = maxOf(1, (entries.size + PER_PAGE - 1) / PER_PAGE)
        page = page.coerceIn(0, totalPages - 1)

        setSlot(4, summaryElement(totalPages))

        if (entries.isEmpty()) {
            setSlot(22, GuiElementBuilder(Items.BARRIER).setName(Chat.of(cfg.messages.captureLogEmpty)).build())
        } else {
            val from = page * PER_PAGE
            val slice = entries.subList(from, minOf(from + PER_PAGE, entries.size))
            slice.forEachIndexed { index, (speciesId, record) ->
                setSlot(FIRST_SLOT + index, entryElement(speciesId, record))
            }
        }

        if (page > 0) setSlot(PREV_SLOT, pageElement("&f◀ Pagina anterior") { page--; render() })
        if (page < totalPages - 1) setSlot(NEXT_SLOT, pageElement("&fPagina seguinte ▶") { page++; render() })

        setSlot(BACK_SLOT, backElement())
        setSlot(CLOSE_SLOT, closeElement())
    }

    // ---------------------------------------------------------------- elementos

    private fun summaryElement(totalPages: Int): GuiElementInterface {
        val total = entries.sumOf { it.second.count }
        val latest = entries.firstOrNull()

        val builder = GuiElementBuilder(Items.PLAYER_HEAD)
            .setSkullOwner(viewer.gameProfile, viewer.server)
            .setName(Chat.of("&b&lDIARIO DE ${viewer.gameProfile.name.uppercase(Locale.ROOT)}"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &7Especies diferentes: &f${entries.size}"))
            .addLoreLine(Chat.of("  &7Capturas no total: &f$total"))

        if (latest != null) {
            builder.addLoreLine(Component.empty())
                .addLoreLine(Chat.of("  &7Ultima captura:"))
                .addLoreLine(Chat.of("  &f${prettyName(latest.first)} &8— &7${formatTime(latest.second.time)}"))
        }

        builder.addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &8Pagina ${page + 1} de $totalPages"))
        return builder.build()
    }

    private fun entryElement(speciesId: String, record: CaptureRecord): GuiElementInterface {
        val builder = GuiElementBuilder(Items.PAPER)
            .setName(Chat.of("&e${prettyName(speciesId)}"))
            .addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &7Quando: &f${formatTime(record.time)}"))
            .addLoreLine(Chat.of("  &7Onde: &f${prettyName(record.biome)}"))

        if (record.dimension.isNotBlank() && record.dimension != "minecraft:overworld") {
            builder.addLoreLine(Chat.of("  &7Dimensao: &f${prettyName(record.dimension)}"))
        }
        if (cfg.captureLog.showServerName && record.server.isNotBlank()) {
            builder.addLoreLine(Chat.of("  &7Servidor: &f${record.server}"))
        }

        builder.addLoreLine(Component.empty())
            .addLoreLine(Chat.of("  &7Ja capturou &f${record.count}&7x"))

        return builder.build()
    }

    private fun pageElement(label: String, action: () -> Unit): GuiElementInterface =
        GuiElementBuilder(Items.ARROW)
            .setName(Chat.of(label))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> action() })
            .build()

    private fun backElement(): GuiElementInterface =
        GuiElementBuilder(Items.OAK_DOOR)
            .setName(Chat.of("&f◀ Voltar ao menu"))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> RewardsGui(viewer).open() })
            .build()

    private fun closeElement(): GuiElementInterface =
        GuiElementBuilder(Items.BARRIER)
            .setName(Chat.of("&c&lFECHAR"))
            .setCallback(GuiElementInterface.ClickCallback { _, _, _, _ -> close() })
            .build()

    // ---------------------------------------------------------------- helpers

    /** "cobblemon:mr_mime" -> "Mr Mime", "minecraft:plains" -> "Plains". */
    private fun prettyName(id: String): String =
        id.substringAfter(':')
            .split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    private fun formatTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "?"
        val zone = runCatching { ZoneId.of(cfg.missions.timezone) }.getOrElse { ZoneId.systemDefault() }
        val pattern = runCatching { DateTimeFormatter.ofPattern(cfg.captureLog.dateFormat) }
            .getOrElse { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }
        return pattern.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
    }

    private fun itemOf(id: String) =
        ResourceLocation.tryParse(id)
            ?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
            ?: Items.BARRIER

    private companion object {
        const val FIRST_SLOT = 9
        const val PER_PAGE = 36
        const val PREV_SLOT = 45
        const val NEXT_SLOT = 53
        const val BACK_SLOT = 48
        const val CLOSE_SLOT = 50
    }
}
