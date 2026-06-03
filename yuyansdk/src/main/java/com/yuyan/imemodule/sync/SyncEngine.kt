package com.yuyan.imemodule.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 同步引擎：WebDAV push/pull/sync/sync-user-dict + rime-frost 初始化。
 * 接口与 qiwo-sync-core 的 SyncEngine 对齐。
 */
class SyncEngine {

    private val selector = FileSelector

    /**
     * 执行同步请求。
     */
    suspend fun execute(request: SyncRequest): SyncSummary = withContext(Dispatchers.IO) {
        when (request.mode) {
            SyncMode.InitFrost -> initFrost(request)
            SyncMode.Push -> push(request)
            SyncMode.Pull -> pull(request)
            SyncMode.Sync -> sync(request)
            SyncMode.SyncUserDict -> syncUserDict(request)
        }
    }

    // ---- InitFrost ----

    private fun initFrost(request: SyncRequest): SyncSummary {
        val messages = mutableListOf<String>()
        val frostSchema = File(request.rimeUserDir, "rime_frost.schema.yaml")
        if (frostSchema.exists()) {
            messages.add("rime-frost already initialized.")
            return SyncSummary(
                mode = SyncMode.InitFrost,
                deviceId = request.deviceId,
                messages = messages
            )
        }

        // Frost 初始化委托给 FrostInitializer
        val result = FrostInitializer.initialize(request.rimeUserDir)
        if (result) {
            messages.add("rime-frost initialized successfully.")
        } else {
            messages.add("rime-frost initialization failed.")
        }
        return SyncSummary(
            mode = SyncMode.InitFrost,
            deviceId = request.deviceId,
            messages = messages
        )
    }

    // ---- Push ----

    private suspend fun push(request: SyncRequest): SyncSummary {
        if (request.remoteUrl.isNullOrEmpty()) {
            return SyncSummary(
                mode = SyncMode.Push,
                deviceId = request.deviceId,
                errors = listOf("RemoteUrl is required for WebDAV sync.")
            )
        }

        val webDav = WebDavClient(request.remoteUrl, request.username, request.password)
        val messages = mutableListOf<String>()
        if (!request.dryRun) {
            webDav.ensureRootAsync()
        }

        val localFiles = scanLocalFiles(request.rimeUserDir)
        var uploaded = 0
        var failed = 0
        for ((path, _) in localFiles.entries.sortedBy { it.key.lowercase() }) {
            if (!request.dryRun) {
                val file = File(request.rimeUserDir, path)
                val ok = webDav.putFileAsync(path, file)
                if (ok) uploaded++ else {
                    failed++
                    messages.add("Failed to upload: $path")
                }
            } else {
                uploaded++
            }
        }

        // 写入清单，与 Rust qiwo-sync-core 的行为保持一致。
        val manifest = createManifest(request, localFiles)
        if (!request.dryRun) {
            writeLocalManifest(request.rimeUserDir, manifest)
            webDav.putBytesAsync(
                SyncConstants.REMOTE_MANIFEST_FILE_NAME,
                ManifestSerializer.toJsonBytes(manifest)
            )
        }

        messages.add("Pushed $uploaded file(s), $failed failed.")
        return SyncSummary(
            mode = SyncMode.Push,
            deviceId = request.deviceId,
            uploaded = uploaded,
            messages = messages,
            errors = if (failed > 0) listOf("$failed file(s) failed to upload") else emptyList()
        )
    }

    // ---- Pull ----

    private suspend fun pull(request: SyncRequest): SyncSummary {
        if (request.remoteUrl.isNullOrEmpty()) {
            return SyncSummary(
                mode = SyncMode.Pull,
                deviceId = request.deviceId,
                errors = listOf("RemoteUrl is required for WebDAV sync.")
            )
        }

        val webDav = WebDavClient(request.remoteUrl, request.username, request.password)
        val remoteManifest = readRemoteManifest(webDav)
        var downloaded = 0
        var skipped = 0
        var failed = 0
        val messages = mutableListOf<String>()

        for ((path, _) in remoteManifest.files.entries.sortedBy { it.key.lowercase() }) {
            if (!selector.shouldSync(path)) {
                skipped++
                continue
            }

            if (!request.dryRun) {
                val localFile = File(request.rimeUserDir, path)
                val ok = webDav.getFileAsync(path, localFile)
                if (ok) downloaded++ else {
                    failed++
                    messages.add("Failed to download: $path")
                }
            } else {
                downloaded++
            }
        }

        // 更新本地清单
        val localFiles = if (request.dryRun) remoteManifest.files else scanLocalFiles(request.rimeUserDir)
        val localManifest = createManifest(request, localFiles)
        if (!request.dryRun) {
            writeLocalManifest(request.rimeUserDir, localManifest)
        }

        messages.add("Pulled $downloaded file(s).")
        return SyncSummary(
            mode = SyncMode.Pull,
            deviceId = request.deviceId,
            downloaded = downloaded,
            skipped = skipped,
            messages = messages
        )
    }

    // ---- Sync (双向) ----

