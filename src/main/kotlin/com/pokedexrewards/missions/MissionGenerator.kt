package com.pokedexrewards.missions

import com.cobblemon.mod.common.api.types.ElementalTypes
import com.pokedexrewards.PokedexRewards
import com.pokedexrewards.config.MissionScopeConfig
import java.time.ZoneId
import java.util.Random

/**
 * Sorteia as missoes do periodo.
 *
 * A semente vem do id do periodo, entao o sorteio e sempre o mesmo para aquele
 * dia/semana/mes: o servidor pode reiniciar no meio do dia e as missoes
 * continuam iguais, e todos os jogadores veem a mesma lista.
 */
object MissionGenerator {

    private val cache = HashMap<String, List<Mission>>()

    fun missionsFor(scope: MissionScope, zone: ZoneId): List<Mission> {
        val periodId = MissionPeriods.currentId(scope, zone)
        val config = PokedexRewards.config.missions.forScope(scope)
        if (!config.enabled) return emptyList()

        val cacheKey = "${scope.name}:$periodId:${config.hashCode()}"
        cache[cacheKey]?.let { return it }

        val generated = generate(scope, periodId, config)
        // Guarda so o periodo atual de cada escopo; nao cresce sem parar.
        cache.keys.removeIf { it.startsWith("${scope.name}:") }
        cache[cacheKey] = generated
        return generated
    }

    private fun generate(scope: MissionScope, periodId: String, config: MissionScopeConfig): List<Mission> {
        val templates = config.templates.mapNotNull { template ->
            val kind = MissionKind.parse(template.kind)
            if (kind == null) {
                PokedexRewards.LOGGER.warn("Missao com tipo desconhecido na config: '{}'", template.kind)
                null
            } else {
                kind to template
            }
        }
        if (templates.isEmpty()) return emptyList()

        val random = Random(seedOf(scope, periodId))
        val wanted = config.missionCount.coerceAtLeast(1)

        // Embaralha os modelos e vai pegando tipos diferentes, para nao sair
        // "capture 10" e "capture 15" na mesma lista.
        val shuffled = templates.shuffled(random)
        val picked = LinkedHashMap<MissionKind, Mission>()

        for ((kind, template) in shuffled) {
            if (picked.size >= wanted) break
            if (picked.containsKey(kind)) continue
            picked[kind] = buildMission(scope, kind, template.minTarget, template.maxTarget, random)
        }

        // Se pediram mais missoes do que existem tipos, repete tipos para completar.
        var guard = 0
        while (picked.size < wanted && guard < wanted * 4) {
            val (kind, template) = shuffled[random.nextInt(shuffled.size)]
            val mission = buildMission(scope, kind, template.minTarget, template.maxTarget, random)
            picked.putIfAbsent(mission.kind, mission)
            guard++
        }

        return picked.values.toList()
    }

    private fun buildMission(
        scope: MissionScope,
        kind: MissionKind,
        minTarget: Int,
        maxTarget: Int,
        random: Random
    ): Mission {
        val low = minTarget.coerceAtLeast(1)
        val high = maxTarget.coerceAtLeast(low)
        val raw = if (high == low) low else low + random.nextInt(high - low + 1)
        val target = roundNicely(raw)

        val param = if (kind == MissionKind.CATCH_TYPE) pickType(random) else null
        return Mission(scope = scope, kind = kind, target = target, param = param)
    }

    /** Numero redondo fica melhor de ler: 20 em vez de 19. */
    private fun roundNicely(value: Int): Int = when {
        value >= 50 -> (value / 10) * 10
        value >= 10 -> (value / 5) * 5
        else -> value
    }.coerceAtLeast(1)

    private fun pickType(random: Random): String {
        val types = runCatching { ElementalTypes.all() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: return "normal"
        return types[random.nextInt(types.size)].name
    }

    private fun seedOf(scope: MissionScope, periodId: String): Long {
        // Sementes diferentes por escopo, senao diaria e semanal sorteariam igual.
        var hash = 1125899906842597L
        val text = "${scope.name}|$periodId"
        for (char in text) hash = 31 * hash + char.code
        return hash
    }

    fun invalidate() {
        cache.clear()
    }
}
