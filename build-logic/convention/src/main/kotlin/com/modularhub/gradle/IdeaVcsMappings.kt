package com.modularhub.gradle

import java.io.File

/**
 * 把 source 模式组件仓注册为 IDE 嵌套 Git 根。
 * 壳仓仍 gitignore `modules/*`，但 Android Studio 会按各组件自己的仓库显示颜色/状态，而不是整片 Ignored。
 */
object IdeaVcsMappings {

    fun write(rootDir: File, modules: List<ResolvedModule>) {
        val repoDirs = modules
            .filter { it.enabled && it.mode == ModuleMode.SOURCE }
            .mapNotNull { it.gitRepoRelativePath }
            .distinct()
            .map { rootDir.resolve(it) }
            .filter { it.resolve(".git").exists() }
            .sortedBy { it.invariantSeparatorsPath }

        val ideaDir = rootDir.resolve(".idea")
        ideaDir.mkdirs()
        val file = ideaDir.resolve("vcs.xml")

        val projectDirToken = "\$PROJECT_DIR$"
        val mappings = buildString {
            appendLine("""    <mapping directory="$projectDirToken" vcs="Git" />""")
            repoDirs.forEach { dir ->
                val rel = dir.relativeTo(rootDir).invariantSeparatorsPath
                appendLine("""    <mapping directory="$projectDirToken/$rel" vcs="Git" />""")
            }
        }

        file.writeText(
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project version="4">
            |  <component name="VcsDirectoryMappings">
            |$mappings  </component>
            |</project>
            |
            """.trimMargin(),
        )
        println("[modularhub] wrote .idea/vcs.xml (${repoDirs.size} nested git roots)")
    }
}
