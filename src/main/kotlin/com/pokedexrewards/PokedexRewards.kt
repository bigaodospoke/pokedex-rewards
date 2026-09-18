package com.pokedexrewards

import com.cobblemon.mod.common.Cobblemon
import com.pokedexrewards.command.PokeCommand
import com.pokedexrewards.config.ConfigLoader
import com.pokedexrewards.config.RewardsConfig
import com.pokedexrewards.config.StorageMode
import com.pokedexrewards.core.ClaimStorage
import com.pokedexrewards.core.ClaimStore
import com.pokedexrewards.core.DisabledClaimStorage
import com.pokedexrewards.core.JsonClaimStorage
import com.pokedexrewards.core.MongoClaimStorage
import com.pokedexrewards.util.Chat
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
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
            claims = ClaimStore(buildStorage(server))
            LOGGER.info("Resgates guardados em: {}", claims.storage.description)
            if (!claims.storage.isShared) {
                LOGGER.warn(
                    "Os resgates sao locais deste mundo. Se este servidor faz parte de uma rede, " +
                        "o jogador consegue resgatar o mesmo premio em cada servidor. " +
                        "Veja a secao 'storage' em config/pokedexrewards.json."
                )
            }
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            if (::claims.isInitialized) claims.preloadAsync(handler.player.uuid)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (::claims.isInitialized) claims.unload(handler.player.uuid)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            if (::claims.isInitialized) claims.shutdown()
        }

        // As aliases sao lidas aqui, entao mudar `command`/`aliases` na config
        // so vale depois de reiniciar o servidor. O resto o /poke reload pega.
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            PokeCommand.register(dispatcher)
        }

        LOGGER.info("Pokedex Rewards ativo com {} niveis de recompensa.", config.tiers.size)
    }

    /**
     * No modo AUTO os resgates seguem o `storageFormat` do Cobblemon. Numa rede
     * a Pokedex ja precisa estar em MongoDB para ser compartilhada, entao os
     * resgates vao pro mesmo banco sem ninguem precisar configurar nada a mais.
     */
    private fun buildStorage(server: MinecraftServer): ClaimStorage {
        val settings = config.storage
        val cobblemonFormat = runCatching { Cobblemon.config.storageFormat }.getOrNull() ?: "nbt"

        val useMongo = when (settings.mode) {
            StorageMode.MONGODB -> true
            StorageMode.JSON -> false
            StorageMode.AUTO -> cobblemonFormat.equals("mongodb", ignoreCase = true)
        }

        if (!useMongo) return JsonClaimStorage(server)

        return try {
            val uri = settings.mongoConnectionString.ifBlank { Cobblemon.config.mongoDBConnectionString }
            val database = settings.mongoDatabase.ifBlank { Cobblemon.config.mongoDBDatabaseName }
            MongoClaimStorage(uri, database, settings.mongoCollection)
        } catch (e: Exception) {
            // Cair pro arquivo local aqui devolveria o bug de resgatar o mesmo
            // premio em cada servidor, e em silencio. Melhor recusar resgate.
            LOGGER.error(
                "MongoDB foi pedido mas nao deu para iniciar ({}). Os resgates ficam BLOQUEADOS " +
                    "ate isso ser resolvido — o menu continua abrindo normalmente.",
                e.message
            )
            DisabledClaimStorage(e.message ?: "erro desconhecido")
        }
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
