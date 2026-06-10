package com.modularhub.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.DefaultTask
import java.io.ByteArrayOutputStream
import java.io.File

@Suppress("UNCHECKED_CAST")
class ModularHubRootPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        require(project == project.rootProject) {
            "modularhub.root plugin must be applied on the root project"
        }

        val modules = project.gradle.extensions.extraProperties
            .get("modularHubModules") as List<ResolvedModule>

        project.extensions.add("modularHubModules", modules)
        project.extensions.extraProperties.set("modularHubModules", modules)

        project.subprojects { sub ->
            sub.extensions.extraProperties.set("modularHubModules", modules)
        }

        registerTasks(project, modules)
    }

    private fun registerTasks(rootProject: Project, modules: List<ResolvedModule>) {
        registerListModulesTask(rootProject, modules)
        registerValidateModulesYamlTask(rootProject, modules)
        registerSyncModulesTask(rootProject, modules)
    }

    private fun registerListModulesTask(rootProject: Project, modules: List<ResolvedModule>) {
        val taskProvider: TaskProvider<DefaultTask> =
            rootProject.tasks.register("listModules", DefaultTask::class.java)
        taskProvider.configure { task ->
            task.group = "modularhub"
            task.description = "Print resolved module manifest (mode, version, path)"
            task.doLast {
                modules.forEach { module ->
                    val detail = when (module.mode) {
                        ModuleMode.SOURCE ->
                            "path=${module.projectDirRelativePath}, git=${module.gitUrl ?: "-"}"
                        ModuleMode.AAR ->
                            "coord=${module.mavenGroup}:${module.artifact}:${module.version}"
                    }
                    println(
                        "[${if (module.enabled) "ON " else "OFF"}] " +
                            "${module.name} (${module.type}) mode=${module.mode.name.lowercase()} $detail",
                    )
                }
            }
        }
    }

    private fun registerValidateModulesYamlTask(rootProject: Project, modules: List<ResolvedModule>) {
        val taskProvider: TaskProvider<DefaultTask> =
            rootProject.tasks.register("validateModulesYaml", DefaultTask::class.java)
        taskProvider.configure { task ->
            task.group = "modularhub"
            task.description = "Validate modules.yaml structure and required fields"
            task.doLast {
                modules.forEach { module ->
                    check(module.name.isNotBlank()) { "Module name must not be blank" }
                    if (!module.enabled) return@forEach

                    when (module.mode) {
                        ModuleMode.SOURCE -> {
                            require(!module.projectDirRelativePath.isNullOrBlank()) {
                                "Module '${module.name}' source.path is required in source mode"
                            }
                            val dir = resolveProjectDir(rootProject, module.projectDirRelativePath!!)
                            require(dir.resolve("build.gradle").isFile) {
                                "Module '${module.name}' source path invalid: ${dir.absolutePath}"
                            }
                        }

                        ModuleMode.AAR -> {
                            require(!module.version.isNullOrBlank()) {
                                "Module '${module.name}' aar.version is required in aar mode"
                            }
                            require(!module.artifact.isNullOrBlank()) {
                                "Module '${module.name}' aar.artifact is required in aar mode"
                            }
                        }
                    }
                }
                println("modules.yaml validation passed (${modules.count { it.enabled }} enabled modules).")
            }
        }
    }

    private fun registerSyncModulesTask(rootProject: Project, modules: List<ResolvedModule>) {
        val taskProvider: TaskProvider<DefaultTask> =
            rootProject.tasks.register("syncModules", DefaultTask::class.java)
        taskProvider.configure { task ->
            task.group = "modularhub"
            task.description = "Clone or update git repositories for source-mode modules"
            task.doLast {
                val sourceModules = modules.filter { it.enabled && it.mode == ModuleMode.SOURCE }
                if (sourceModules.isEmpty()) {
                    println("No source-mode modules to sync.")
                    return@doLast
                }

                sourceModules.forEach { module ->
                    syncSingleModule(rootProject, module)
                }
            }
        }
    }

    private fun syncSingleModule(rootProject: Project, module: ResolvedModule) {
        val relativePath = module.projectDirRelativePath
            ?: error("Module '${module.name}' is missing source.path")

        val destDir = resolveProjectDir(rootProject, relativePath)
        val hasBuildFile = destDir.resolve("build.gradle").isFile

        if (hasBuildFile) {
            println("[skip] ${module.name}: already present at ${destDir.absolutePath}")
            return
        }

        if (module.isPlaceholderGit) {
            println(
                "[skip] ${module.name}: git url is placeholder or empty. " +
                    "Create repo later and update modules.yaml, or copy source into ${destDir.absolutePath}",
            )
            return
        }

        val gitUrl = module.gitUrl ?: error("Module '${module.name}' has no git.url configured")
        val gitRef = module.gitRef ?: "main"

        destDir.parentFile?.mkdirs()

        if (!destDir.exists()) {
            println("[clone] ${module.name} -> $gitUrl")
            runGit(rootProject, listOf("clone", gitUrl, destDir.absolutePath))
        } else {
            println("[fetch] ${module.name} -> ${destDir.absolutePath}")
            runGit(rootProject, listOf("-C", destDir.absolutePath, "fetch", "--all", "--tags"))
        }

        println("[checkout] ${module.name} @$gitRef")
        runGit(rootProject, listOf("-C", destDir.absolutePath, "checkout", gitRef))
    }

    private fun runGit(rootProject: Project, args: List<String>) {
        val output = ByteArrayOutputStream()
        val result = rootProject.exec { spec ->
            spec.commandLine(listOf("git") + args)
            spec.standardOutput = output
            spec.errorOutput = output
            spec.isIgnoreExitValue = true
        }

        if (result.exitValue != 0) {
            val message = output.toString(Charsets.UTF_8.name()).ifBlank { "git command failed" }
            throw IllegalStateException("git ${args.joinToString(" ")} failed: $message")
        }
    }

    private fun resolveProjectDir(rootProject: Project, path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else rootProject.file(path)
    }
}
