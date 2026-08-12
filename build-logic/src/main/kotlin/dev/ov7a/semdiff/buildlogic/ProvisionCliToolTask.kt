package dev.ov7a.semdiff.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Downloads one pinned CLI tool from its official release, verifies its checksum and unpacks it.
 *
 * All I/O happens at execution time behind declared inputs and outputs, so the task is
 * configuration-cache friendly and skipped entirely when the binary is already in place.
 */
abstract class ProvisionCliToolTask : DefaultTask() {

    @get:Input
    abstract val toolId: Property<String>

    @get:Input
    abstract val toolVersion: Property<String>

    /** Absent when the tool publishes no binary for this host; the task then fails with a clear message. */
    @get:Input
    @get:Optional
    abstract val downloadUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val sha256: Property<String>

    @get:Input
    abstract val hostPlatform: Property<String>

    @get:Input
    abstract val executableInArchive: Property<String>

    @get:OutputDirectory
    abstract val installDirectory: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fileOperations: FileSystemOperations

    @TaskAction
    fun provision() {
        val url = downloadUrl.orNull ?: throw GradleException(
            "${toolId.get()} ${toolVersion.get()} publishes no binary for ${hostPlatform.get()}. " +
                "Install it manually and point SEMDIFF_TOOLS_DIR at it, or skip its tests.",
        )
        val expectedChecksum = sha256.orNull ?: throw GradleException(
            "No checksum pinned for ${toolId.get()} on ${hostPlatform.get()}.",
        )

        val target = installDirectory.get().asFile
        fileOperations.delete { delete(target) }
        target.mkdirs()

        val archive = File(temporaryDir, URI(url).path.substringAfterLast('/'))
        logger.lifecycle("Downloading ${toolId.get()} ${toolVersion.get()} for ${hostPlatform.get()}")
        URI(url).toURL().openStream().use { input -> archive.outputStream().use(input::copyTo) }

        val actualChecksum = archive.sha256()
        if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
            throw GradleException(
                "Checksum mismatch for $url\n  expected $expectedChecksum\n  actual   $actualChecksum",
            )
        }

        fileOperations.copy {
            from(archives.tarTree(archives.gzip(archive)))
            into(target)
        }

        val executable = target.walkTopDown().firstOrNull { it.isFile && it.name == executableInArchive.get() }
            ?: throw GradleException("'${executableInArchive.get()}' not found in $url")
        executable.setExecutable(true)

        // Flatten so consumers can rely on <installDirectory>/<name> regardless of archive layout.
        if (executable.parentFile != target) {
            val flattened = File(target, executable.name)
            executable.copyTo(flattened, overwrite = true)
            flattened.setExecutable(true)
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
