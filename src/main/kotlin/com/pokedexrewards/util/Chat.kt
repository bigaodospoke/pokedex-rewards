package com.pokedexrewards.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

/**
 * Converte texto com codigos legacy (&a, &l, &#FF00AA) em Component.
 *
 * O italico fica desligado na raiz de proposito: nomes de item em GUI vem
 * italicos por padrao no cliente e isso deixa o menu feio.
 */
object Chat {

    private val CODES: Map<Char, ChatFormatting> =
        ChatFormatting.values().associateBy { it.char.lowercaseChar() }

    fun of(raw: String): MutableComponent {
        val root = Component.empty().setStyle(Style.EMPTY.withItalic(false))
        val buffer = StringBuilder()
        var style = Style.EMPTY.withItalic(false)
        var i = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                root.append(Component.literal(buffer.toString()).setStyle(style))
                buffer.setLength(0)
            }
        }

        while (i < raw.length) {
            val c = raw[i]
            if ((c == '&' || c == '\u00A7') && i + 1 < raw.length) {
                val next = raw[i + 1]

                // &#RRGGBB
                if (next == '#' && i + 7 < raw.length) {
                    val parsed = TextColor.parseColor("#" + raw.substring(i + 2, i + 8)).result().orElse(null)
                    if (parsed != null) {
                        flush()
                        style = style.withColor(parsed)
                        i += 8
                        continue
                    }
                }

                val fmt = CODES[next.lowercaseChar()]
                if (fmt != null) {
                    flush()
                    style = when {
                        fmt == ChatFormatting.RESET -> Style.EMPTY.withItalic(false)
                        // cor limpa os estilos anteriores, igual ao comportamento legacy
                        fmt.isColor -> Style.EMPTY.withItalic(false).withColor(fmt)
                        else -> style.applyFormat(fmt)
                    }
                    i += 2
                    continue
                }
            }
            buffer.append(c)
            i++
        }

        flush()
        return root
    }

    /** Aplica os placeholders `%chave%` antes de converter. */
    fun of(raw: String, vararg placeholders: Pair<String, String>): MutableComponent {
        var text = raw
        placeholders.forEach { (key, value) -> text = text.replace("%$key%", value) }
        return of(text)
    }
}
