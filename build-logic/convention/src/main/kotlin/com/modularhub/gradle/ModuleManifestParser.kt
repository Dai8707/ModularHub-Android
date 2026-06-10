package com.modularhub.gradle

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

object ModuleManifestParser {

    private const val MANIFEST_FILE = "modules.yaml"
    private const val LOCAL_OVERRIDE_FILE = "local.override.yaml"

    fun load(rootDir: File): List<ResolvedModule> {
        val manifestFile = rootDir.resolve(MANIFEST_FILE)
        require(manifestFile.isFile) {
            "Module manifest not found: ${manifestFile.absolutePath}"
        }

        val manifest = parseYamlFile(manifestFile)
        val override = rootDir.resolve(LOCAL_OVERRIDE_FILE).takeIf { it.isFile }?.let(::parseYamlFile)

        val defaults = manifest["defaults"] as? Map<*, *> ?: emptyMap<String, Any>()
        val profile = (override?.get("profile") ?: manifest["profile"])?.toString() ?: "dev"
        val profileDefaults = readProfileDefaults(manifest, profile)

        val defaultMode = ModuleMode.from(
            (override?.get("defaults") as? Map<*, *>)?.get("mode")?.toString()
                ?: profileDefaults["mode"]?.toString()
                ?: defaults["mode"]?.toString()
                ?: "source",
        )

        val mavenDefaults = defaults["maven"] as? Map<*, *> ?: emptyMap<String, Any>()
        val groupPrefix = mavenDefaults["group_prefix"]?.toString() ?: "com.modularhub"
        val repository = mavenDefaults["repository"]?.toString()

        val modulesRaw = manifest["modules"] as? List<*> ?: emptyList<Any>()
        val overrideModules = (override?.get("modules") as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
        val overrideByName = overrideModules.associateBy { it["name"].toString() }

        return modulesRaw.filterIsInstance<Map<*, *>>().map { moduleMap ->
            val name = moduleMap["name"]?.toString()
                ?: error("Each module entry must define 'name'")

            val moduleOverride = overrideByName[name]
            val moduleProfileOverrides = readModuleProfileOverrides(moduleMap, profile)

            val enabled = moduleOverride?.get("enabled")?.let { it as? Boolean ?: it.toString().toBoolean() }
                ?: moduleMap["enabled"]?.let { it as? Boolean ?: it.toString().toBoolean() }
                ?: true

            val mode = ModuleMode.from(
                moduleOverride?.get("mode")?.toString()
                    ?: moduleProfileOverrides["mode"]?.toString()
                    ?: defaultMode.name.lowercase(),
            )

            val sourceMap = mergeMaps(
                moduleMap["source"] as? Map<*, *>,
                moduleOverride?.get("source") as? Map<*, *>,
            )
            val aarMap = mergeMaps(
                mergeMaps(moduleMap["aar"] as? Map<*, *>, moduleProfileOverrides["aar"] as? Map<*, *>),
                moduleOverride?.get("aar") as? Map<*, *>,
            )
            val gitMap = mergeMaps(
                moduleMap["git"] as? Map<*, *>,
                moduleOverride?.get("git") as? Map<*, *>,
            )

            val gradlePath = sourceMap["gradle_path"]?.toString()?.let(::normalizeGradlePath)
                ?: ":$name"
            val sourcePath = sourceMap["path"]?.toString()

            ResolvedModule(
                name = name,
                type = moduleMap["type"]?.toString() ?: "lib",
                enabled = enabled,
                mode = mode,
                gradlePath = gradlePath,
                projectDirRelativePath = sourcePath,
                mavenGroup = groupPrefix,
                artifact = aarMap["artifact"]?.toString() ?: name,
                version = aarMap["version"]?.toString(),
                gitUrl = gitMap["url"]?.toString(),
                gitRef = gitMap["ref"]?.toString() ?: defaults["git"]?.let { (it as Map<*, *>)["ref"]?.toString() },
            )
        }.also { modules ->
            if (repository.isNullOrBlank()) {
                // Maven repository is optional until AAR mode is used in CI.
            }
        }
    }

    private fun parseYamlFile(file: File): Map<String, Any> {
        FileInputStream(file).use { input ->
            val loaded = Yaml().load<Any?>(input) ?: emptyMap<String, Any>()
            @Suppress("UNCHECKED_CAST")
            return loaded as? Map<String, Any> ?: emptyMap()
        }
    }

    private fun readProfileDefaults(manifest: Map<String, Any>, profile: String): Map<*, *> {
        val profiles = manifest["profiles"] as? Map<*, *> ?: return emptyMap<String, Any>()
        val profileNode = profiles[profile] as? Map<*, *> ?: return emptyMap<String, Any>()
        return profileNode["defaults"] as? Map<*, *> ?: emptyMap<String, Any>()
    }

    private fun readModuleProfileOverrides(moduleMap: Map<*, *>, profile: String): Map<*, *> {
        val profiles = moduleMap["profiles"] as? Map<*, *> ?: return emptyMap<String, Any>()
        return profiles[profile] as? Map<*, *> ?: emptyMap<String, Any>()
    }

    private fun mergeMaps(base: Map<*, *>?, overlay: Map<*, *>?): Map<*, *> {
        if (base == null && overlay == null) return emptyMap<String, Any>()
        if (base == null) return overlay ?: emptyMap<String, Any>()
        if (overlay == null) return base
        return base.toMutableMap().apply { putAll(overlay) }
    }

    private fun normalizeGradlePath(raw: String): String =
        if (raw.startsWith(":")) raw else ":$raw"
}
