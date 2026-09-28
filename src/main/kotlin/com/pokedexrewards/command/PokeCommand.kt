package com.pokedexrewards.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.pokedexrewards.PokedexRewards
import com.pokedexrewards.gui.CaptureLogGui
import com.pokedexrewards.gui.MissionsGui
import com.pokedexrewards.gui.RewardsGui
import com.pokedexrewards.util.Chat
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer

object PokeCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val config = PokedexRewards.config

        val root = dispatcher.register(
            Commands.literal(config.command)
                .executes(::openMenu)
                .then(Commands.literal("missoes").executes(::openMissions))
                .then(Commands.literal("diario").executes(::openCaptureLog))
                .then(
                    Commands.literal("reload")
                        .requires { it.hasPermission(2) }
                        .executes(::reloadConfig)
                )
                .then(
                    Commands.literal("reset")
                        .requires { it.hasPermission(2) }
                        .then(
                            Commands.argument("alvo", EntityArgument.players())
                                .executes(::resetPlayers)
                        )
                )
        )

        config.aliases
            .filter { it.isNotBlank() && it != config.command }
            .forEach { alias ->
                dispatcher.register(
                    Commands.literal(alias)
                        .redirect(root)
                        .executes(root.command)
                )
            }
    }

    private fun openMenu(ctx: CommandContext<CommandSourceStack>): Int {
        val player = playerOrWarn(ctx) ?: return 0
        RewardsGui(player).open()
        return 1
    }

    private fun openMissions(ctx: CommandContext<CommandSourceStack>): Int {
        val player = playerOrWarn(ctx) ?: return 0
        MissionsGui(player).open()
        return 1
    }

    private fun openCaptureLog(ctx: CommandContext<CommandSourceStack>): Int {
        val player = playerOrWarn(ctx) ?: return 0
        CaptureLogGui(player).open()
        return 1
    }

    private fun reloadConfig(ctx: CommandContext<CommandSourceStack>): Int {
        val tiers = PokedexRewards.reload()
        ctx.source.sendSuccess(
            { Chat.of(PokedexRewards.config.messages.prefix + PokedexRewards.config.messages.reloaded, "count" to tiers.toString()) },
            true
        )
        return 1
    }

    private fun resetPlayers(ctx: CommandContext<CommandSourceStack>): Int {
        val targets = EntityArgument.getPlayers(ctx, "alvo")
        targets.forEach { target ->
            PokedexRewards.claims.reset(target.uuid)
            ctx.source.sendSuccess(
                {
                    Chat.of(
                        PokedexRewards.config.messages.prefix + PokedexRewards.config.messages.resetDone,
                        "player" to target.gameProfile.name
                    )
                },
                true
            )
        }
        return targets.size
    }

    private fun playerOrWarn(ctx: CommandContext<CommandSourceStack>): ServerPlayer? {
        val player = ctx.source.player
        if (player == null) {
            ctx.source.sendFailure(
                Chat.of(PokedexRewards.config.messages.prefix + PokedexRewards.config.messages.playersOnly)
            )
        }
        return player
    }
}
