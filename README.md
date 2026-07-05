# Pokemon for JetBrains IDEs

[Download from Marketplace](https://plugins.jetbrains.com/plugin/32763-pokemon)

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

## Build from source

Requires **JDK 21**. The Gradle wrapper is included.

```sh
./gradlew buildPlugin
# -> build/distributions/pokemon-jetbrains-<version>.zip

# Optional: run a sandboxed IDE with the plugin installed
./gradlew runIde
```

The build auto-detects a locally installed DataSpell/PyCharm to compile against;
without one it downloads IntelliJ Community 2024.2 (one-time, ~1 GB).

### Regenerating the bundled webview assets

`src/main/resources/pokemon/media.zip` (sprites + compiled `main-bundle.js`) and
`pokemon-catalog.json` are generated from the upstream repo (needs node/npm):

```sh
git clone https://github.com/jakobhoeg/vscode-pokemon
cd vscode-pokemon
npm install

# Build only the webview bundle (first entry of the upstream webpack config):
cat > webpack.panel-only.js <<'EOF'
const cfgs = require('./webpack.config.js');
const c = cfgs[0];
c.mode = 'production';
c.devtool = false;
module.exports = c;
EOF
npx webpack --config webpack.panel-only.js

# Package the assets for the plugin:
cd media && zip -qr ../../pokemon-jetbrains/src/main/resources/pokemon/media.zip . && cd ../..
```

If the sprite/data set changed, bump `MEDIA_VERSION` in `PokemonMedia.kt` so the
new assets are re-extracted, and regenerate `pokemon-catalog.json` from
`src/common/pokemon-data.ts` (it is a plain JSON projection of that file:
type, name, id, generation, originalSpriteSize, possibleColors).

## Credits

All Pokémon logic, sprites, and assets come from
[jakobhoeg/vscode-pokemon](https://github.com/jakobhoeg/vscode-pokemon) (MIT) by
Jakob Hoeg Mørk, and its sprite sources (see the PMD sprite credits in that repo).
Pokémon is © Nintendo / Game Freak / The Pokémon Company; this is a
non-commercial fan project.
