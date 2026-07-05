import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.9.0"
}

group = "io.github.anto"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Prefer a locally installed JetBrains IDE (no download); otherwise
        // fall back to downloading IntelliJ Community once.
        val localIde = listOf(
            "${System.getProperty("user.home")}/Applications/DataSpell.app",
            "/Applications/DataSpell.app",
            "${System.getProperty("user.home")}/Applications/PyCharm.app",
            "/Applications/PyCharm.app",
        ).map(::file).firstOrNull { it.exists() }

        if (localIde != null) {
            local(localIde)
        } else {
            create("IC", "2024.2.5")
        }
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    pluginConfiguration {
        id = "io.github.anto.pokemon-jetbrains"
        name = "Pokemon"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
