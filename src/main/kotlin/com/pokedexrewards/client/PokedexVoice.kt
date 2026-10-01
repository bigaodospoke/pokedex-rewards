package com.pokedexrewards.client

import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import org.slf4j.LoggerFactory

/**
 * Voz da Pokedex: toca a locucao da especie quando o jogador seleciona uma
 * entrada na tela da Pokedex do Cobblemon.
 *
 * Roda inteiramente no cliente. O audio vem dos assets deste mod, entao nao
 * precisa de resource pack separado nem de pacote vindo do servidor.
 */
object PokedexVoice {

    private val LOGGER = LoggerFactory.getLogger("PokedexRewards/Voz")

    /** Som tocando agora, para conseguir cortar quando a selecao muda. */
    private var playingId: ResourceLocation? = null

    /** Evita repetir quando a selecao volta pro mesmo Pokemon. */
    private var lastSpeciesPath: String? = null

    private var lastPlayedAt = 0L

    fun onEntrySelected(entry: PokedexEntry) {
        val config = ClientVoiceConfig.get()
        if (!config.enabled) return

        val speciesPath = runCatching { entry.speciesId.path }.getOrNull() ?: return

        val now = System.currentTimeMillis()
        if (speciesPath == lastSpeciesPath && now - lastPlayedAt < config.repeatCooldownMs) return
        if (now - lastPlayedAt < config.minIntervalMs) return

        lastSpeciesPath = speciesPath
        lastPlayedAt = now

        play(speciesPath, config.volume, config.pitch)
    }

    private fun play(speciesPath: String, volume: Float, pitch: Float) {
        val client = Minecraft.getInstance()
        val id = ResourceLocation.tryParse("pokedexrewards:voice.$speciesPath") ?: return

        try {
            // As locucoes incluem a descricao da Pokedex e passam de 20s em
            // varias especies. Sem cortar a anterior, trocar de Pokemon faria
            // duas vozes falarem por cima uma da outra.
            stopCurrent()

            // O SoundEvent e criado na hora em vez de registrado: o som nao
            // precisa existir no registro do servidor, so no sounds.json deste
            // mod, que o cliente carrega como resource pack.
            val sound = SoundEvent.createVariableRangeEvent(id)
            client.soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
            playingId = id
        } catch (e: Exception) {
            LOGGER.debug("Nao consegui tocar a voz de {}: {}", speciesPath, e.message)
        }
    }

    fun stopCurrent() {
        val current = playingId ?: return
        runCatching { Minecraft.getInstance().soundManager.stop(current, SoundSource.MASTER) }
        playingId = null
    }

    /** Usado quando a tela fecha, para a proxima abertura falar de novo. */
    fun reset() {
        stopCurrent()
        lastSpeciesPath = null
    }
}
