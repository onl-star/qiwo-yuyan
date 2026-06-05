package com.yuyan.imemodule.sync

import java.io.File

/**
 * 确保 Rime 的 installation.yaml 包含正确的同步配置。
 * installation_id 使用设备标识（与 WebDAV 同步配置中的 deviceId 一致），
 * sync_dir 指向词库导出目录，供 Rime 原生 sync_user_data() 机制使用。
 *
 * 与 qiwo-sync-core 的 InstallationHelper 对齐。
 */
object InstallationHelper {

    private const val SYNC_DIR = "sync"

    /**
     * 确保 installation.yaml 存在且包含正确的 installation_id 和 sync_dir。
     * 如果文件已存在且配置正确，不做修改。
     */
    fun ensure(rimeUserDir: File, deviceId: String) {
        val file = File(rimeUserDir, "installation.yaml")
        file.parentFile?.mkdirs()
        val safeId = makeSafeId(deviceId)
        val syncDirPath = syncDirForYaml(rimeUserDir)

        if (file.exists()) {
            val lines = file.readLines(Charsets.UTF_8)
            var needsUpdate = false
            var hasSyncDir = false
            var hasInstallationId = false
            val updated = mutableListOf<String>()

            for (line in lines) {
                val stripped = line.trimStart()
                when {
                    stripped.startsWith("sync_dir:") -> {
                        hasSyncDir = true
                        val newLine = "sync_dir: \"$syncDirPath\""
                        updated += newLine
                        needsUpdate = needsUpdate || line != newLine
                    }
                    stripped.startsWith("installation_id:") -> {
                        hasInstallationId = true
                        val newLine = "installation_id: \"$safeId\""
                        updated += newLine
                        needsUpdate = needsUpdate || line != newLine
                    }
                    else -> updated += line
                }
            }

            if (!hasSyncDir) {
                updated += "sync_dir: \"$syncDirPath\""
                needsUpdate = true
            }
            if (!hasInstallationId) {
                updated += "installation_id: \"$safeId\""
                needsUpdate = true
            }

            if (needsUpdate) {
                file.writeText(updated.joinToString("\n").trimEnd() + "\n", Charsets.UTF_8)
            }
            return
        }

        // 新建文件
        val yaml = buildString {
            appendLine("distribution: \"Qiwo\"")
            appendLine("distribution_version: \"1.0\"")
            appendLine("installation_id: \"$safeId\"")
            appendLine("sync_dir: \"$syncDirPath\"")
        }
        file.writeText(yaml, Charsets.UTF_8)
    }

    /**
     * 确保 sync/{deviceId}/ 目录存在，Rime sync_user_data() 会将词库导出到这里。
     */
    fun ensureSyncExportDir(rimeUserDir: File, deviceId: String): File {
        val dir = File(rimeUserDir, "$SYNC_DIR/${makeSafeId(deviceId)}")
        dir.mkdirs()
        return dir
    }

    /**
     * 将设备 ID 转换为文件系统安全的目录名。
     * 替换空格和特殊字符为 `-`，转为小写。
     */
    fun makeSafeId(deviceId: String): String {
        val safeId = deviceId
            .replace(' ', '-')
            .replace(':', '-')
            .replace('\\', '-')
            .replace('/', '-')
            .lowercase()
        return safeId.ifBlank { "unknown" }
    }

    private fun syncDirForYaml(rimeUserDir: File): String {
        return File(rimeUserDir, SYNC_DIR).absolutePath.replace('\\', '/')
    }
}
