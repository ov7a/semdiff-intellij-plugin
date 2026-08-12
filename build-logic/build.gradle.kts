plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.serializationGradlePlugin)
    implementation(libs.intellijPlatform.gradlePlugin)

    // Lets precompiled script plugins reference the root build's version catalog.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        register("cliTools") {
            id = "semdiff.cli-tools"
            implementationClass = "dev.ov7a.semdiff.buildlogic.CliToolsPlugin"
        }
    }
}
