package com.pokedexrewards.core

/**
 * Ultima captura que o jogador fez de uma especie, estilo Pokemon GO:
 * onde foi, quando foi, e quantas vezes ja pegou.
 */
data class CaptureRecord(
    /** Epoch millis da ultima captura. */
    val time: Long,
    /** Id do bioma, ex: "minecraft:plains". */
    val biome: String,
    /** Id da dimensao, ex: "minecraft:overworld". */
    val dimension: String,
    /** Nome do mundo/servidor, para quem roda em rede. */
    val server: String,
    val count: Int
)
