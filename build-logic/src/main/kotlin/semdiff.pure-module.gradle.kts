import dev.ov7a.semdiff.buildlogic.ForbiddenDependenciesTask

plugins {
    id("semdiff.kotlin-common")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

val checkNoIntellij = tasks.register<ForbiddenDependenciesTask>("checkNoIntellijDependencies") {
    group = "verification"
    description = "Fails if an IntelliJ dependency reaches this IntelliJ-free module."

    checkedConfiguration = "compileClasspath"
    forbiddenPrefixes = listOf("com.jetbrains.intellij", "com.intellij", "org.jetbrains.intellij")
    componentIds = configurations.named("compileClasspath").flatMap { configuration ->
        configuration.incoming.artifacts.resolvedArtifacts.map { artifacts ->
            artifacts.map { it.id.componentIdentifier.displayName }
        }
    }
    receipt = layout.buildDirectory.file("reports/forbidden-dependencies/compileClasspath.txt")
}

tasks.named("check") {
    dependsOn(checkNoIntellij)
}
