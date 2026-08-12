package dev.ov7a.semdiff.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when a module that must stay IntelliJ-free acquires an IntelliJ dependency.
 *
 * The pure modules are the ones that can be unit-tested without an IDE; letting a `com.intellij`
 * type leak into them is easy to do by accident and expensive to undo later.
 */
@CacheableTask
abstract class ForbiddenDependenciesTask : DefaultTask() {

    /** Component identifiers on the checked classpath, e.g. `com.example:lib:1.0`. */
    @get:Input
    abstract val componentIds: ListProperty<String>

    @get:Input
    abstract val forbiddenPrefixes: ListProperty<String>

    @get:Input
    abstract val checkedConfiguration: org.gradle.api.provider.Property<String>

    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @TaskAction
    fun check() {
        val prefixes = forbiddenPrefixes.get()
        val violations = componentIds.get().filter { id -> prefixes.any(id::startsWith) }.sorted()

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("${path}: '${checkedConfiguration.get()}' must not contain IntelliJ dependencies, found:\n")
                    violations.forEach { append("  - $it\n") }
                    append("Move the code that needs them into an IntelliJ-facing module.")
                },
            )
        }

        receipt.get().asFile.writeText(
            "checked ${componentIds.get().size} components on ${checkedConfiguration.get()}\n",
        )
    }
}
