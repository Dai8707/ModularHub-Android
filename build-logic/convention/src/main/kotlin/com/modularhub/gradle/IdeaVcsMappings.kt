package com.modularhub.gradle

import java.io.File

/**
 * 只登记壳工程自己为 Git 根，并清掉 modules 下嵌套映射。
 *
 * `modules/project-*` 在壳仓 gitignore 内时，Android Studio 若登记为嵌套 VCS 根，
 * 会报 “Invalid VCS root mappings”（即使本地 .git 可用）。
 */
object IdeaVcsMappings {

    fun write(rootDir: File) {
        val ideaDir = rootDir.resolve(".idea")
        ideaDir.mkdirs()
        val file = ideaDir.resolve("vcs.xml")
        val projectDirToken = "\$PROJECT_DIR$"

        file.writeText(
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project version="4">
            |  <component name="VcsDirectoryMappings">
            |    <mapping directory="$projectDirToken" vcs="Git" />
            |  </component>
            |</project>
            |
            """.trimMargin(),
        )
        println("[modularhub] wrote .idea/vcs.xml (shell root only)")
    }
}
