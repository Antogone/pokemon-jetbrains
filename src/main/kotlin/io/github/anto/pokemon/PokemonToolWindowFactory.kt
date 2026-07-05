package io.github.anto.pokemon

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.content.ContentFactory
import javax.swing.SwingConstants

class PokemonToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (!JBCefApp.isSupported()) {
            val label = JBLabel(
                "JCEF browser is not supported in this IDE runtime, Pokemon cannot start.",
                SwingConstants.CENTER,
            )
            toolWindow.contentManager.addContent(
                ContentFactory.getInstance().createContent(label, "", false),
            )
            return
        }

        val panel = PokemonBrowserPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)

        toolWindow.setTitleActions(
            listOf(
                spawnPokemonAction(panel),
                spawnRandomAction(panel),
                removePokemonAction(project, panel),
            ),
        )
        toolWindow.setAdditionalGearActions(
            DefaultActionGroup(
                changeSizeAction(panel),
                changeThemeAction(panel),
                rollCallAction(panel),
                removeAllAction(panel),
                openSettingsAction(project),
            ),
        )
    }

    private fun spawnPokemonAction(panel: PokemonBrowserPanel) =
        object : DumbAwareAction("Spawn Pokemon", "Choose a Pokemon to spawn", AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) {
                val entries = PokemonCatalog.entries
                JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(entries)
                    .setTitle("Spawn Pokemon")
                    .setRenderer(SimpleListCellRenderer.create("") { "#${it.id}  ${it.name}" })
                    .setNamerForFiltering { it.name }
                    .setItemChosenCallback { entry -> chooseColorAndSpawn(e, panel, entry) }
                    .createPopup()
                    .showInBestPositionFor(e.dataContext)
            }
        }

    private fun chooseColorAndSpawn(e: AnActionEvent, panel: PokemonBrowserPanel, entry: PokemonEntry) {
        val colors = entry.possibleColors.filter { it != "null" }
        if (colors.size <= 1) {
            spawn(panel, entry, colors.firstOrNull() ?: "default")
            return
        }
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(colors)
            .setTitle("Color for ${entry.name}")
            .setItemChosenCallback { color -> spawn(panel, entry, color) }
            .createPopup()
            .showInBestPositionFor(e.dataContext)
    }

    private fun spawn(panel: PokemonBrowserPanel, entry: PokemonEntry, color: String) {
        panel.spawnPokemon(entry, color)
    }

    private fun spawnRandomAction(panel: PokemonBrowserPanel) =
        object : DumbAwareAction("Spawn Random Pokemon", "Spawn a random Pokemon", AllIcons.Actions.Lightning) {
            override fun actionPerformed(e: AnActionEvent) {
                // Pick host-side like VS Code does; the webview's own
                // spawn-random-pokemon handler sends a broken generation string.
                val entry = PokemonCatalog.entries.random()
                spawn(panel, entry, maybeMakeShiny(entry))
            }
        }

    /** Same odds as upstream's maybeMakeShiny (1 in 8192, the authentic game odds). */
    private fun maybeMakeShiny(entry: PokemonEntry): String {
        val colors = entry.possibleColors.filter { it != "null" }
        if ("shiny" in colors && (0 until 8192).random() == 0) {
            return "shiny"
        }
        return colors.firstOrNull() ?: "default"
    }

    private fun removePokemonAction(project: Project, panel: PokemonBrowserPanel) =
        object : DumbAwareAction("Remove Pokemon", "Choose a Pokemon to remove", AllIcons.General.Remove) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.requestPokemonList().whenComplete { list, error ->
                    ApplicationManager.getApplication().invokeLater {
                        if (error != null || list.isNullOrEmpty()) {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("Pokemon")
                                .createNotification("There are no Pokemon to remove.", NotificationType.INFORMATION)
                                .notify(project)
                            return@invokeLater
                        }
                        JBPopupFactory.getInstance()
                            .createPopupChooserBuilder(list)
                            .setTitle("Remove Pokemon")
                            .setRenderer(SimpleListCellRenderer.create("") { "${it.name} (${it.color} ${it.type})" })
                            .setItemChosenCallback { chosen ->
                                panel.postMessage(mapOf("command" to "delete-pokemon", "name" to chosen.name))
                            }
                            .createPopup()
                            .showInBestPositionFor(e.dataContext)
                    }
                }
            }
        }

    private fun removeAllAction(panel: PokemonBrowserPanel) =
        object : DumbAwareAction("Remove All Pokemon", "Remove every Pokemon from the panel", null) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.postMessage(mapOf("command" to "reset-pokemon"))
            }
        }

    private fun rollCallAction(panel: PokemonBrowserPanel) =
        object : DumbAwareAction("Roll Call", "Every Pokemon says hello", null) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.postMessage(mapOf("command" to "roll-call"))
            }
        }

    private fun changeSizeAction(panel: PokemonBrowserPanel) =
        object : DumbAwareAction("Change Pokemon Size", "Set the Pokemon sprite size", null) {
            override fun actionPerformed(e: AnActionEvent) {
                JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(PokemonSettings.SIZES)
                    .setTitle("Pokemon Size")
                    .setItemChosenCallback { size ->
                        PokemonSettings.getInstance().state.pokemonSize = size
                        panel.reload()
                    }
                    .createPopup()
                    .showInBestPositionFor(e.dataContext)
            }
        }

    private fun openSettingsAction(project: Project) =
        object : DumbAwareAction("Settings…", "Open the Pokemon settings page", null) {
            override fun actionPerformed(e: AnActionEvent) {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, PokemonConfigurable::class.java)
            }
        }

    private fun changeThemeAction(panel: PokemonBrowserPanel) =
        object : DumbAwareAction("Change Theme", "Set the background theme", null) {
            override fun actionPerformed(e: AnActionEvent) {
                JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(PokemonSettings.THEMES)
                    .setTitle("Background Theme")
                    .setItemChosenCallback { theme ->
                        PokemonSettings.getInstance().state.theme = theme
                        panel.reload()
                    }
                    .createPopup()
                    .showInBestPositionFor(e.dataContext)
            }
        }
}
