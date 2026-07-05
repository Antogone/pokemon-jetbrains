package io.github.anto.pokemon

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.AnActionButton
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.FormBuilder
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page (Settings > Tools > Pokemon). The "Default Pokemon" list is the
 * equivalent of vscode-pokemon's `defaultPokemon` setting: those Pokemon are
 * spawned automatically whenever the panel opens with an empty roster.
 */
class PokemonConfigurable : Configurable {

    private var sizeCombo: ComboBox<String>? = null
    private var themeCombo: ComboBox<String>? = null
    private val listModel = DefaultListModel<Pair<String, String>>()
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Pokemon"

    override fun createComponent(): JComponent {
        val sizes = ComboBox(PokemonSettings.SIZES.toTypedArray())
        val themes = ComboBox(PokemonSettings.THEMES.toTypedArray())
        sizeCombo = sizes
        themeCombo = themes

        val list = JBList(listModel)
        list.cellRenderer = SimpleListCellRenderer.create("") { displayName(it) }
        list.setEmptyText("No default Pokemon (panel starts empty)")

        val decorated = ToolbarDecorator.createDecorator(list)
            .setAddAction { button -> showAddPopup(button) }
            .setRemoveAction {
                list.selectedIndices.sortedDescending().forEach { listModel.remove(it) }
            }
            .disableUpDownActions()
            .createPanel()

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Pokemon size:", sizes)
            .addLabeledComponent("Background theme:", themes)
            .addSeparator()
            .addLabeledComponentFillVertically("Default Pokemon (spawned when the roster is empty):", decorated)
            .panel
        panel = form
        reset()
        return form
    }

    private fun displayName(spec: Pair<String, String>): String {
        val entry = PokemonCatalog.entries.find { it.type == spec.first }
        val name = entry?.name ?: spec.first
        return if (spec.second == "default") name else "$name (${spec.second})"
    }

    private fun showAddPopup(button: AnActionButton) {
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(PokemonCatalog.entries)
            .setTitle("Add Default Pokemon")
            .setRenderer(SimpleListCellRenderer.create("") { "#${it.id}  ${it.name}" })
            .setNamerForFiltering { it.name }
            .setItemChosenCallback { entry ->
                val colors = entry.possibleColors.filter { it != "null" }
                if (colors.size <= 1) {
                    listModel.addElement(entry.type to (colors.firstOrNull() ?: "default"))
                } else {
                    JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(colors)
                        .setTitle("Color for ${entry.name}")
                        .setItemChosenCallback { color -> listModel.addElement(entry.type to color) }
                        .createPopup()
                        .show(button.preferredPopupPoint)
                }
            }
            .createPopup()
            .show(button.preferredPopupPoint)
    }

    private fun currentSpecs(): List<Pair<String, String>> =
        (0 until listModel.size()).map { listModel.get(it) }

    override fun isModified(): Boolean {
        val state = PokemonSettings.getInstance().state
        return sizeCombo?.selectedItem != state.pokemonSize ||
            themeCombo?.selectedItem != state.theme ||
            PokemonSettings.encodeDefaultPokemon(currentSpecs()) != state.defaultPokemon
    }

    override fun apply() {
        val state = PokemonSettings.getInstance().state
        state.pokemonSize = sizeCombo?.selectedItem as? String ?: state.pokemonSize
        state.theme = themeCombo?.selectedItem as? String ?: state.theme
        state.defaultPokemon = PokemonSettings.encodeDefaultPokemon(currentSpecs())
        PokemonBrowserPanel.reloadAll()
    }

    override fun reset() {
        val state = PokemonSettings.getInstance().state
        sizeCombo?.selectedItem = state.pokemonSize
        themeCombo?.selectedItem = state.theme
        listModel.clear()
        PokemonSettings.decodeDefaultPokemon(state.defaultPokemon).forEach(listModel::addElement)
    }

    override fun disposeUIResources() {
        panel = null
        sizeCombo = null
        themeCombo = null
    }
}
