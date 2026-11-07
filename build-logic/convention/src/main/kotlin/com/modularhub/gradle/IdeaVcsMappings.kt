package com.modularhub.gradle

import java.io.File

/**
 * 只登记壳工程 Git 根。
 * 组件仍在 modules/ 下且被壳仓 ignore，不登记嵌套根（否则 Android Studio 报 Invalid）。
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
    }
}
