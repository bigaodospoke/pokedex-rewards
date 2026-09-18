package com.pokedexrewards.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource

/**
 * Toca som pelo id do registro em vez de pelas constantes de [net.minecraft.sounds.SoundEvents],
 * assim o som vira uma opcao de config e nao quebra se a constante mudar de tipo entre versoes.
 */
object Sounds {

    fun play(player: ServerPlayer, id: String, volume: Float, pitch: Float) {
        val location = ResourceLocation.tryParse(id) ?: return
        val sound = BuiltInRegistries.SOUND_EVENT.getOptional(location).orElse(null) ?: return
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch)
    }
}
