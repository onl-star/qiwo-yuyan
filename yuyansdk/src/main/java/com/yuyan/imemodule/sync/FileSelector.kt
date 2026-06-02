package com.yuyan.imemodule.sync

/**
 * 文件过滤器：决定哪些文件应该同步。
 * 与 qiwo-sync-core 的 FileSelector 保持一致。
 *
 * 同步：
 *   *.custom.yaml, *.schema.yaml, *.dict.yaml, custom_phrase.txt, symbols.yaml,
 *   opencc/, lua/, sync/
 *
 * 不同步：
 *   .git/, .qiwo-sync/, build/, *.bin, *.table.bin, *.reverse.bin, *.userdb
 */
object FileSelector {

    /** 需要同步的精确文件名（大小写不敏感）。 */
    private val includedExact = setOf(
        "custom_phrase.txt",
        "symbols.yaml"
    )

    /** 需要同步的文件扩展名。 */
    private val includedExtensions = setOf(
        ".custom.yaml",
        ".schema.yaml",
        ".dict.yaml"
    )

    /** 需要同步的目录前缀（包含结尾的 /）。 */
    private val includedDirectories = setOf(
        "opencc/",
        "lua/",
        "sync/"
    )

    /** 排除的目录前缀。 */
    private val excludedDirectories = setOf(
        ".git/",
        ".qiwo-sync/",
        "build/"
    )

    /** 排除的扩展名。 */
    private val excludedExtensions = setOf(
        ".bin"
    )

    /** 排除的后缀。 */
    private val excludedSuffixes = setOf(
        ".table.bin",
        ".reverse.bin",
        ".userdb"
    )

    /**
     * 判断文件是否应该同步。
     * 解析顺序与 C# FileSelector.ShouldSync 一致：
     * 1. 规范化路径
     * 2. 排除目录
     * 3. 排除 .userdb 路径段
     * 4. 排除后缀/扩展名
     * 5. 精确文件名匹配
     * 6. 扩展名匹配
     * 7. 目录匹配
     */
    fun shouldSync(relativePath: String): Boolean {
        val path = normalizePath(relativePath)
        val lower = path.lowercase()

        // 排除特定目录
        if (excludedDirectories.any { lower.startsWith(it) }) {
            return false
        }

        // 排除路径中包含 .userdb 的目录段
        if (lower.split('/').any { it.endsWith(".userdb") }) {
            return false
        }

        // 排除后缀和扩展名
        if (excludedSuffixes.any { lower.endsWith(it) } || excludedExtensions.any { lower.endsWith(it) }) {
            return false
        }

        // 精确文件名匹配
        val fileName = path.substringAfterLast('/')
        if (fileName.lowercase() in includedExact) {
            return true
        }

        // 扩展名匹配
        if (includedExtensions.any { lower.endsWith(it) }) {
            return true
        }

        // 目录匹配
        if (includedDirectories.any { lower.startsWith(it) }) {
            return true
        }

        return false
    }

    /** 规范化路径：反斜杠转正斜杠，移除开头的 /。 */
    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimStart('/')
    }
}
