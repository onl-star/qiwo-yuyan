package com.yuyan.imemodule.sync

import java.io.File

/**
 * 同步模式，与 qiwo-sync-core 保持一致。
 */
enum class SyncMode {
    /** 推送到远端 */
    Push,
    /** 从远端拉取 */
    Pull,
    /** 双向同步（基于 manifest 冲突检测） */
    Sync,
    /** 初始化/更新 rime-frost 方案 */
    InitFrost,
    /** 仅同步用户词库（sync/ 目录） */
    SyncUserDict
}

/**
 * 同步请求数据类。
 * 接口与 qiwo-sync-core 的 SyncRequest 对齐。
 */
data class SyncRequest(
    /** 设备标识（用于多设备冲突检测） */
    val deviceId: String,
    /** Rime 用户目录 */
    val rimeUserDir: File,
    /** WebDAV 远端地址 */
    val remoteUrl: String?,
    /** WebDAV 用户名 */
    val username: String?,
    /** WebDAV 密码 */
    val password: String?,
    /** 同步模式 */
    val mode: SyncMode,
    /** 前端标识（用于 manifest） */
    val frontend: String = "qiwo-yuyan",
    /** rime-frost 资源目录（assets 中的路径） */
    val frostDir: File? = null,
    /** 仅试运行，不实际写入 */
    val dryRun: Boolean = false
)

/**
 * 同步结果摘要。
 */
data class SyncSummary(
    val mode: SyncMode,
    val deviceId: String,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val conflictsBackedUp: Int = 0,
    val skipped: Int = 0,
    val errors: List<String> = emptyList(),
    val messages: List<String> = emptyList()
) {
    val totalFiles: Int get() = uploaded + downloaded
    val hasErrors: Boolean get() = errors.isNotEmpty()
}
