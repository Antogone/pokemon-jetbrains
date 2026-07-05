package io.github.anto.pokemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings. `savedStateJson` mirrors what the webview passes to
 * `setState` (the Pokémon roster), so the collection survives IDE restarts —
 * the same mechanism VS Code's webview state API provides.
 */
@Service(Service.Level.APP)
@State(name = "PokemonJetbrainsSettings", storages = [Storage("pokemon-jetbrains.xml")])
class PokemonSettings : PersistentStateComponent<PokemonSettings.State> {

    class State {
        var pokemonSize: String = "nano"
        var theme: String = "none"
        var savedStateJson: String = ""

        /**
         * VS Code-style `defaultPokemon`: spawned automatically when the panel
         * opens with an empty roster. Encoded as "type/color" pairs joined by
         * commas (e.g. "pikachu/default,mismagius/shiny") to keep the XML simple.
         */
        var defaultPokemon: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        val SIZES = listOf("nano", "small", "medium", "large")
        val THEMES = listOf("none", "forest", "castle", "beach")

        fun getInstance(): PokemonSettings =
            ApplicationManager.getApplication().getService(PokemonSettings::class.java)

        fun decodeDefaultPokemon(encoded: String): List<Pair<String, String>> =
            encoded.split(',')
                .filter { it.isNotBlank() }
                .map { spec ->
                    val parts = spec.split('/', limit = 2)
                    parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "default")
                }

        fun encodeDefaultPokemon(specs: List<Pair<String, String>>): String =
            specs.joinToString(",") { (type, color) -> "$type/$color" }
    }
}
