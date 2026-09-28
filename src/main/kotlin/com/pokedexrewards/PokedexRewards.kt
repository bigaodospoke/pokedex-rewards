package com.pokedexrewards

import com.cobblemon.mod.common.Cobblemon
import com.pokedexrewards.command.PokeCommand
import com.pokedexrewards.config.ConfigLoader
import com.pokedexrewards.config.RewardsConfig
import com.pokedexrewards.config.StorageMode
import com.pokedexrewards.core.CaptureTracker
import com.pokedexrewards.core.ClaimStore
import com.pokedexrewards.core.DisabledRewardsStorage
import com.pokedexrewards.core.JsonRewardsStorage
import com.pokedexrewards.core.MongoRewardsStorage
import com.pokedexrewards.core.RewardsStorage
import com.pokedexrewards.gui.CaptureLogGui
import com.pokedexrewards.missions.MissionGenerator
import com.pokedexrewards.util.Chat
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.ItemStack
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object PokedexRewards : ModInitializer {

    const val MOD_ID = "pokedexrewards"

    val LOGGER: Logger = LoggerFactory.getLogger("PokedexRewards")

    var config: RewardsConfig = RewardsConfig()
        private set

    lateinit var claims: ClaimStore
        private set

    /** Se o armazenamento ja subiu. Falso antes do servidor iniciar. */
    val isStoreReady: Boolean get() = ::claims.isInitialized

    override fun onInitialize() {
        config = ConfigLoader.load()

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            claims = ClaimStore(buildStorage(server))
            LOGGER.info("Dados dos jogadores em: {}", claims.storage.description)
            if (!claims.storage.isShared) {
                LOGGER.warn(
                    "Os dados sao locais deste mundo. Se este servidor faz parte de uma rede, " +
                        "o jogador consegue resgatar o mesmo premio em cada servidor. " +
                        "Veja a secao 'storage' em config/pokedexrewards.json."
                )
            }
            CaptureTracker.register()
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            if (isStoreReady) claims.preloadAsync(handler.player.uuid)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (isStoreReady) claims.unload(handler.player.uuid)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            if (isStoreReady) claims.shutdown()
        }

        registerPokedexItemHook()

        // As aliases sao lidas aqui, entao mudar `command`/`aliases` na config
        // so vale depois de reiniciar o servidor. O resto o /poke reload pega.
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            PokeCommand.register(dispatcher)
        }

        LOGGER.info("Pokedex Rewards ativo com {} niveis de recompensa.", config.tiers.size)
    }

    /**
     * Shift + clique direito segurando qualquer Pokedex do Cobblemon abre o
     * diario de capturas. Fica "dentro do item da Pokedex" sem precisar de mod
     * no cliente: o menu e um inventario comum enviado pelo servidor.
     */
    private fun registerPokedexItemHook() {
        UseItemCallback.EVENT.register { player, level, hand ->
            val stack = player.getItemInHand(hand)
            if (level.isClientSide ||
                player !is ServerPlayer ||
                !isStoreReady ||
                !config.captureLog.enabled ||
                !config.captureLog.openWithPokedexItem ||
                !player.isShiftKeyDown ||
                !isPokedexItem(stack)
            ) {
                return@register InteractionResultHolder.pass(stack)
            }

            CaptureLogGui(player).open()
            // success cancela o uso normal, senao a Pokedex do Cobblemon abriria junto.
            InteractionResultHolder.success(stack)
        }
    }

    private fun isPokedexItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val id = BuiltInRegistries.ITEM.getKey(stack.item)
        return id.namespace == "cobblemon" && id.path.startsWith("pokedex")
    }

    /**
     * No modo AUTO os dados seguem o `storageFormat` do Cobblemon. Numa rede a
     * Pokedex ja precisa estar em MongoDB para ser compartilhada, entao os
     * resgates e as missoes vao pro mesmo banco sem configurar nada a mais.
     */
    private fun buildStorage(server: MinecraftServer): RewardsStorage {
        val settings = config.storage
        val cobblemonFormat = runCatching { Cobblemon.config.storageFormat }.getOrNull() ?: "nbt"

        val useMongo = when (settings.mode) {
            StorageMode.MONGODB -> true
            StorageMode.JSON -> false
            StorageMode.AUTO -> cobblemonFormat.equals("mongodb", ignoreCase = true)
        }

        if (!useMongo) return JsonRewardsStorage(server)

        return try {
            val uri = settings.mongoConnectionString.ifBlank { Cobblemon.config.mongoDBConnectionString }
            val database = settings.mongoDatabase.ifBlank { Cobblemon.config.mongoDBDatabaseName }
            MongoRewardsStorage(uri, database, settings.mongoCollection)
        } catch (e: Exception) {
            // Cair pro arquivo local aqui devolveria o bug de resgatar o mesmo
            // premio em cada servidor, e em silencio. Melhor recusar resgate.
            LOGGER.error(
                "MongoDB foi pedido mas nao deu para iniciar ({}). Os resgates ficam BLOQUEADOS " +
                    "ate isso ser resolvido — os menus continuam abrindo normalmente.",
                e.message
            )
            DisabledRewardsStorage(e.message ?: "erro desconhecido")
        }
    }

    fun reload(): Int {
        config = ConfigLoader.load()
        // As missoes sao sorteadas a partir da config; se ela mudou, sorteia de novo.
        MissionGenerator.invalidate()
        return config.tiers.size
    }

    fun tell(player: ServerPlayer, message: String, vararg placeholders: Pair<String, String>) {
        if (message.isBlank()) return
        val all = arrayOf("player" to player.gameProfile.name, *placeholders)
        player.sendSystemMessage(Chat.of(config.messages.prefix + message, *all))
    }
}
