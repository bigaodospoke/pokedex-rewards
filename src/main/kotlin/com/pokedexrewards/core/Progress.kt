package com.pokedexrewards.core

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.CaughtCount
import com.cobblemon.mod.common.api.pokedex.SeenCount
import com.cobblemon.mod.common.api.pokedex.entry.DexEntries
import com.pokedexrewards.config.Metric
import net.minecraft.server.level.ServerPlayer
import kotlin.math.ceil

/**
 * Foto do progresso da Pokedex de um jogador num instante.
 *
 * [counted] e o numero que vale para a porcentagem: capturados ou vistos,
 * dependendo da metrica escolhida na config.
 */
data class DexProgress(
    val caught: Int,
    val seen: Int,
    val total: Int,
    val counted: Int,
    val percent: Double
) {
    /** Quantos Pokemon sao necessarios para bater esse tier. */
    fun requirementFor(tierPercent: Int): Int =
        if (total <= 0) Int.MAX_VALUE else ceil(total * tierPercent / 100.0).toInt()

    fun missingFor(tierPercent: Int): Int =
        (requirementFor(tierPercent) - counted).coerceAtLeast(0)

    fun hasReached(tierPercent: Int): Boolean =
        total > 0 && counted >= requirementFor(tierPercent)
}

object Progress {

    fun of(player: ServerPlayer, metric: Metric): DexProgress {
        val dex = Cobblemon.playerDataManager.getPokedexData(player)

        // Mesma base que o Cobblemon usa no CaughtPercent: especies distintas
        // entre todas as dex entries carregadas (inclui datapacks de terceiros).
        val total = DexEntries.entries.values.map { it.speciesId }.toSet().size

        val caught = dex.getGlobalCalculatedValue(CaughtCount)
        val seen = dex.getGlobalCalculatedValue(SeenCount)
        val counted = if (metric == Metric.SEEN) seen else caught

        // Registros de especie que nao estao em nenhuma dex entry podem empurrar
        // o valor acima de 100; travar aqui evita "110% da Pokedex" no menu.
        val percent = if (total <= 0) 0.0 else (counted.toDouble() / total * 100.0).coerceIn(0.0, 100.0)

        return DexProgress(caught = caught, seen = seen, total = total, counted = counted, percent = percent)
    }
}
