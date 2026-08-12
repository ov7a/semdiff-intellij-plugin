import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("semdiff.kotlin-common")
    id("org.jetbrains.intellij.platform.module")
}

val libs = the<LibrariesForLibs>()

dependencies {
    intellijPlatform {
        intellijIdea(libs.versions.intellijPlatform)
        testFramework(TestFrameworkType.Platform)
    }

    // The platform's test fixtures are JUnit 3/4; the vintage engine lets them run on the same
    // JUnit Platform as the pure modules' Jupiter tests.
    testImplementation(libs.junit.vintage.engine)
}

tasks.withType<Test>().configureEach {
    // Platform tests need a writable system/config directory and the headless flag.
    systemProperty("java.awt.headless", "true")
    systemProperty("idea.force.use.core.classloader", "true")
}
