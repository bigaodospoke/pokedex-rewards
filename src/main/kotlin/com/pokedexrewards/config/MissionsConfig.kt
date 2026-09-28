package com.pokedexrewards.config

import com.pokedexrewards.missions.MissionScope

/**
 * Um modelo que o gerador pode sortear. O alvo sai entre min e max, arredondado
 * para um numero redondo.
 */
data class MissionTemplate(
    /** CATCH_ANY, CATCH_SHINY, CATCH_TYPE, CATCH_NEW_SPECIES, CATCH_LEGENDARY */
    val kind: String,
    val minTarget: Int,
    val maxTarget: Int
)

data class MissionScopeConfig(
    val enabled: Boolean = true,
    /** Quantas missoes sorteia por periodo. */
    val missionCount: Int = 3,
    val templates: List<MissionTemplate> = emptyList(),
    /** Texto do premio, so visual. */
    val rewardsDisplay: List<String> = emptyList(),
    /** Comandos rodados ao resgatar. Placeholders: %player% %uuid% */
    val commands: List<String> = emptyList()
)

data class MissionsConfig(
    val enabled: Boolean = true,
    /** Fuso usado para virar o dia, a semana e o mes. Vazio = fuso da maquina. */
    val timezone: String = "America/Sao_Paulo",
    /** Aviso no chat quando o jogador completa uma missao. */
    val notifyOnComplete: Boolean = true,
    val daily: MissionScopeConfig = defaultDaily(),
    val weekly: MissionScopeConfig = defaultWeekly(),
    val monthly: MissionScopeConfig = defaultMonthly()
) {
    fun forScope(scope: MissionScope): MissionScopeConfig = when (scope) {
        MissionScope.DAILY -> daily
        MissionScope.WEEKLY -> weekly
        MissionScope.MONTHLY -> monthly
    }
}

private fun defaultDaily() = MissionScopeConfig(
    missionCount = 3,
    templates = listOf(
        MissionTemplate("CATCH_ANY", 8, 15),
        MissionTemplate("CATCH_TYPE", 3, 6),
        MissionTemplate("CATCH_NEW_SPECIES", 2, 4),
        MissionTemplate("CATCH_SHINY", 1, 1),
        MissionTemplate("CATCH_LEGENDARY", 1, 1)
    ),
    rewardsDisplay = listOf("&f10x &7Poke Ball", "&f2x &7Rare Candy", "&f3.000 &7pokemoedas"),
    commands = listOf(
        "give %player% cobblemon:poke_ball 10",
        "give %player% cobblemon:rare_candy 2",
        "eco give %player% 3000"
    )
)

private fun defaultWeekly() = MissionScopeConfig(
    missionCount = 3,
    templates = listOf(
        MissionTemplate("CATCH_ANY", 40, 70),
        MissionTemplate("CATCH_TYPE", 15, 25),
        MissionTemplate("CATCH_NEW_SPECIES", 10, 20),
        MissionTemplate("CATCH_SHINY", 2, 3),
        MissionTemplate("CATCH_LEGENDARY", 1, 2)
    ),
    rewardsDisplay = listOf("&f10x &7Ultra Ball", "&f8x &7Rare Candy", "&f1x &7Ability Capsule", "&f25.000 &7pokemoedas"),
    commands = listOf(
        "give %player% cobblemon:ultra_ball 10",
        "give %player% cobblemon:rare_candy 8",
        "give %player% cobblemon:ability_capsule 1",
        "eco give %player% 25000"
    )
)

private fun defaultMonthly() = MissionScopeConfig(
    missionCount = 3,
    templates = listOf(
        MissionTemplate("CATCH_ANY", 180, 300),
        MissionTemplate("CATCH_TYPE", 60, 100),
        MissionTemplate("CATCH_NEW_SPECIES", 40, 70),
        MissionTemplate("CATCH_SHINY", 5, 8),
        MissionTemplate("CATCH_LEGENDARY", 3, 5)
    ),
    rewardsDisplay = listOf(
        "&f1x &7Master Ball",
        "&f25x &7Rare Candy",
        "&f3x &7Ability Patch",
        "&f10x &7Exp Candy XL",
        "&f150.000 &7pokemoedas"
    ),
    commands = listOf(
        "give %player% cobblemon:master_ball 1",
        "give %player% cobblemon:rare_candy 25",
        "give %player% cobblemon:ability_patch 3",
        "give %player% cobblemon:exp_candy_xl 10",
        "eco give %player% 150000"
    )
)
