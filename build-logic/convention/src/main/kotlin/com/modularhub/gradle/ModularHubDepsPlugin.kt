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

    private fun resolve(name: String): Any {
        val module = modules.firstOrNull { it.name == name }
            ?: error("Module '$name' not found in modules.yaml")

        if (!module.enabled) {
            throw IllegalStateException("Module '$name' is disabled in modules.yaml")
        }

        return when (module.mode) {
            ModuleMode.SOURCE -> project.project(module.gradlePath)
            ModuleMode.AAR -> {
                val version = module.version
                    ?: error("Module '$name' is in aar mode but aar.version is missing")
                val artifact = module.artifact
                    ?: error("Module '$name' is in aar mode but aar.artifact is missing")
                "${module.mavenGroup}:$artifact:$version"
            }
        }
    }
}
