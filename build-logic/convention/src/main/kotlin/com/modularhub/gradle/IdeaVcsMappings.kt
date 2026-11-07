package com.modularhub.gradle

import java.io.File

/**
 * 壳根 + 已 clone 成功的 `modules/project-*` 登记为嵌套 Git 根。
 *
 * 壳仓仍 gitignore `modules/` 里的源码，不提交组件文件。
 * 嵌套根只在 `.git` 可用之后写入本地 `.idea/vcs.xml`（不进壳仓），
 * 避免仓库还没拉下来时 IDE 报 Invalid VCS root。
 * 登记成功后，工程树颜色按各组件自己的仓库显示。
 */
object IdeaVcsMappings {

    /** 拉取过程中先只留壳根，避免 IDE 拿着已删目录的旧映射报 Invalid。 */
    fun writeShellOnly(rootDir: File) {
        writeMappings(rootDir, emptyList())
    }

    fun write(rootDir: File, modules: List<ResolvedModule>) {
        val repoDirs = modules
            .filter { it.enabled && it.mode == ModuleMode.SOURCE }
            .mapNotNull { it.gitRepoRelativePath }
            .distinct()
            .map { resolve(rootDir, it) }
            .filter { isUsableGitWorkTree(it) }
            .sortedBy { it.invariantSeparatorsPath }

        writeMappings(rootDir, repoDirs)
    }

    private fun writeMappings(rootDir: File, repoDirs: List<File>) {
        val ideaDir = rootDir.resolve(".idea")
        ideaDir.mkdirs()
        val file = ideaDir.resolve("vcs.xml")
        val projectDirToken = "\$PROJECT_DIR$"

        val mappings = buildString {
            appendLine("""    <mapping directory="$projectDirToken" vcs="Git" />""")
            repoDirs.forEach { dir ->
                val rel = rootDir.canonicalFile.toPath()
                    .relativize(dir.canonicalFile.toPath())
                    .toString()
                    .replace('\\', '/')
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
        println("[modularhub] wrote .idea/vcs.xml (${repoDirs.size} module git roots)")
    }

    private fun resolve(rootDir: File, path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else rootDir.resolve(path).normalize()
    }

    private fun isUsableGitWorkTree(repoDir: File): Boolean {
        val git = repoDir.resolve(".git")
        if (!repoDir.isDirectory || !git.exists()) return false
        val head = if (git.isDirectory) git.resolve("HEAD") else null
        if (head != null && !head.isFile) return false
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
