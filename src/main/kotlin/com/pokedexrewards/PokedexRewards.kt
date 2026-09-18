package com.pokedexrewards

import com.pokedexrewards.command.PokeCommand
import com.pokedexrewards.config.ConfigLoader
import com.pokedexrewards.config.RewardsConfig
import com.pokedexrewards.core.ClaimStore
import com.pokedexrewards.util.Chat
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.level.ServerPlayer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PokedexRewards : ModInitializer {

    const val MOD_ID = "pokedexrewards"

    val LOGGER: Logger = LoggerFactory.getLogger("PokedexRewards")

    var config: RewardsConfig = RewardsConfig()
        private set

    lateinit var claims: ClaimStore
        private set

    override fun onInitialize() {
        config = ConfigLoader.load()

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            claims = ClaimStore(server).also { it.load() }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            if (::claims.isInitialized) claims.save()
        }

        // As aliases sao lidas aqui, entao mudar `command`/`aliases` na config
        // so vale depois de reiniciar o servidor. O resto o /poke reload pega.
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            PokeCommand.register(dispatcher)
        }

        LOGGER.info("Pokedex Rewards ativo com {} niveis de recompensa.", config.tiers.size)
    }

    fun reload(): Int {
        config = ConfigLoader.load()
        return config.tiers.size
    }

    fun tell(player: ServerPlayer, message: String, vararg placeholders: Pair<String, String>) {
        if (message.isBlank()) return
        val all = arrayOf("player" to player.gameProfile.name, *placeholders)
        player.sendSystemMessage(Chat.of(config.messages.prefix + message, *all))
    }
}
