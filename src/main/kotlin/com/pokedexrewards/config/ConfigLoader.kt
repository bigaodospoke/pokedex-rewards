package com.pokedexrewards.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Le/escreve `config/pokedexrewards.json`.
 *
 * O arquivo do usuario e mesclado em cima dos valores padrao antes de virar
 * objeto. Isso resolve dois problemas de uma vez: opcao nova em atualizacao
 * aparece sozinha no arquivo, e campo apagado pelo usuario nao vira null
 * dentro de um tipo nao-nulavel do Kotlin.
 */
object ConfigLoader {

    private val LOGGER = LoggerFactory.getLogger("PokedexRewards/Config")
    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private val path: Path
        get() = FabricLoader.getInstance().configDir.resolve("pokedexrewards.json")

    fun load(): RewardsConfig {
        val defaults = RewardsConfig()
        val defaultTree = GSON.toJsonTree(defaults).asJsonObject

        if (Files.notExists(path)) {
            write(defaultTree)
            LOGGER.info("Config criada em {}", path)
            return defaults
        }

        return try {
            val parsed = Files.newBufferedReader(path).use { JsonParser.parseReader(it) }
            require(parsed.isJsonObject) { "a raiz do arquivo precisa ser um objeto JSON" }

            val merged = merge(defaultTree, parsed.asJsonObject, defaultTierTemplate())
            val config = GSON.fromJson(merged, RewardsConfig::class.java)
            write(merged)
            config
        } catch (e: Exception) {
            LOGGER.error("Nao consegui ler pokedexrewards.json, usando os valores padrao. Erro: {}", e.message)
            backupBroken()
            defaults
        }
    }

    private fun defaultTierTemplate(): JsonObject =
        GSON.toJsonTree(defaultTiers().first()).asJsonObject

    /**
     * Objeto mescla campo a campo; lista e valor simples do usuario substituem
     * o padrao inteiro (senao nao daria para remover um tier). A lista `tiers`
     * e a excecao: cada item ainda ganha os campos que faltam.
     */
    private fun merge(base: JsonObject, override: JsonObject, tierTemplate: JsonObject): JsonObject {
        val result = base.deepCopy()
        for ((key, value) in override.entrySet()) {
            val current = result.get(key)
            when {
                key == "tiers" && value.isJsonArray -> result.add(key, mergeTiers(value.asJsonArray, tierTemplate))
                current != null && current.isJsonObject && value.isJsonObject ->
                    result.add(key, merge(current.asJsonObject, value.asJsonObject, tierTemplate))
                else -> result.add(key, value)
            }
        }
        return result
    }

    private fun mergeTiers(userTiers: JsonArray, template: JsonObject): JsonArray {
        val out = JsonArray()
        userTiers.forEach { element: JsonElement ->
            if (element.isJsonObject) {
                val tier = template.deepCopy()
                element.asJsonObject.entrySet().forEach { (k, v) -> tier.add(k, v) }
                out.add(tier)
            } else {
                out.add(element)
            }
        }
        return out
    }

    private fun write(tree: JsonObject) {
        try {
            path.parent?.let { Files.createDirectories(it) }
            Files.writeString(path, GSON.toJson(tree))
        } catch (e: Exception) {
            LOGGER.error("Nao consegui salvar a config: {}", e.message)
        }
    }

    private fun backupBroken() {
        try {
            val broken = path.resolveSibling("pokedexrewards.json.broken")
            Files.deleteIfExists(broken)
            Files.copy(path, broken)
            LOGGER.warn("Copia do arquivo com problema salva em {}", broken)
        } catch (_: Exception) {
            // nao vale derrubar o carregamento por causa do backup
        }
    }
}
