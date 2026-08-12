plugins {
    id("semdiff.pure-module")
    // Version comes from build-logic.
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
