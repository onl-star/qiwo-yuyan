package com.yuyan.imemodule.sync

/**
 * 文件过滤器：决定哪些文件应该同步。
 * 与 qiwo-sync-core 的 FileSelector 保持一致。
 *
 * 同步：
 *   *.custom.yaml, *.schema.yaml, *.dict.yaml, custom_phrase.txt,
 *   opencc/, lua/, symbols.yaml, *.json
 *
 * 不同步：
 *   build/, *.bin, *.table.bin, *.reverse.bin, *.userdb/, sync_manifest.json
 */
object FileSelector {

    /** 需要同步的文件扩展名 */
    private val syncExtensions = setOf(
        ".custom.yaml",
        ".schema.yaml",
        ".dict.yaml",
        ".txt",
        ".json"
    )

    /** 需要同步的顶级目录名 */
    private val syncTopDirs = setOf(
        "opencc",
        "lua"
    )

    /** 需要同步的精确文件名 */
    private val syncExactFiles = setOf(
        "symbols.yaml",
        "symbols_v.yaml",
        "key_bindings.yaml",
        "punctuation.yaml",
        "essay.txt",
        "default.custom.yaml",
        "weasel.custom.yaml",
        "squirrel.custom.yaml",
        "ibus_rime.custom.yaml",
        "trime.custom.yaml",
        "installation.yaml",
        "user.yaml"
    )

    /** 排除的目录/文件模式 */
    private val excludePatterns = listOf(
        "build/",
        "sync_manifest.json",
        ".userdb/",
        "userdb/",
        "trime.local.yaml"
    )

    /** 排除的扩展名 */
    private val excludeExtensions = setOf(
        ".bin",
        ".table.bin",
        ".reverse.bin"
    )

    /**
     * 判断文件是否应该同步。
     */
    fun shouldSync(relativePath: String): Boolean {
        // 排除特定目录/文件
        for (pattern in excludePatterns) {
            if (relativePath.startsWith(pattern) || relativePath == pattern) {
                return false
            }
        }

        // 排除二进制文件
        for (ext in excludeExtensions) {
            if (relativePath.endsWith(ext)) {
                return false
            }
        }

        // 精确文件名匹配
        val fileName = relativePath.substringAfterLast('/')
        if (fileName in syncExactFiles) {
            return true
        }

        // 扩展名匹配
        for (ext in syncExtensions) {
            if (relativePath.endsWith(ext)) {
                return true
            }
        }

        // 顶级目录匹配（目录下所有文件）
        for (dir in syncTopDirs) {
            if (relativePath.startsWith("$dir/")) {
                return true
            }
        }

        return false
    }
}