    private suspend fun sync(request: SyncRequest): SyncSummary {
        if (request.remoteUrl.isNullOrEmpty()) {
            return SyncSummary(
                mode = SyncMode.Sync,
                deviceId = request.deviceId,
                errors = listOf("RemoteUrl is required for WebDAV sync.")
            )
        }

        val webDav = WebDavClient(request.remoteUrl, request.username, request.password)
        if (!request.dryRun) {
            webDav.ensureRootAsync()
        }

        val previousManifest = readLocalManifest(request.rimeUserDir)
        val remoteManifest = readRemoteManifest(webDav)
        val localFiles = scanLocalFiles(request.rimeUserDir)

        return doThreeWayMerge(request, webDav, localFiles, remoteManifest, previousManifest)
    }

    // ---- SyncUserDict (仅同步用户词库) ----
    // 先由调用方触发 Rime 的 sync_user_data() 导出词库，再调用本方法。

    private suspend fun syncUserDict(request: SyncRequest): SyncSummary {
        if (request.remoteUrl.isNullOrEmpty()) {
            return SyncSummary(
                mode = SyncMode.SyncUserDict,
                deviceId = request.deviceId,
                errors = listOf("RemoteUrl is required for WebDAV sync.")
            )
        }

        val webDav = WebDavClient(request.remoteUrl, request.username, request.password)
        if (!request.dryRun) {
            webDav.ensureRootAsync()
        }

        val previousManifest = readLocalManifest(request.rimeUserDir)
        val remoteManifest = readRemoteManifest(webDav)
        val localFiles = scanLocalFiles(request.rimeUserDir)

        // 过滤：仅保留 sync/ 目录下的文件（用户词库文本导出）
        val localDictFiles = localFiles.filterKeys { it.startsWith("sync/") }
        val remoteDictFiles = remoteManifest.files.filterKeys { it.startsWith("sync/") }

        return doThreeWayMerge(
            request, webDav,
            localDictFiles,
            SyncManifest(deviceId = remoteManifest.deviceId, files = remoteDictFiles),
            previousManifest,
            label = "Dict sync"
        )
    }

    /**
     * 三路合并核心逻辑，用于 sync 和 syncUserDict 共用。
     */
    private suspend fun doThreeWayMerge(
        request: SyncRequest,
        webDav: WebDavClient,
        localFiles: Map<String, SyncFileEntry>,
        remoteManifest: SyncManifest,
        previousManifest: SyncManifest,
        label: String = "Sync"
    ): SyncSummary {
        var uploaded = 0
        var downloaded = 0
        var skipped = 0
        var conflicts = 0
        val messages = mutableListOf<String>()

        val allPaths = (localFiles.keys + remoteManifest.files.keys)
            .distinct()
            .sortedBy { it.lowercase() }

        for (path in allPaths) {
            if (!selector.shouldSync(path)) {
                skipped++
                continue
            }

            val localEntry = localFiles[path]
            val remoteEntry = remoteManifest.files[path]
            val previousEntry = previousManifest.files[path]

            // 文件相同 → 跳过
            if (localEntry != null && remoteEntry != null && localEntry.sha256 == remoteEntry.sha256) {
                skipped++
                continue
            }

            val localChanged = localEntry != null &&
                (previousEntry == null || localEntry.sha256 != previousEntry.sha256)
            val remoteChanged = remoteEntry != null &&
                (previousEntry == null || remoteEntry.sha256 != previousEntry.sha256)

            when {
                // 仅本地有
                localEntry != null && remoteEntry == null -> {
                    if (!request.dryRun) {
                        webDav.putFileAsync(path, File(request.rimeUserDir, path))
                    }
                    uploaded++
                }
                // 仅远端有
                localEntry == null && remoteEntry != null -> {
                    if (!request.dryRun) {
                        webDav.getFileAsync(path, File(request.rimeUserDir, path))
                    }
                    downloaded++
                }
                // 仅本地变更
                localEntry != null && remoteEntry != null && localChanged && !remoteChanged -> {
                    if (!request.dryRun) {
                        webDav.putFileAsync(path, File(request.rimeUserDir, path))
                    }
                    uploaded++
                }
                // 仅远端变更
                localEntry != null && remoteEntry != null && !localChanged && remoteChanged -> {
                    if (!request.dryRun) {
                        webDav.getFileAsync(path, File(request.rimeUserDir, path))
                    }
                    downloaded++
                }
                // 双方都变更 → 冲突：备份本地，保留远端（远端优先）
                localEntry != null && remoteEntry != null && localChanged && remoteChanged -> {
                    if (!request.dryRun) {
                        backupLocalFile(request.rimeUserDir, path)
                        webDav.getFileAsync(path, File(request.rimeUserDir, path))
                    }
                    downloaded++
                    conflicts++
                    messages.add("Conflict backed up, remote kept: $path")
                }
                // 双方都未变更 → 以时间戳为准（较新的获胜）
                localEntry != null && remoteEntry != null -> {
                    if (localEntry.lastWriteUtc >= remoteEntry.lastWriteUtc) {
                        if (!request.dryRun) {
                            webDav.putFileAsync(path, File(request.rimeUserDir, path))
                        }
                        uploaded++
                    } else {
                        if (!request.dryRun) {
                            webDav.getFileAsync(path, File(request.rimeUserDir, path))
                        }
                        downloaded++
                    }
                }
                else -> skipped++
            }
        }

        // 更新清单
        val finalFiles = if (request.dryRun) localFiles else scanLocalFiles(request.rimeUserDir)
        val finalManifest = createManifest(request, finalFiles)
        if (!request.dryRun) {
            writeLocalManifest(request.rimeUserDir, finalManifest)
            webDav.putBytesAsync(
                SyncConstants.REMOTE_MANIFEST_FILE_NAME,
                ManifestSerializer.toJsonBytes(finalManifest)
            )
        }

        messages.add("$label — uploaded $uploaded, downloaded $downloaded, conflicts $conflicts.")
        return SyncSummary(
            mode = request.mode,
            deviceId = request.deviceId,
            uploaded = uploaded,
            downloaded = downloaded,
            conflictsBackedUp = conflicts,
            skipped = skipped,
            messages = messages
        )
    }

