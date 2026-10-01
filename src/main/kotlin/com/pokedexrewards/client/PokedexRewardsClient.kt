package com.pokedexrewards.client

import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object PokedexRewardsClient : ClientModInitializer {

    override fun onInitializeClient() {
        // Se o jogador fecha a Pokedex no meio da fala, a locucao continuaria
        // tocando sozinha por ate uns 20s. Checar a tela a cada tick corta
        // isso sem precisar de um segundo mixin numa classe do Minecraft.
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.screen !is PokedexGUI) PokedexVoice.reset()
        }
    }
}
