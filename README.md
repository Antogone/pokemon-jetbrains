# Pokemon for JetBrains IDEs

A port of [jakobhoeg/vscode-pokemon](https://github.com/jakobhoeg/vscode-pokemon) to
JetBrains IDEs — **PyCharm**, **DataSpell**, IntelliJ IDEA, WebStorm, and any other
IntelliJ-Platform IDE (2024.2+).

Walking pixel-art Pokémon (generations 1–4, 565 species) live in a **Pokemon tool
window**, with selectable sprite sizes, themed backgrounds (forest, castle, beach),
shiny variants (authentic 1-in-8192 odds on random spawns), Pokémon friendships,
and a roster that survives IDE restarts.

## Install

1. Grab `pokemon-jetbrains-<version>.zip` (from the
   [releases](../../releases) page, or build it yourself — see below).
2. In the IDE: **Settings → Plugins → ⚙️ → Install Plugin from Disk…** and pick the zip.
3. Restart the IDE, then open the **Pokemon** tool window (right sidebar).

## Usage

- Title bar buttons: **+** spawn a chosen Pokémon (type to filter), **⚡** spawn
  random, **–** remove one.
- Gear (⚙️) menu: Pokémon size, background theme, roll call, remove all, settings.
- **Settings → Tools → Pokemon**: size, theme, and a **Default Pokemon** list —
  the equivalent of vscode-pokemon's `defaultPokemon` setting. Whenever the panel
  opens with an empty roster, these Pokémon are spawned automatically.

Your current roster is saved automatically to the IDE config
(`<IDE config dir>/options/pokemon-jetbrains.xml`).

## How it works

The original extension keeps all Pokémon behavior in a self-contained webview
bundle (`media/main-bundle.js`, built from `src/panel/` upstream). This plugin
reuses that bundle **unmodified** inside a JCEF browser:

- `PokemonBrowserPanel` generates the same HTML the VS Code extension generates,
  and injects a small JavaScript shim that replaces `acquireVsCodeApi()`:
  `getState`/`setState` round-trip into the IDE's persistent settings, and
  webview→host messages arrive via `JBCefJSQuery`.
- Host→webview commands (`spawn-pokemon`, `delete-pokemon`, `reset-pokemon`, …)
  are delivered with `window.postMessage`, exactly like VS Code does.
- Sprites/backgrounds are bundled as `media.zip` and extracted to the IDE system
  directory on first use (JCEF loads the panel over `file://`).

## Credits

All Pokémon logic, sprites, and assets come from
[jakobhoeg/vscode-pokemon](https://github.com/jakobhoeg/vscode-pokemon) (MIT) by
Jakob Hoeg Mørk, and its sprite sources (see the PMD sprite credits in that repo).
Pokémon is © Nintendo / Game Freak / The Pokémon Company; this is a
non-commercial fan project.
