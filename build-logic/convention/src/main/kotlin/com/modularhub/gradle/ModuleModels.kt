package com.modularhub.gradle

enum class ModuleMode {
    SOURCE,
    AAR,
    ;

    companion object {
        fun from(raw: String?): ModuleMode =
            when (raw?.lowercase()) {
                "source" -> SOURCE
                "aar" -> AAR
                else -> error("Unsupported module mode: $raw (expected source or aar)")
            }
    }
}

data class ModuleManifest(
    val profile: String,
    val mavenRepository: String?,
    val modules: List<ResolvedModule>,
)

data class ResolvedModule(
    val name: String,
    val type: String,
    val enabled: Boolean,
    val mode: ModuleMode,
    val gradlePath: String,
    val projectDirRelativePath: String?,
    val mavenGroup: String,
    val artifact: String?,
    val version: String?,
    val gitUrl: String?,
    val gitRef: String?,
) {
    val coordinate: String
        get() {
            val artifactId = artifact ?: error("Module '$name' is missing aar.artifact")
            val ver = version ?: error("Module '$name' is missing aar.version")
            return "$mavenGroup:$artifactId:$ver"
        }

    /** Git 仓库落地目录：source.path 指向 library 子模块，仓库根是其父目录。 */
    val gitRepoRelativePath: String?
        get() = projectDirRelativePath?.let { path ->
            val file = java.io.File(path)
            file.parent?.replace('\\', '/')
        }

    val isPlaceholderGit: Boolean
        get() = gitUrl.isNullOrBlank() ||
            gitUrl.contains("PLACEHOLDER", ignoreCase = true) ||
            gitUrl.startsWith("#")
}
