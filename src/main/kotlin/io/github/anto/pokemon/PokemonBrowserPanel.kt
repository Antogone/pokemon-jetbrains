package io.github.anto.pokemon

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

data class PokemonListEntry(val type: String, val name: String, val color: String)

/**
 * Hosts the unmodified vscode-pokemon webview bundle in a JCEF browser.
 *
 * The VS Code webview API (`acquireVsCodeApi`) is replaced by a small JS shim:
 *  - getState/setState round-trip through [PokemonSettings] so the roster
 *    survives IDE restarts,
 *  - postMessage (webview -> host) arrives through a [JBCefJSQuery],
 *  - host -> webview commands are delivered via `window.postMessage`.
 */
class PokemonBrowserPanel(private val project: Project) : Disposable {

    private val browser = JBCefBrowser()
    private val stateQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val messageQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val pendingListRequests = ConcurrentLinkedQueue<CompletableFuture<String>>()
    private val gson = Gson()

    val component: JComponent get() = browser.component

    private val loadHandler = object : CefLoadHandlerAdapter() {
        override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
            if (frame.isMain) {
                maybeSpawnDefaultPokemon()
            }
        }
    }

    init {
        Disposer.register(this, browser)
        Disposer.register(this, stateQuery)
        Disposer.register(this, messageQuery)

        stateQuery.addHandler { json ->
            PokemonSettings.getInstance().state.savedStateJson = json
            null
        }
        messageQuery.addHandler { json ->
            handleWebviewMessage(json)
            null
        }
        browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)
        panels.add(this)
        reload()
    }

    /** Regenerates index.html (config + saved state) and reloads the browser. */
    fun reload() {
        val mediaDir = PokemonMedia.mediaDir()
        val indexFile = mediaDir.resolve("index.html")
        Files.writeString(indexFile, buildHtml())
        // Cache-busting query: Chromium otherwise serves the previous document
        // from its memory cache when navigating to an identical file:// URL,
        // so theme/size changes would never show up.
        browser.loadURL(indexFile.toUri().toString() + "?v=" + System.currentTimeMillis())
    }

    fun spawnPokemon(entry: PokemonEntry, color: String) {
        postMessage(
            mapOf(
                "command" to "spawn-pokemon",
                "type" to entry.type,
                "color" to color,
                "name" to null, // webview picks a random nickname
                "generation" to entry.generation,
                "originalSpriteSize" to entry.originalSpriteSize,
            ),
        )
    }

    /** Spawns the configured default roster when the panel starts empty (VS Code's `defaultPokemon`). */
    private fun maybeSpawnDefaultPokemon() {
        val settings = PokemonSettings.getInstance().state
        val defaults = PokemonSettings.decodeDefaultPokemon(settings.defaultPokemon)
        if (defaults.isEmpty() || hasSavedRoster(settings.savedStateJson)) {
            return
        }
        for ((type, color) in defaults) {
            val entry = PokemonCatalog.entries.find { it.type == type } ?: continue
            spawnPokemon(entry, color)
        }
    }

    private fun hasSavedRoster(json: String): Boolean {
        if (json.isBlank()) return false
        return runCatching {
            JsonParser.parseString(json).asJsonObject
                .getAsJsonArray("pokemonStates")
                ?.let { !it.isEmpty }
                ?: false
        }.getOrDefault(false)
    }

    fun postMessage(message: Map<String, Any?>) {
        val json = gson.toJson(message)
        browser.cefBrowser.executeJavaScript(
            "window.postMessage($json, '*');",
            browser.cefBrowser.url ?: "",
            0,
        )
    }

    /** Mirrors VS Code's delete flow: ask the webview for its roster. */
    fun requestPokemonList(): CompletableFuture<List<PokemonListEntry>> {
        val future = CompletableFuture<String>()
        pendingListRequests.add(future)
        postMessage(mapOf("command" to "list-pokemon"))
        return future
            .orTimeout(5, TimeUnit.SECONDS)
            .thenApply { text ->
                text.lineSequence()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val parts = line.split(',')
                        PokemonListEntry(
                            type = parts.getOrElse(0) { "" },
                            name = parts.getOrElse(1) { "" },
                            color = parts.getOrElse(2) { "default" },
                        )
                    }
                    .toList()
            }
    }

    private fun handleWebviewMessage(json: String) {
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return
        val text = obj.get("text")?.asString ?: ""
        when (obj.get("command")?.asString) {
            "info" -> notify(text, NotificationType.INFORMATION)
            "alert" -> notify(text, NotificationType.ERROR)
            "list-pokemon" -> pendingListRequests.poll()?.complete(text)
        }
    }

    /**
     * Repairs roster entries saved with a bare numeric generation ("1" instead
     * of "gen1") — sprites for those would otherwise fail to resolve.
     */
    private fun sanitizeState(json: String): String {
        if (json.isBlank()) return json
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            root.getAsJsonArray("pokemonStates")?.forEach { el ->
                val pokemon = el.asJsonObject
                val gen = pokemon.get("pokemonGeneration")?.takeIf { it.isJsonPrimitive }?.asString
                if (gen != null && !gen.startsWith("gen")) {
                    pokemon.addProperty("pokemonGeneration", "gen$gen")
                }
            }
            root.toString()
        }.getOrDefault(json)
    }

    private fun notify(text: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Pokemon")
            .createNotification(text, type)
            .notify(project)
    }

    private fun buildHtml(): String {
        val settings = PokemonSettings.getInstance().state
        val theme = settings.theme
        val size = settings.pokemonSize
        // ColorThemeKind: light = 1, dark = 2 (matches upstream types.ts)
        val themeKind = if (JBColor.isBright()) 1 else 2
        val initialState = sanitizeState(settings.savedStateJson).ifBlank { "null" }
        val saveStateJs = stateQuery.inject("stateJson")
        val postMessageJs = messageQuery.inject("msgJson")

        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="./reset.css" rel="stylesheet">
    <link href="./pokemon.css" rel="stylesheet">
    <style>
    @font-face {
        font-family: 'silkscreen';
        src: url('./Silkscreen-Regular.ttf') format('truetype');
    }
    </style>
    <title>Pokemon</title>
</head>
<body>
    <canvas id="pokemonCanvas"></canvas>
    <div id="pokemonContainer"></div>
    <div id="foreground"></div>
    <script>
    (function () {
        var currentState = $initialState;
        var api = {
            getState: function () { return currentState === null ? undefined : currentState; },
            setState: function (s) {
                currentState = s;
                var stateJson = JSON.stringify(s);
                $saveStateJs
            },
            postMessage: function (m) {
                var msgJson = JSON.stringify(m);
                $postMessageJs
            }
        };
        window.acquireVsCodeApi = function () { return api; };
    })();
    </script>
    <script src="./main-bundle.js"></script>
    <script>
    pokemonApp.pokemonPanelApp(
        ".",
        "$theme",
        $themeKind,
        "default",
        "$size",
        "bulbasaur",
        "false",
        "gen1",
        "32"
    );
    </script>
</body>
</html>"""
    }

    override fun dispose() {
        panels.remove(this)
        browser.jbCefClient.removeLoadHandler(loadHandler, browser.cefBrowser)
        // children (browser, queries) are disposed via Disposer registration
    }

    companion object {
        private val panels = CopyOnWriteArrayList<PokemonBrowserPanel>()

        /** Reloads every open panel; used after settings changes. */
        fun reloadAll() {
            panels.forEach { it.reload() }
        }
    }
}
