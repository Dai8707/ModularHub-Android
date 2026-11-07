package com.modularhub.gradle

import java.io.File

/**
 * 把已落地且 git 可用的 source 组件仓注册为 IDE 嵌套 Git 根。
 * 仅在本地生成 .idea/vcs.xml（不提交），避免壳仓里预登记尚不存在的路径导致
 * “Invalid VCS root mappings”。
 */
object IdeaVcsMappings {

    fun write(rootDir: File, modules: List<ResolvedModule>) {
        val repoDirs = modules
            .filter { it.enabled && it.mode == ModuleMode.SOURCE }
            .mapNotNull { it.gitRepoRelativePath }
            .distinct()
            .map { rootDir.resolve(it) }
            .filter { isUsableGitWorkTree(it) }
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

    /** 仅目录存在不够：必须是可用的 git work tree，否则 IDE 会报 Invalid VCS root。 */
    private fun isUsableGitWorkTree(repoDir: File): Boolean {
        if (!repoDir.isDirectory || !repoDir.resolve(".git").exists()) return false
        return try {
            val process = ProcessBuilder(
                "git",
                "-C",
                repoDir.absolutePath,
                "rev-parse",
                "--is-inside-work-tree",
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor() == 0 && output.equals("true", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}
