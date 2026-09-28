package com.pokedexrewards.missions

import com.cobblemon.mod.common.pokemon.Pokemon
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

enum class MissionScope(val configName: String) {
    DAILY("diaria"),
    WEEKLY("semanal"),
    MONTHLY("mensal")
}

enum class MissionKind {
    CATCH_ANY,
    CATCH_SHINY,
    CATCH_TYPE,
    CATCH_NEW_SPECIES,
    CATCH_LEGENDARY;

    companion object {
        fun parse(raw: String): MissionKind? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

/**
 * Uma missao ja sorteada para um periodo.
 *
 * Nao e guardada em banco: o gerador recria a mesma lista a partir do id do
 * periodo, entao so o progresso do jogador precisa ser salvo. Isso tambem faz
 * com que todos os jogadores do servidor peguem as mesmas missoes no mesmo dia.
 */
data class Mission(
    val scope: MissionScope,
    val kind: MissionKind,
    val target: Int,
    /** Usado pelo CATCH_TYPE para saber qual tipo elemental conta. */
    val param: String? = null
) {
    /** Chave usada no banco. Inclui o periodo, entao virar o dia zera sozinho. */
    fun key(periodId: String): String = buildString {
        append(scope.name.lowercase())
        append(':')
        append(periodId)
        append(':')
        append(kind.name.lowercase())
        if (param != null) {
            append(':')
            append(param.lowercase())
        }
    }

    fun matches(pokemon: Pokemon, isNewSpeciesForPlayer: Boolean): Boolean = when (kind) {
        MissionKind.CATCH_ANY -> true
        MissionKind.CATCH_SHINY -> pokemon.shiny
        MissionKind.CATCH_NEW_SPECIES -> isNewSpeciesForPlayer
        MissionKind.CATCH_LEGENDARY -> runCatching { pokemon.isLegendary() }.getOrDefault(false)
        MissionKind.CATCH_TYPE -> param != null && pokemon.types.any { it.name.equals(param, ignoreCase = true) }
    }
}

/**
 * Calcula o id do periodo atual. Enquanto o id nao muda, as missoes e o
 * progresso continuam os mesmos; quando muda, tudo recomeca.
 */
object MissionPeriods {

    fun currentId(scope: MissionScope, zone: ZoneId): String {
        val today = LocalDate.now(zone)
        return when (scope) {
            MissionScope.DAILY -> today.toString()
            MissionScope.WEEKLY -> {
                val fields = WeekFields.ISO
                val week = today.get(fields.weekOfWeekBasedYear())
                val year = today.get(fields.weekBasedYear())
                "%d-W%02d".format(Locale.US, year, week)
            }

            MissionScope.MONTHLY -> "%d-%02d".format(Locale.US, today.year, today.monthValue)
        }
    }

    /** Texto amigavel do quanto falta para virar, mostrado no menu. */
    fun resetLabel(scope: MissionScope, zone: ZoneId): String {
        val today = LocalDate.now(zone)
        return when (scope) {
            MissionScope.DAILY -> "vira amanha"
            MissionScope.WEEKLY -> {
                val left = 7 - today.dayOfWeek.value
                if (left <= 0) "vira amanha" else "vira em $left dia(s)"
            }

            MissionScope.MONTHLY -> {
                val left = today.lengthOfMonth() - today.dayOfMonth
                if (left <= 0) "vira amanha" else "vira em $left dia(s)"
            }
        }
    }
}
