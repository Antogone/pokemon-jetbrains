package io.github.anto.pokemon

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * JCEF loads the panel over file://, so the sprites/backgrounds bundled in the
 * plugin jar are extracted once to the IDE system directory.
 */
object PokemonMedia {
    // Bump when the bundled media.zip changes to force re-extraction.
    private const val MEDIA_VERSION = "1.0.0"

    @Synchronized
    fun mediaDir(): Path {
        val target = Paths.get(PathManager.getSystemPath(), "pokemon-jetbrains", "media-$MEDIA_VERSION")
        val marker = target.resolve(".complete")
        if (Files.exists(marker)) {
            return target
        }
        if (Files.exists(target)) {
            target.toFile().deleteRecursively()
        }
        Files.createDirectories(target)
        PokemonMedia::class.java.getResourceAsStream("/pokemon/media.zip")!!.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val out = target.resolve(entry.name).normalize()
                    check(out.startsWith(target)) { "Bad zip entry: ${entry.name}" }
                    if (entry.isDirectory) {
                        Files.createDirectories(out)
                    } else {
                        Files.createDirectories(out.parent)
                        Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING)
                    }
                    entry = zip.nextEntry
                }
            }
        }
        Files.createFile(marker)
        return target
    }
}
