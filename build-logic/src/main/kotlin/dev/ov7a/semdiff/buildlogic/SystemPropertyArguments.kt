package dev.ov7a.semdiff.buildlogic

import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes system properties to a test JVM lazily.
 *
 * `Test.systemProperty` resolves eagerly at configuration time, which would force the tool paths
 * (and therefore the host-platform lookup) into the configuration phase.
 */
abstract class SystemPropertyArguments : CommandLineArgumentProvider {

    @get:Input
    abstract val properties: MapProperty<String, String>

    override fun asArguments(): Iterable<String> =
        properties.get().entries.sortedBy { it.key }.map { (key, value) -> "-D$key=$value" }
}
