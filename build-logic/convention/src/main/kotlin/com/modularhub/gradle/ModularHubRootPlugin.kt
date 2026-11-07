package com.modularhub.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
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
        val profile = project.gradle.extensions.extraProperties
            .get("modularHubProfile")?.toString() ?: "dev"

        project.extensions.add("modularHubModules", modules)
        project.extensions.extraProperties.set("modularHubModules", modules)
        project.extensions.extraProperties.set("modularHubProfile", profile)

        project.subprojects { sub ->
            sub.extensions.extraProperties.set("modularHubModules", modules)
        }

        registerTasks(project, modules, profile)
    }

    private fun registerTasks(
        rootProject: Project,
        modules: List<ResolvedModule>,
        profile: String,
    ) {
        registerListModulesTask(rootProject, modules, profile)
        registerValidateModulesYamlTask(rootProject, modules, profile)
        registerPublishModulesTask(rootProject, modules)
    }

    private fun registerListModulesTask(
        rootProject: Project,
        modules: List<ResolvedModule>,
        profile: String,
    ) {
        val taskProvider: TaskProvider<DefaultTask> =
            rootProject.tasks.register("listModules", DefaultTask::class.java)
        taskProvider.configure { task ->
            task.group = "modularhub"
            task.description = "Print resolved module manifest (mode, version, path)"
            task.doLast {
                println("profile=$profile")
                modules.forEach { module ->
                    val coord = runCatching { module.coordinate }.getOrElse { "missing-version" }
                    val path = module.projectDirRelativePath ?: "-"
                    val git = module.gitUrl ?: "-"
                    println(
                        "[${if (module.enabled) "ON " else "OFF"}] " +
                            "${module.name} (${module.type}) " +
                            "mode=${module.mode.name.lowercase()} " +
                            "coord=$coord path=$path git=$git",
                    )
                }
            }
        }
    }

    private fun registerValidateModulesYamlTask(
        rootProject: Project,
        modules: List<ResolvedModule>,
        profile: String,
    ) {
        val taskProvider: TaskProvider<DefaultTask> =
            rootProject.tasks.register("validateModulesYaml", DefaultTask::class.java)
        taskProvider.configure { task ->
            task.group = "modularhub"
            task.description = "Validate modules.yaml structure and required fields"
            task.doLast {
                modules.forEach { module ->
                    check(module.name.isNotBlank()) { "Module name must not be blank" }
                    if (!module.enabled) return@forEach

                    require(!module.version.isNullOrBlank()) {
                        "Module '${module.name}' aar.version is required (all builds use versioned coordinates)"
                    }
                    require(!module.artifact.isNullOrBlank()) {
                        "Module '${module.name}' aar.artifact is required"
                    }

                    if (module.mode == ModuleMode.SOURCE) {
                        require(!module.projectDirRelativePath.isNullOrBlank()) {
                            "Module '${module.name}' source.path is required in source mode"
                        }
                        val dir = resolveProjectDir(rootProject, module.projectDirRelativePath!!)
                        require(dir.resolve("build.gradle").isFile || dir.resolve("build.gradle.kts").isFile) {
                            "Module '${module.name}' source path invalid: ${dir.absolutePath}. " +
                                "Check modules.yaml git.url (Gradle Sync 会自动 clone 缺失仓库)"
                        }
                    }
                }

                if (profile == "release") {
                    val sourceEnabled = modules.filter { it.enabled && it.mode == ModuleMode.SOURCE }
                    require(sourceEnabled.isEmpty()) {
                        "release profile forbids source mode: ${sourceEnabled.joinToString { it.name }}"
                    }
                }

                println(
                    "modules.yaml validation passed " +
                        "(profile=$profile, ${modules.count { it.enabled }} enabled modules).",
                )
            }
        }
    }

    private fun registerPublishModulesTask(rootProject: Project, modules: List<ResolvedModule>) {
        val taskProvider: TaskProvider<DefaultTask> =
            rootProject.tasks.register("publishModules", DefaultTask::class.java)
        taskProvider.configure { task ->
            task.group = "modularhub"
            task.description =
                "Publish source-mode modules to mavenLocal (+ GitHub Packages when gpr.token is set)"
            task.doLast {
                val sourceModules = modules.filter { it.enabled && it.mode == ModuleMode.SOURCE }
                if (sourceModules.isEmpty()) {
                    println("No source-mode modules to publish. aar-mode modules are consumed from Maven.")
                    return@doLast
                }

                val mavenUrl = readMavenRepository(rootProject)
                val token = readGithubToken(rootProject)
                val user = readGithubUser(rootProject)
                val publishRemote = !token.isNullOrBlank() && !mavenUrl.isNullOrBlank()
                if (!publishRemote) {
                    println(
                        "remote publish skipped (tokenSet=${!token.isNullOrBlank()}, " +
                            "mavenUrl=${mavenUrl ?: "<empty>"}). See docs/GITHUB_PACKAGES.md",
                    )
                } else {
                    println("remote publish enabled → $mavenUrl (user=${user ?: "<empty>"})")
                }

                publishOrder(sourceModules).forEach { module ->
                    publishSingleModule(rootProject, module, mavenUrl, publishRemote, user, token)
                }
                println(
                    if (publishRemote) {
                        "publishModules finished → mavenLocal + GitHub Packages"
                    } else {
                        "publishModules finished → mavenLocal only"
                    },
                )
            }
        }
    }

    private fun readMavenRepository(rootProject: Project): String? {
        val fromExtra = runCatching {
            rootProject.gradle.extensions.extraProperties
                .get("modularHubMavenRepository")?.toString()
        }.getOrNull()
        if (!fromExtra.isNullOrBlank()) return fromExtra
        return ModuleManifestParser.load(rootProject.rootDir).mavenRepository
    }

    private fun readGithubUser(rootProject: Project): String? =
        rootProject.findProperty("gpr.user")?.toString()?.takeIf { it.isNotBlank() }
            ?: System.getenv("GITHUB_ACTOR")?.takeIf { it.isNotBlank() }
            ?: readGradleUserProp("gpr.user")

    private fun readGithubToken(rootProject: Project): String? =
        rootProject.findProperty("gpr.token")?.toString()?.takeIf { it.isNotBlank() }
            ?: System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }
            ?: readGradleUserProp("gpr.token")

    private fun readGradleUserProp(key: String): String? {
        val file = File(System.getProperty("user.home"), ".gradle/gradle.properties")
        if (!file.isFile) return null
        return file.readLines()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key=") && !it.startsWith("#") }
            ?.substringAfter("=", "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun publishOrder(modules: List<ResolvedModule>): List<ResolvedModule> =
        modules.sortedBy { module ->
            when {
                module.type == "lib" -> 0
                module.name == "feature-main" -> 100
                else -> 50
            }
        }

    private fun publishSingleModule(
        rootProject: Project,
        module: ResolvedModule,
        mavenUrl: String?,
        publishRemote: Boolean,
        user: String?,
        token: String?,
    ) {
        val moduleDir = resolveProjectDir(
            rootProject,
            module.projectDirRelativePath
                ?: error("Module '${module.name}' is missing source.path"),
        )
        require(moduleDir.resolve("build.gradle").isFile || moduleDir.resolve("build.gradle.kts").isFile) {
            "Module '${module.name}' not found at ${moduleDir.absolutePath}. " +
                "Check modules.yaml git.url (Gradle Sync 会自动 clone 缺失仓库)"
        }

        val repoDir = moduleDir.parentFile
            ?: error("Cannot resolve git repo dir for '${module.name}'")
        val gradlePath = module.gradlePath.trimStart(':')
        val version = module.version
            ?: error("Module '${module.name}' is missing aar.version")
        val group = module.mavenGroup
        val wrapper = resolveWrapper(rootProject)

        val targets = buildList {
            add(":$gradlePath:publishReleasePublicationToMavenLocal")
            if (publishRemote) {
                add(":$gradlePath:publishReleasePublicationToGitHubPackagesRepository")
            }
        }
        val targetLabel = if (publishRemote) "mavenLocal + GitHub Packages" else "mavenLocal"
        println("[publish] ${module.name} -> $group:${module.artifact}:$version ($targetLabel)")

        val output = ByteArrayOutputStream()
        val command = mutableListOf(
            wrapper,
            "-p", repoDir.absolutePath,
            *targets.toTypedArray(),
            "-PmodularGroup=$group",
            "-PmodularVersion=$version",
            "-PmodularArtifact=${module.artifact}",
            "--stacktrace",
        )
        if (!mavenUrl.isNullOrBlank()) {
            command.add("-PmodularMavenUrl=$mavenUrl")
        }
        if (!user.isNullOrBlank()) command.add("-Pgpr.user=$user")
        if (!token.isNullOrBlank()) command.add("-Pgpr.token=$token")

        val result = rootProject.exec { spec ->
            // 用壳工程的 wrapper，-p 指向组件仓，避免 gradlew 固定到壳根目录
            spec.workingDir = rootProject.rootDir
            spec.environment("ANDROID_HOME", System.getenv("ANDROID_HOME") ?: "")
            spec.environment("ANDROID_SDK_ROOT", System.getenv("ANDROID_SDK_ROOT") ?: "")
            spec.environment("JAVA_HOME", System.getenv("JAVA_HOME") ?: "")
            spec.environment("HTTP_PROXY", System.getenv("HTTP_PROXY") ?: "")
            spec.environment("HTTPS_PROXY", System.getenv("HTTPS_PROXY") ?: "")
            spec.commandLine(command)
            spec.standardOutput = output
            spec.errorOutput = output
            spec.isIgnoreExitValue = true
        }

        if (result.exitValue != 0) {
            val message = output.toString(Charsets.UTF_8.name()).ifBlank { "publish failed" }
            throw IllegalStateException(
                "Failed to publish '${module.name}' from ${repoDir.absolutePath}: $message",
            )
        }
    }

    private fun resolveWrapper(rootProject: Project): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val name = if (isWindows) "gradlew.bat" else "gradlew"
        val wrapper = rootProject.rootDir.resolve(name)
        require(wrapper.isFile) { "Gradle wrapper not found: ${wrapper.absolutePath}" }
        return wrapper.absolutePath
    }

    private fun resolveProjectDir(rootProject: Project, path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else rootProject.file(path)
    }
}
