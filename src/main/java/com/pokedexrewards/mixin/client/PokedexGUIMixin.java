package com.pokedexrewards.mixin.client;

import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry;
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI;
import com.pokedexrewards.client.PokedexVoice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faz a Pokedex falar quando o jogador seleciona uma entrada.
 *
 * `setSelectedEntry` e chamado pelo proprio Cobblemon sempre que a selecao
 * muda na tela da Pokedex. Nao existe pacote serverbound para esse clique, por
 * isso o gancho precisa ser aqui, no cliente.
 *
 * `remap = false` porque o alvo e uma classe do Cobblemon, nao do Minecraft:
 * nao ha mapeamento para traduzir.
 */
@Mixin(value = PokedexGUI.class, remap = false)
public class PokedexGUIMixin {

    @Inject(method = "setSelectedEntry", at = @At("RETURN"), remap = false)
    private void pokedexrewards$falarEntradaSelecionada(PokedexEntry entry, CallbackInfo ci) {
        PokedexVoice.INSTANCE.onEntrySelected(entry);
    }
}
