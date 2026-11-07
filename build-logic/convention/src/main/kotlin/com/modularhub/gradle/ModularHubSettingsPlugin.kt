package com.modularhub.gradle

import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import java.net.URI

/**
 * 壳工程不 include 业务模块。
 * Gradle 配置阶段自动拉齐 source 模式缺失的仓库；编译依赖一律走 Maven 坐标。
 */
class ModularHubSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val rootDir = settings.rootDir
        val manifest = ModuleManifestParser.load(rootDir)

        settings.gradle.extensions.extraProperties.set("modularHubModules", manifest.modules)
        settings.gradle.extensions.extraProperties.set("modularHubProfile", manifest.profile)
        settings.gradle.extensions.extraProperties.set(
            "modularHubMavenRepository",
            manifest.mavenRepository,
        )

        val autoSync = settings.providers.gradleProperty("modularHub.autoSync")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(true)
            .get()
        if (autoSync) {
            ModuleGitSync.syncSourceModules(
                rootDir = rootDir,
                modules = manifest.modules,
            )
        }
        // 只登记壳根。modules/ 被壳仓 ignore，不能再挂嵌套 VCS（会 Invalid），目录结构也不改。
        IdeaVcsMappings.write(rootDir)

        settings.dependencyResolutionManagement.repositories { repos ->
            configureRepositories(settings, repos, manifest.mavenRepository)
        }
    }

    private fun configureRepositories(
        settings: Settings,
        repos: RepositoryHandler,
        mavenRepository: String?,
    ) {
        repos.mavenLocal()
        if (mavenRepository.isNullOrBlank()) return

        val user = settings.providers.gradleProperty("gpr.user")
            .orElse(settings.providers.environmentVariable("GITHUB_ACTOR"))
        val token = settings.providers.gradleProperty("gpr.token")
            .orElse(settings.providers.environmentVariable("GITHUB_TOKEN"))

        repos.maven { maven ->
            maven.name = "modularHubMaven"
            maven.url = URI.create(mavenRepository)
            maven.credentials { creds ->
                creds.username = user.orNull.orEmpty()
                creds.password = token.orNull.orEmpty()
            }
        }
    }
}
