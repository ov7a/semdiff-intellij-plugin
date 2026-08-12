package dev.ov7a.semdiff.buildlogic

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Reads the host OS and architecture.
 *
 * Goes through a [ValueSource] rather than `System.getProperty` at configuration time so the
 * configuration cache stays valid and correctly invalidates when the host changes.
 */
abstract class HostPlatformSource : ValueSource<String, ValueSourceParameters.None> {

    override fun obtain(): String {
        val osName = System.getProperty("os.name").lowercase()
        val os = when {
            osName.contains("mac") || osName.contains("darwin") -> "macos"
            osName.contains("win") -> "windows"
            else -> "linux"
        }

        val archName = System.getProperty("os.arch").lowercase()
        val arch = when (archName) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            else -> archName
        }

        return "$os-$arch"
    }
}

fun parseHostPlatform(value: String): HostPlatform {
    val (os, arch) = value.split('-', limit = 2)
    return HostPlatform(os, arch)
}
