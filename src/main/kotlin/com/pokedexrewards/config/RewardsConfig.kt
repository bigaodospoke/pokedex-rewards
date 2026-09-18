package com.pokedexrewards.config

/** Qual numero da Pokedex conta para a porcentagem. */
enum class Metric { CAUGHT, SEEN }

data class RewardTier(
    /** Porcentagem necessaria para liberar (10, 20, ... 100). */
    val percent: Int,
    /** Nome do item no menu. */
    val title: String,
    /** Item mostrado no menu, ex: "minecraft:diamond". */
    val icon: String,
    /** Linhas de lore que descrevem o premio (so visual). */
    val rewards: List<String>,
    /** Comandos executados pelo console ao resgatar. Placeholders: %player% %uuid% %tier% */
    val commands: List<String>,
    /** Anuncio no chat do servidor ao resgatar. Deixe null para nao anunciar. */
    val broadcast: String? = null
)

data class GuiSettings(
    val title: String = "&8Pokedex &7\u00bb &fRecompensas",
    val filler: String = "minecraft:gray_stained_glass_pane",
    val separator: String = "minecraft:cyan_stained_glass_pane",
    val lockedIcon: String = "minecraft:gray_dye",
    val useLockedIcon: Boolean = false,
    val barLength: Int = 20,
    val barFilled: String = "&a\u25ac",
    val barEmpty: String = "&8\u25ac",
    val claimSound: String = "entity.player.levelup",
    val errorSound: String = "entity.villager.no"
)

data class Messages(
    val prefix: String = "&8[&bPokedex&8] &r",
    val claimed: String = "&aVoce resgatou as recompensas de &e%tier%%&a!",
    val locked: String = "&cVoce ainda nao chegou em &e%tier%% &cda Pokedex.",
    val alreadyClaimed: String = "&cVoce ja resgatou essa recompensa.",
    val nothingToClaim: String = "&eNao tem nenhuma recompensa liberada para resgatar agora.",
    val claimedAll: String = "&aVoce resgatou &e%count% &arecompensas de uma vez!",
    val reloaded: String = "&aConfig recarregada. &7(%count% niveis)",
    val playersOnly: String = "&cSo jogador pode usar esse comando.",
    val resetDone: String = "&aAs recompensas de &e%player% &aforam resetadas."
)

data class RewardsConfig(
    /** Comando principal. O padrao e /poke. */
    val command: String = "poke",
    /** Comandos extras que abrem o mesmo menu. */
    val aliases: List<String> = listOf("pokerewards", "pokedexrewards"),
    /** CAUGHT = so capturados contam. SEEN = vistos tambem contam. */
    val metric: Metric = Metric.CAUGHT,
    val gui: GuiSettings = GuiSettings(),
    val messages: Messages = Messages(),
    val tiers: List<RewardTier> = defaultTiers()
)

/**
 * Os comandos de dinheiro usam `eco give` so como exemplo.
 * Troque pelo comando da economia do seu servidor (Impactor, Vault, etc).
 */
internal fun defaultTiers(): List<RewardTier> = listOf(
    RewardTier(
        percent = 10,
        title = "&a&l10%&r &7da Pokedex",
        icon = "minecraft:iron_ingot",
        rewards = listOf("&f15x &7Ultra Ball", "&f5x &7Rare Candy", "&f15.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:ultra_ball 15",
            "give %player% cobblemon:rare_candy 5",
            "eco give %player% 15000"
        )
    ),
    RewardTier(
        percent = 20,
        title = "&a&l20%&r &7da Pokedex",
        icon = "minecraft:gold_ingot",
        rewards = listOf("&f20x &7Ultra Ball", "&f8x &7Rare Candy", "&f25.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:ultra_ball 20",
            "give %player% cobblemon:rare_candy 8",
            "eco give %player% 25000"
        )
    ),
    RewardTier(
        percent = 30,
        title = "&a&l30%&r &7da Pokedex",
        icon = "minecraft:lapis_lazuli",
        rewards = listOf("&f1x &7Ability Capsule", "&f10x &7Rare Candy", "&f40.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:ability_capsule 1",
            "give %player% cobblemon:rare_candy 10",
            "eco give %player% 40000"
        )
    ),
    RewardTier(
        percent = 40,
        title = "&a&l40%&r &7da Pokedex",
        icon = "minecraft:redstone",
        rewards = listOf("&f5x &7Exp Candy L", "&f2x &7Link Cable", "&f60.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:exp_candy_l 5",
            "give %player% cobblemon:link_cable 2",
            "eco give %player% 60000"
        )
    ),
    RewardTier(
        percent = 50,
        title = "&b&l50%&r &7da Pokedex",
        icon = "minecraft:diamond",
        rewards = listOf("&f1x &7Master Ball", "&f15x &7Rare Candy", "&f100.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:master_ball 1",
            "give %player% cobblemon:rare_candy 15",
            "eco give %player% 100000"
        ),
        broadcast = "&b%player% &fchegou em &b50% &fda Pokedex!"
    ),
    RewardTier(
        percent = 60,
        title = "&b&l60%&r &7da Pokedex",
        icon = "minecraft:emerald",
        rewards = listOf("&f2x &7Ability Patch", "&f10x &7Exp Candy L", "&f150.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:ability_patch 2",
            "give %player% cobblemon:exp_candy_l 10",
            "eco give %player% 150000"
        )
    ),
    RewardTier(
        percent = 70,
        title = "&d&l70%&r &7da Pokedex",
        icon = "minecraft:amethyst_shard",
        rewards = listOf("&f3x &7Lucky Egg", "&f20x &7Rare Candy", "&f220.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:lucky_egg 3",
            "give %player% cobblemon:rare_candy 20",
            "eco give %player% 220000"
        )
    ),
    RewardTier(
        percent = 80,
        title = "&d&l80%&r &7da Pokedex",
        icon = "minecraft:echo_shard",
        rewards = listOf("&f2x &7Master Ball", "&f10x &7Exp Candy XL", "&f320.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:master_ball 2",
            "give %player% cobblemon:exp_candy_xl 10",
            "eco give %player% 320000"
        )
    ),
    RewardTier(
        percent = 90,
        title = "&6&l90%&r &7da Pokedex",
        icon = "minecraft:netherite_ingot",
        rewards = listOf("&f5x &7Ability Patch", "&f30x &7Rare Candy", "&f500.000 &7pokemoedas"),
        commands = listOf(
            "give %player% cobblemon:ability_patch 5",
            "give %player% cobblemon:rare_candy 30",
            "eco give %player% 500000"
        ),
        broadcast = "&6%player% &festa a um passo de fechar a Pokedex! &e(90%)"
    ),
    RewardTier(
        percent = 100,
        title = "&e&l100%&r &7\u2014 &e&lPOKEDEX COMPLETA",
        icon = "minecraft:nether_star",
        rewards = listOf(
            "&e\u2605 &bMewtwo shiny &fnivel 100",
            "&f1.000.000 &7pokemoedas",
            "&8Exclusivo de quem fechou 100%"
        ),
        // Troque por uma skin exclusiva de verdade quando ela existir.
        commands = listOf(
            "givepokemon %player% mewtwo shiny=true level=100",
            "eco give %player% 1000000"
        ),
        broadcast = "&e\u2605 &f%player% &eFECHOU A POKEDEX 100%! &fUm feito e tanto."
    )
)
