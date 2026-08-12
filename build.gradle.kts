import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("semdiff.kotlin-common")
    id("semdiff.cli-tools")
    // Version comes from build-logic, which puts the plugin on the build classpath.
    id("org.jetbrains.intellij.platform")
}

group = "dev.ov7a.semdiff"
// version lives in gradle.properties so a release build can override it with -Pversion=<tag>

dependencies {
    intellijPlatform {
        intellijIdea(libs.versions.intellijPlatform)
        testFramework(TestFrameworkType.Platform)
    }


    // Plain project dependencies, not `intellijPlatformPluginModule`: that configuration makes the
    // root project read the submodules' extensions, which violates Isolated Projects. We are not
    // using v2 content modules, so bundling the jars into the distribution's lib/ is equivalent.
    implementation(project(":semdiff-model"))
    implementation(project(":semdiff-tools"))
    implementation(project(":semdiff-ide"))
    implementation(project(":semdiff-ui-diff"))
    implementation(project(":semdiff-ui-settings"))

    // Platform test fixtures are JUnit 3/4. These tests live in the root project because that is
    // where plugin.xml is on the test classpath, so the plugin's services and extensions register.
    testImplementation(libs.junit.vintage.engine)
}

// kotlinx-serialization drags kotlin-stdlib back in transitively; the IDE already provides it and
// shipping a second copy inside the plugin is unsupported.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.ov7a.semdiff"
        name = "Semantic Diff"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }

        vendor {
            name = "ov7a"
        }
    }

    pluginVerification {
        ides {
            // Exactly the platform this is compiled and tested against, which is also the only
            // version sinceBuild claims. `recommended()` would pull in releases nothing here has
            // ever run on.
            current()
        }

        // Every problem category is fatal. This used to exclude INTERNAL_API_USAGES for
        // DiffToolSubstitutor; dropping that class removed the plugin's only internal-API usage,
        // so the exemption is gone too.
        failureLevel = FailureLevel.ALL
    }
}

// The IntelliJ Platform Gradle Plugin leaves `buildPlugin` out of `assemble`, so a plain `build`
// produces only the module jars. The distribution zip is this project's actual deliverable, so wire
// it in and let `./gradlew build` mean what it looks like it means.
tasks.named("assemble") {
    dependsOn(tasks.named("buildPlugin"))
}

tasks.named("check") {
    dependsOn(tasks.named("verifyPlugin"))
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs every check in every module. Combine with -Psemdiff.requireTools=true to " +
        "fail instead of skip when a CLI tool is missing."
    dependsOn(tasks.named("check"))
    dependsOn(subprojects.map { "${it.path}:check" })
}
