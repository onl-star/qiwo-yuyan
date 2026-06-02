package com.yuyan.imemodule.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.Instant

/**
 * 同步引擎：WebDAV push/pull/sync + rime-frost 初始化。
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
        if (!request.dryRun) {
            webDav.ensureRootAsync()
        }

        val localFiles = scanLocalFiles(request.rimeUserDir)
        val messages = mutableListOf<String>()
        var uploaded = 0

        for ((path, entry) in localFiles.entries.sortedBy { it.key }) {
            if (!request.dryRun) {
                val file = File(request.rimeUserDir, path)
                webDav.putFileAsync(path, file)
            }
            uploaded++
        }

        // 写入清单
        val manifest = createManifest(request, localFiles)
        if (!request.dryRun) {
            writeManifest(request.rimeUserDir, manifest)
            webDav.putBytesAsync(
                SyncConstants.REMOTE_MANIFEST_FILE_NAME,
                ManifestSerializer.toJsonBytes(manifest)
            )
        }

        messages.add("Pushed $uploaded file(s).")
        return SyncSummary(
            mode = SyncMode.Push,
            deviceId = request.deviceId,
            uploaded = uploaded,
            messages = messages
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
        val messages = mutableListOf<String>()

        for ((path, entry) in remoteManifest.files.entries.sortedBy { it.key }) {
            if (!selector.shouldSync(path)) {
                skipped++
                continue
            }

            if (!request.dryRun) {
                val localFile = File(request.rimeUserDir, path)
                webDav.getFileAsync(path, localFile)
            }
            downloaded++
        }

        // 更新本地清单
        val localFiles = if (request.dryRun) remoteManifest.files else scanLocalFiles(request.rimeUserDir)
        val localManifest = createManifest(request, localFiles)
        if (!request.dryRun) {
            writeManifest(request.rimeUserDir, localManifest)
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
        var uploaded = 0
        var downloaded = 0
        var skipped = 0
        var conflicts = 0
        val messages = mutableListOf<String>()

        val allPaths = (localFiles.keys + remoteManifest.files.keys)
            .distinct()
            .sorted()

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
                // 双方都变更 → 冲突：备份本地，保留远端
                localEntry != null && remoteEntry != null && localChanged && remoteChanged -> {
                    if (!request.dryRun) {
                        backupLocalFile(request.rimeUserDir, path)
                        webDav.getFileAsync(path, File(request.rimeUserDir, path))
                    }
                    downloaded++
                    conflicts++
                    messages.add("Conflict backed up, remote kept: $path")
                }
                // 双方都未变更 → 以时间戳为准
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
            writeManifest(request.rimeUserDir, finalManifest)
            webDav.putBytesAsync(
                SyncConstants.REMOTE_MANIFEST_FILE_NAME,
                ManifestSerializer.toJsonBytes(finalManifest)
            )
        }

        messages.add("Uploaded $uploaded, downloaded $downloaded, conflicts $conflicts.")
        return SyncSummary(
            mode = SyncMode.Sync,
            deviceId = request.deviceId,
            uploaded = uploaded,
            downloaded = downloaded,
            conflictsBackedUp = conflicts,
            skipped = skipped,
            messages = messages
        )
    }

    // ---- 辅助方法 ----

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
            if (file.name == SyncConstants.LOCAL_MANIFEST_FILE_NAME) continue
            if (file.name.startsWith(".")) continue

            val relativePath = file.relativeTo(baseDir).path.replace('\\', '/')

            if (file.isDirectory) {
                // 检查排除模式，跳过不需要同步的目录
                val dirPath = "$relativePath/"
                if (dirPath.startsWith("build/") || dirPath.startsWith(".userdb/")) continue
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
            frontend = "YuyanIme",
            updatedAtUtc = Instant.now(),
            files = files
        )
    }

    private fun writeManifest(rimeDir: File, manifest: SyncManifest) {
        val file = File(rimeDir, SyncConstants.LOCAL_MANIFEST_FILE_NAME)
        file.parentFile?.mkdirs()
        try {
            file.writeBytes(ManifestSerializer.toJsonBytes(manifest))
        } catch (_: Exception) {
        }
    }

    private fun readLocalManifest(rimeDir: File): SyncManifest {
        val file = File(rimeDir, SyncConstants.LOCAL_MANIFEST_FILE_NAME)
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

    private fun backupLocalFile(rimeDir: File, relativePath: String) {
        val src = File(rimeDir, relativePath)
        if (!src.exists()) return
        val backupDir = File(rimeDir, "backup")
        backupDir.mkdirs()
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupName = "${src.name}.$timestamp.bak"
        val dst: File = if (relativePath.contains('/')) {
            val sub = File(backupDir, relativePath.substringBeforeLast('/'))
            sub.mkdirs()
            File(sub, backupName)
        } else {
            File(backupDir, backupName)
        }
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

/** 同步常量。 */
object SyncConstants {
    const val LOCAL_MANIFEST_FILE_NAME = "sync_manifest.json"
    const val REMOTE_MANIFEST_FILE_NAME = "sync_manifest.json"
}
