package com.modularhub.gradle

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

class ModularHubSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val rootDir = settings.rootDir
        val modules = ModuleManifestParser.load(rootDir)

        settings.gradle.extensions.extraProperties.set("modularHubModules", modules)
        settings.gradle.extensions.extraProperties.set(
            "modularHubMavenRepository",
            readMavenRepository(rootDir),
        )

        modules.filter { it.enabled && it.mode == ModuleMode.SOURCE }.forEach { module ->
            val relativePath = module.projectDirRelativePath
                ?: error("Module '${module.name}' is in source mode but source.path is missing")

            val projectDir = resolveProjectDir(rootDir, relativePath)
            require(projectDir.resolve("build.gradle").isFile) {
                "Source module '${module.name}' path does not contain a Gradle build file: ${projectDir.absolutePath}"
            }

            settings.include(module.gradlePath)
            settings.project(module.gradlePath).projectDir = projectDir
        }
    }

    private fun readMavenRepository(rootDir: File): String? {
        val manifestFile = rootDir.resolve("modules.yaml")
        if (!manifestFile.isFile) return null
        val yaml = org.yaml.snakeyaml.Yaml()
        @Suppress("UNCHECKED_CAST")
        val manifest = yaml.load<Map<String, Any>>(manifestFile.readText())
        val defaults = manifest["defaults"] as? Map<*, *> ?: return null
        val maven = defaults["maven"] as? Map<*, *> ?: return null
        return maven["repository"]?.toString()
    }

    private fun resolveProjectDir(rootDir: File, path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else rootDir.resolve(path)
    }
}
