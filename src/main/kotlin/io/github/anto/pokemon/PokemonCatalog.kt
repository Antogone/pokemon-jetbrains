package io.github.anto.pokemon

import com.google.gson.Gson

data class PokemonEntry(
    val type: String,
    val name: String,
    val id: Int,
    val generation: String,
    val originalSpriteSize: Int,
    val possibleColors: List<String>,
)

/** Catalog generated from upstream `src/common/pokemon-data.ts`. */
object PokemonCatalog {
    val entries: List<PokemonEntry> by lazy {
        val json = PokemonCatalog::class.java
            .getResourceAsStream("/pokemon/pokemon-catalog.json")!!
            .bufferedReader()
            .readText()
        Gson().fromJson(json, Array<PokemonEntry>::class.java).toList()
    }
}
