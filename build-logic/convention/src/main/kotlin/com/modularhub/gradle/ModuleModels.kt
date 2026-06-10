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
    val isPlaceholderGit: Boolean
        get() = gitUrl.isNullOrBlank() ||
            gitUrl.contains("PLACEHOLDER", ignoreCase = true) ||
            gitUrl.startsWith("#")
}
