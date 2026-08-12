package dev.ov7a.semdiff.buildlogic

/**
 * Pinned CLI tools the integration tests execute.
 *
 * Checksums are mandatory: the golden-file suite only means something if every machine runs the
 * exact same binary. Bump a version and its checksums together, then regenerate the goldens with
 * `-Psemdiff.updateGolden=true`.
 */
object CliTools {

    val all: List<CliToolSpec> = listOf(
        CliToolSpec(
            id = "difftastic",
            version = "0.69.0",
            executableInArchive = "difft",
            urlTemplate = "https://github.com/Wilfred/difftastic/releases/download/{version}/{asset}",
            assets = mapOf(
                HostPlatform("macos", "aarch64") to Asset(
                    "difft-aarch64-apple-darwin.tar.gz",
                    "c958b87885a5825a356c5899ac7ecdd752a7942084199f2be4bc0bf8c9de8e33",
                ),
                HostPlatform("macos", "x86_64") to Asset(
                    "difft-x86_64-apple-darwin.tar.gz",
                    "5f5487e7a6e817194a1cef297d2ffb300454371635a4cde865087dbc064730a2",
                ),
                HostPlatform("linux", "aarch64") to Asset(
                    "difft-aarch64-unknown-linux-gnu.tar.gz",
                    "abd2f42d2afd424312b4862aa7c7bb0320447670ae22fabcc5159db03e2dccbd",
                ),
                HostPlatform("linux", "x86_64") to Asset(
                    "difft-x86_64-unknown-linux-gnu.tar.gz",
                    "038db96a0e8fce69f2554e33e04ff75fbf6f96ea45cb4edb9ed6203a2c4750ff",
                ),
            ),
        ),
        CliToolSpec(
            id = "diffsitter",
            version = "0.9.0",
            executableInArchive = "diffsitter",
            urlTemplate = "https://github.com/afnanenayet/diffsitter/releases/download/v{version}/{asset}",
            assets = mapOf(
                HostPlatform("macos", "aarch64") to Asset(
                    "diffsitter-aarch64-apple-darwin.tar.gz",
                    "83b02d6a27ce7f9365f068ffd400dada36e8fa449e48f1f8895dd77912650ecb",
                ),
                HostPlatform("macos", "x86_64") to Asset(
                    "diffsitter-x86_64-apple-darwin.tar.gz",
                    "91e3f5509d85b6702063d91a8a658a3f3d1c375d3a13c30a99bd234a7282a29e",
                ),
                HostPlatform("linux", "aarch64") to Asset(
                    "diffsitter-aarch64-unknown-linux-gnu.tar.gz",
                    "8f651d1db49c8ffb8974faaff442cc9dd8153b07271261da61380968e2db6957",
                ),
                HostPlatform("linux", "x86_64") to Asset(
                    "diffsitter-x86_64-unknown-linux-gnu.tar.gz",
                    "3496d7ae8dfdd3eba92edd1ccd68f79442ff86a48f30d54b4fd522e772e699b9",
                ),
            ),
        ),
        CliToolSpec(
            id = "sem",
            version = "0.21.0",
            executableInArchive = "sem",
            urlTemplate = "https://github.com/Ataraxy-Labs/sem/releases/download/v{version}/{asset}",
            assets = mapOf(
                HostPlatform("macos", "aarch64") to Asset(
                    "sem-darwin-arm64.tar.gz",
                    "7e17372ffdf6477a2b711e173fb783ecd82a4559ee3747985e2397c128d1e6f7",
                ),
                HostPlatform("macos", "x86_64") to Asset(
                    "sem-darwin-x86_64.tar.gz",
                    "b179b996cf6060d74873fc117b2dd94104af9835ff48097c6e9c2923a374dee1",
                ),
                HostPlatform("linux", "aarch64") to Asset(
                    "sem-linux-arm64.tar.gz",
                    "0480663055d3d7c386dabee6e57766205984ac151bd691540bde0b3be64af27b",
                ),
                HostPlatform("linux", "x86_64") to Asset(
                    "sem-linux-x86_64.tar.gz",
                    "4a06f019552add37b4b0693309daaf529eae7f291217d20c291294c790b16b4b",
                ),
            ),
        ),
    )
}

data class CliToolSpec(
    val id: String,
    val version: String,
    val executableInArchive: String,
    val urlTemplate: String,
    val assets: Map<HostPlatform, Asset>,
) {
    fun urlFor(platform: HostPlatform): String? {
        val asset = assets[platform] ?: return null
        return urlTemplate.replace("{version}", version).replace("{asset}", asset.name)
    }
}

data class Asset(val name: String, val sha256: String)

data class HostPlatform(val os: String, val arch: String) {
    override fun toString(): String = "$os-$arch"
}
