package com.modularhub.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class ModularHubDepsPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        @Suppress("UNCHECKED_CAST")
        val modules = project.rootProject.extensions.extraProperties
            .get("modularHubModules") as List<ResolvedModule>

        val extension = project.extensions.create(
            "modularHub",
            ModularHubExtension::class.java,
            project,
            modules,
        )

        project.extensions.extraProperties.set("modularHubExtension", extension)
    }
}

open class ModularHubExtension(
    private val project: Project,
    private val modules: List<ResolvedModule>,
) {

    fun implementation(name: String) {
        project.dependencies.add("implementation", resolve(name))
    }

    fun api(name: String) {
        project.dependencies.add("api", resolve(name))
    }

    fun debugImplementation(name: String) {
        project.dependencies.add("debugImplementation", resolve(name))
    }

    /**
     * 一律解析成带版本号的 Maven 坐标，不使用 project()。
     * mode=source 只决定是否把源码拉到本地，不改变依赖解析方式。
     */
    private fun resolve(name: String): String {
        val module = modules.firstOrNull { it.name == name }
            ?: error("Module '$name' not found in modules.yaml")

        if (!module.enabled) {
            throw IllegalStateException("Module '$name' is disabled in modules.yaml")
        }

        require(!module.version.isNullOrBlank()) {
            "Module '$name' must declare aar.version (local and remote both use versioned coordinates)"
        }
        require(!module.artifact.isNullOrBlank()) {
            "Module '$name' must declare aar.artifact"
        }

        return module.coordinate
    }
}
