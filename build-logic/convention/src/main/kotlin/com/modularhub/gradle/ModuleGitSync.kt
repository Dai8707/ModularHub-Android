package com.modularhub.gradle

import java.io.File

/**
 * Settings 阶段自动拉取 source 模式缺失的组件仓（已有且 remote 正确则跳过）。
 * origin 与 modules.yaml 不一致时：直接删根目录重 clone（本地改动一并清掉）。
 */
object ModuleGitSync {

    fun syncSourceModules(rootDir: File, modules: List<ResolvedModule>) {
        val sourceModules = modules.filter { it.enabled && it.mode == ModuleMode.SOURCE }
        if (sourceModules.isEmpty()) {
            println("[modularhub] no source-mode modules to sync")
            return
        }
        sourceModules.forEach { syncOne(rootDir, it) }
    }

    fun syncOne(rootDir: File, module: ResolvedModule) {
        val relativePath = module.projectDirRelativePath
            ?: error("Module '${module.name}' is missing source.path")

        val moduleDir = resolve(rootDir, relativePath)
        val repoRelative = module.gitRepoRelativePath
            ?: error("Module '${module.name}' source.path must be inside a project directory")
        val repoDir = resolve(rootDir, repoRelative)

        val hasBuildFile =
            moduleDir.resolve("build.gradle").isFile || moduleDir.resolve("build.gradle.kts").isFile

        if (hasBuildFile) {
            when (ensureCorrectRepo(repoDir, module)) {
                EnsureResult.OK_SKIP -> {
                    println("[modularhub][skip] ${module.name}: already present")
                    return
                }
                EnsureResult.REPLACED,
                EnsureResult.CLONED,
                -> {
                    requireBuildFile(moduleDir, module)
                    return
                }
            }
        }

        if (module.isPlaceholderGit) {
            println(
                "[modularhub][skip] ${module.name}: git url is placeholder. " +
                    "Put source at ${repoDir.absolutePath} or update modules.yaml",
            )
            return
        }

        ensureCorrectRepo(repoDir, module)
        requireBuildFile(moduleDir, module)
    }

    private enum class EnsureResult {
        OK_SKIP,
        CLONED,
        REPLACED,
    }

    /**
     * 保证 repoDir 对应 YAML 的 git.url。
     * - 无目录 / 空目录 → clone
     * - 有 .git 且 remote 一致 → skip（不 fetch）
     * - remote 不一致 → 删掉仓库根目录重 clone（含本地改动）
     * - 有源码无 .git → 当作本地联调目录，跳过
     */
    private fun ensureCorrectRepo(repoDir: File, module: ResolvedModule): EnsureResult {
        if (module.isPlaceholderGit) {
            return EnsureResult.OK_SKIP
        }

        val gitUrl = module.gitUrl ?: error("Module '${module.name}' has no git.url configured")
        val gitRef = module.gitRef ?: "main"
        val gitDir = repoDir.resolve(".git")

        if (!gitDir.exists()) {
            if (repoDir.exists()) {
                val children = repoDir.listFiles()?.filterNot { it.name == ".DS_Store" }.orEmpty()
                if (children.isNotEmpty()) {
                    // 纯本地目录（无 git）：保留，不覆盖
                    println(
                        "[modularhub][warn] ${module.name}: local source without .git at " +
                            "${repoDir.absolutePath}; skip remote check / re-clone",
                    )
                    return EnsureResult.OK_SKIP
                }
            }
            println("[modularhub][clone] ${module.name} -> $gitUrl")
            repoDir.parentFile?.mkdirs()
            runGit(listOf("clone", gitUrl, repoDir.absolutePath))
            checkoutRef(repoDir, module.name, gitRef)
            return EnsureResult.CLONED
        }

        val actual = runGit(listOf("-C", repoDir.absolutePath, "remote", "get-url", "origin")).trim()
        if (sameGitRemote(gitUrl, actual)) {
            return EnsureResult.OK_SKIP
        }

        println(
            "[modularhub][replace] ${module.name}: remote mismatch, remove & re-clone whole repo\n" +
                "  was: $actual\n" +
                "  now: $gitUrl",
        )
        if (!repoDir.deleteRecursively()) {
            throw IllegalStateException(
                "Failed to delete mismatched module dir: ${repoDir.absolutePath}",
            )
        }
        repoDir.parentFile?.mkdirs()
        runGit(listOf("clone", gitUrl, repoDir.absolutePath))
        checkoutRef(repoDir, module.name, gitRef)
        return EnsureResult.REPLACED
    }

    private fun checkoutRef(repoDir: File, moduleName: String, gitRef: String) {
        println("[modularhub][checkout] $moduleName @$gitRef")
        runGit(listOf("-C", repoDir.absolutePath, "checkout", gitRef))
    }

    private fun requireBuildFile(moduleDir: File, module: ResolvedModule) {
        require(
            moduleDir.resolve("build.gradle").isFile || moduleDir.resolve("build.gradle.kts").isFile,
        ) {
            "Module '${module.name}' build file missing at ${moduleDir.absolutePath}"
        }
    }

    /** 归一化后比较，兼容 SSH / HTTPS / 有无 .git 后缀。 */
    internal fun sameGitRemote(expected: String, actual: String): Boolean {
        val a = normalizeGitRemote(expected)
        val b = normalizeGitRemote(actual)
        return a.isNotEmpty() && a == b
    }

    internal fun normalizeGitRemote(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""

        url = url.removeSuffix("/")
        if (url.endsWith(".git", ignoreCase = true)) {
            url = url.dropLast(4)
        }

        val scp = Regex("""^git@([^:]+):(.+)$""")
        val scpMatch = scp.matchEntire(url)
        if (scpMatch != null) {
            val host = scpMatch.groupValues[1].lowercase()
            val path = scpMatch.groupValues[2].trimStart('/').lowercase()
            return "$host/$path"
        }

        val ssh = Regex("""^ssh://(?:git@)?([^/]+)/(.+)$""", RegexOption.IGNORE_CASE)
        val sshMatch = ssh.matchEntire(url)
        if (sshMatch != null) {
            val host = sshMatch.groupValues[1].lowercase()
            val path = sshMatch.groupValues[2].trimStart('/').lowercase()
            return "$host/$path"
        }

        val http = Regex("""^https?://([^/]+)/(.+)$""", RegexOption.IGNORE_CASE)
        val httpMatch = http.matchEntire(url)
        if (httpMatch != null) {
            val host = httpMatch.groupValues[1].lowercase()
            val path = httpMatch.groupValues[2].trimStart('/').lowercase()
            return "$host/$path"
        }

        return url.lowercase()
    }

    private fun resolve(rootDir: File, path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else rootDir.resolve(path)
    }

    private fun runGit(args: List<String>): String {
        val process = ProcessBuilder(listOf("git") + args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) {
            throw IllegalStateException(
                "git ${args.joinToString(" ")} failed (exit=$code): ${output.ifBlank { "no output" }}",
            )
        }
        return output
    }
}
