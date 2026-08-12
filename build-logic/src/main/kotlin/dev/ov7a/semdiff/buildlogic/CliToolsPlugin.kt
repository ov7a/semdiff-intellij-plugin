package dev.ov7a.semdiff.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Provisions the real CLI binaries the tool integration tests run against, and tells the tests
 * where to find them.
 *
 * `SEMDIFF_TOOLS_DIR` overrides provisioning entirely so CI can pre-seed a cache; the layout it
 * must provide is `<dir>/<tool-id>/<version>/<executable>`.
 *
 * Written as a plugin class rather than a precompiled script plugin because every lambda in a
 * script plugin captures the script object, which the configuration cache cannot serialize.
 */
class CliToolsPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = with(project) {
        val host = providers.of(HostPlatformSource::class.java) {}
        val toolsDirOverride = providers.environmentVariable("SEMDIFF_TOOLS_DIR")
        val requireTools = providers.gradleProperty("semdiff.requireTools").map(String::toBoolean).orElse(false)
        val updateGolden = providers.gradleProperty("semdiff.updateGolden").map(String::toBoolean).orElse(false)
        val rootDirectory = isolated.rootProject.projectDirectory
        val testDataDirectory = rootDirectory.dir("test-data").asFile.absolutePath

        // The binaries are large (difftastic alone is ~125 MB) and more than one project's tests
        // need them, so exactly one project downloads into a shared directory and the others just
        // depend on that task by path.
        val isOwner = path == OWNER_PROJECT
        val provisionAll = if (isOwner) {
            tasks.register("provisionCliTools") {
                group = "build setup"
                description = "Downloads and verifies the pinned CLI diff tools used by the integration tests."
            }
        } else {
            null
        }

        val toolProperties = mutableMapOf<String, Provider<String>>()

        CliTools.all.forEach { spec ->
            val installDirectory = rootDirectory.dir("build/cli-tools/${spec.id}/${spec.version}")

            if (isOwner) {
                val overridePresent = toolsDirOverride.map { true }.orElse(false)
                val provision = tasks.register(
                    "provision${spec.id.replaceFirstChar(Char::uppercase)}",
                    ProvisionCliToolTask::class.java,
                ) {
                    group = "build setup"
                    description = "Downloads ${spec.id} ${spec.version}."

                    toolId.set(spec.id)
                    toolVersion.set(spec.version)
                    executableInArchive.set(spec.executableInArchive)
                    hostPlatform.set(host)
                    downloadUrl.set(
                        host.map { spec.urlFor(parseHostPlatform(it)).orEmpty() }.filter(String::isNotEmpty),
                    )
                    sha256.set(
                        host.map { spec.assets[parseHostPlatform(it)]?.sha256.orEmpty() }.filter(String::isNotEmpty),
                    )
                    this.installDirectory.set(installDirectory)

                    // An externally provided tools directory replaces provisioning outright.
                    onlyIf("SEMDIFF_TOOLS_DIR is not set", NotOverridden(overridePresent))
                }
                provisionAll?.configure { dependsOn(provision) }
            }

            toolProperties["semdiff.tool.${spec.id}"] = toolsDirOverride
                .map { "$it/${spec.id}/${spec.version}/${spec.executableInArchive}" }
                .orElse(providers.provider { installDirectory.file(spec.executableInArchive).asFile.absolutePath })
        }

        tasks.withType<Test>().configureEach {
            dependsOn(if (isOwner) "provisionCliTools" else "$OWNER_PROJECT:provisionCliTools")

            val arguments = objects.newInstance(SystemPropertyArguments::class.java)
            toolProperties.forEach { (key, value) -> arguments.properties.put(key, value) }
            arguments.properties.put("semdiff.requireTools", requireTools.map(Boolean::toString))
            arguments.properties.put("semdiff.updateGolden", updateGolden.map(Boolean::toString))
            arguments.properties.put("semdiff.testData", testDataDirectory)
            jvmArgumentProviders.add(arguments)

            // Regenerating goldens rewrites checked-in files, so the task must never be up to date.
            outputs.upToDateWhen(NotUpdatingGoldens(updateGolden))
        }
    }

    private companion object {
        /** The project that downloads the binaries; every other consumer depends on its task. */
        const val OWNER_PROJECT = ":"
    }
}
