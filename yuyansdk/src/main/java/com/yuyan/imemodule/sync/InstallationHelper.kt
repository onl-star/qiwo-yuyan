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

        if (file.exists()) {
            val existing = file.readText(Charsets.UTF_8)
            var needsUpdate = false
            var updated = existing.trimEnd()

            // 确保 sync_dir 存在
            if (!updated.contains("sync_dir:")) {
                updated += "\nsync_dir: \"$SYNC_DIR\"\n"
                needsUpdate = true
            }

            // 确保 installation_id 存在
            if (!updated.contains("installation_id:")) {
                updated += "\ninstallation_id: \"${makeSafeId(deviceId)}\"\n"
                needsUpdate = true
            }

            if (needsUpdate) {
                file.writeText(updated, Charsets.UTF_8)
            }
            return
        }

        // 新建文件
        val yaml = buildString {
            appendLine("distribution: \"Qiwo\"")
            appendLine("distribution_version: \"1.0\"")
            appendLine("installation_id: \"${makeSafeId(deviceId)}\"")
            appendLine("sync_dir: \"$SYNC_DIR\"")
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
        return deviceId
            .replace(' ', '-')
            .replace(':', '-')
            .replace('\\', '-')
            .replace('/', '-')
            .lowercase()
    }
}