    // ---- 辅助方法 ----

    /** 扫描本地文件，仅通过 FileSelector 过滤。 */
    private fun scanLocalFiles(rimeDir: File): Map<String, SyncFileEntry> {
        val result = mutableMapOf<String, SyncFileEntry>()
        if (!rimeDir.exists()) return result
        scanDir(rimeDir, rimeDir, result)
        return result
    }

    private fun scanDir(
        baseDir: File,
        currentDir: File,
        result: MutableMap<String, SyncFileEntry>
    ) {
        for (file in currentDir.listFiles() ?: emptyArray()) {
            val relativePath = file.relativeTo(baseDir).path.replace('\\', '/')

            if (file.isDirectory) {
                scanDir(baseDir, file, result)
            } else {
                if (selector.shouldSync(relativePath)) {
                    result[relativePath] = SyncFileEntry(
                        relativePath = relativePath,
                        sha256 = sha256(file),
                        sizeBytes = file.length(),
                        lastWriteUtc = Instant.ofEpochMilli(file.lastModified())
                    )
                }
            }
        }
    }

    private fun createManifest(
        request: SyncRequest,
        files: Map<String, SyncFileEntry>
    ): SyncManifest {
        return SyncManifest(
            deviceId = request.deviceId,
            frontend = request.frontend,
            updatedAtUtc = Instant.now(),
            files = files
        )
    }

    /** 写入本地清单到 .qiwo-sync/manifest.json。 */
    private fun writeLocalManifest(rimeDir: File, manifest: SyncManifest) {
        val stateDir = File(rimeDir, SyncConstants.STATE_DIR)
        stateDir.mkdirs()
        val file = File(stateDir, SyncConstants.LOCAL_MANIFEST_FILE_NAME)
        try {
            file.writeBytes(ManifestSerializer.toJsonBytes(manifest))
        } catch (_: Exception) {
        }
    }

    /** 读取本地清单：.qiwo-sync/manifest.json。 */
    private fun readLocalManifest(rimeDir: File): SyncManifest {
        val file = File(rimeDir, "${SyncConstants.STATE_DIR}/${SyncConstants.LOCAL_MANIFEST_FILE_NAME}")
        return if (file.exists()) {
            try {
                ManifestSerializer.fromJsonBytes(file.readBytes())
            } catch (_: Exception) {
                SyncManifest.Empty
            }
        } else {
            SyncManifest.Empty
        }
    }

    /** 读取远端清单：.qiwo-sync-manifest.json。 */
    private suspend fun readRemoteManifest(webDav: WebDavClient): SyncManifest {
        val bytes = webDav.getBytesAsync(SyncConstants.REMOTE_MANIFEST_FILE_NAME)
        return if (bytes != null) {
            try {
                ManifestSerializer.fromJsonBytes(bytes)
            } catch (_: Exception) {
                SyncManifest.Empty
            }
        } else {
            SyncManifest.Empty
        }
    }

    /**
     * 备份本地文件到 .qiwo-sync/backups/{yyyyMMddHHmmss}/{relativePath}。
     * 与 Rust qiwo-sync-core 的备份目录结构保持一致。
     */
    private fun backupLocalFile(rimeDir: File, relativePath: String) {
        val src = File(rimeDir, relativePath)
        if (!src.exists()) return

        val timestamp = java.time.LocalDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val backupDir = File(rimeDir, "${SyncConstants.STATE_DIR}/${SyncConstants.BACKUP_DIR}/$timestamp")
        val dst = File(backupDir, relativePath)
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** 同步常量，与 qiwo-sync-core 的 SyncConstants 对齐。 */
object SyncConstants {
    const val STATE_DIR = ".qiwo-sync"
    const val BACKUP_DIR = "backups"
    const val LOCAL_MANIFEST_FILE_NAME = "manifest.json"
    const val REMOTE_MANIFEST_FILE_NAME = ".qiwo-sync-manifest.json"
}
