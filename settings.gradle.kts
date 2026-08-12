import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "semdiff-ij-plugin"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

include(
    "semdiff-model",
    "semdiff-tools",
    "semdiff-ide",
    "semdiff-ui-diff",
    "semdiff-ui-settings",
)
