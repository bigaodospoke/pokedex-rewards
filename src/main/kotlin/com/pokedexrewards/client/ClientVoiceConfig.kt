package com.pokedexrewards.client

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * Config da voz, separada da config do mod.
 *
 * A voz e coisa de cliente: quem decide o volume e se quer ouvir e o jogador,
 * nao o servidor. Por isso vive em `config/pokedexrewards-voz.json` na maquina
 * dele, e nao junto da config do servidor.
 */
data class ClientVoiceConfig(
    val enabled: Boolean = true,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
    /** Espera minima entre duas falas, em ms. Evita sobrepor ao rolar a lista. */
    val minIntervalMs: Long = 250,
    /** Espera para repetir a MESMA especie, em ms. */
    val repeatCooldownMs: Long = 1500
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger("PokedexRewards/Voz")
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        @Volatile
        private var cached: ClientVoiceConfig? = null

        fun get(): ClientVoiceConfig = cached ?: load().also { cached = it }

        private fun load(): ClientVoiceConfig {
            val path = FabricLoader.getInstance().configDir.resolve("pokedexrewards-voz.json")
            val defaults = ClientVoiceConfig()
            return try {
                if (Files.notExists(path)) {
                    path.parent?.let { Files.createDirectories(it) }
                    Files.writeString(path, GSON.toJson(defaults))
                    defaults
                } else {
                    Files.newBufferedReader(path).use { GSON.fromJson(it, ClientVoiceConfig::class.java) } ?: defaults
                }
            } catch (e: Exception) {
                LOGGER.error("Nao consegui ler a config da voz, usando padroes: {}", e.message)
                defaults
            }
        }

        fun reload() {
            cached = null
        }
    }
}
