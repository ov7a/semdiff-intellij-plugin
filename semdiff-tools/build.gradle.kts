plugins {
    id("semdiff.pure-module")
    id("semdiff.cli-tools")
    // Version comes from build-logic.
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(project(":semdiff-model"))
}
