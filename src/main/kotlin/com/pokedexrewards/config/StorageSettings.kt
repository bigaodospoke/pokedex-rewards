package com.pokedexrewards.config

/**
 * AUTO segue o `storageFormat` do Cobblemon: se ele estiver em "mongodb", os
 * resgates tambem vao pro Mongo. Serve para a rede inteira com uma config so.
 */
enum class StorageMode { AUTO, JSON, MONGODB }

data class StorageSettings(
    val mode: StorageMode = StorageMode.AUTO,
    /** Vazio = usa a connection string do proprio Cobblemon. */
    val mongoConnectionString: String = "",
    /** Vazio = usa o database do proprio Cobblemon. */
    val mongoDatabase: String = "",
    val mongoCollection: String = "PokedexRewardsClaims"
)
