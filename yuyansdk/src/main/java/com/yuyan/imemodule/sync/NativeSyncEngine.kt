package com.yuyan.imemodule.sync

import com.qiwo.sync.QiwoSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dispatches sync work to the Rust qiwo-sync-core JNI bridge.
 */
object NativeSyncEngine {
    suspend fun execute(request: SyncRequest): SyncSummary = withContext(Dispatchers.IO) {
        try {
            val result = QiwoSync.execute(
                rimeUserDir = request.rimeUserDir.absolutePath,
                remoteUrl = request.remoteUrl.orEmpty(),
                username = request.username,
                password = request.password,
                deviceId = request.deviceId,
                mode = request.mode.toWireMode(),
                dryRun = request.dryRun
            )

            if (!result.success) {
                return@withContext SyncSummary(
                    mode = request.mode,
                    deviceId = request.deviceId,
                    errors = listOf(result.message.ifBlank { "Native sync failed." })
                )
            }

            SyncSummary(
                mode = request.mode,
                deviceId = request.deviceId,
                uploaded = result.uploaded,
                downloaded = result.downloaded,
                conflictsBackedUp = result.conflicts,
                messages = listOf(result.message).filter { it.isNotBlank() }
            )
        } catch (e: UnsatisfiedLinkError) {
            SyncSummary(
                mode = request.mode,
                deviceId = request.deviceId,
                errors = listOf("Native sync unavailable: ${e.message ?: "qiwo-sync-core library failed to load."}")
            )
        } catch (e: Throwable) {
            SyncSummary(
                mode = request.mode,
                deviceId = request.deviceId,
                errors = listOf(e.message ?: e.javaClass.simpleName)
            )
        }
    }

    private fun SyncMode.toWireMode(): String = when (this) {
        SyncMode.Push -> "push"
        SyncMode.Pull -> "pull"
        SyncMode.Sync -> "sync"
        SyncMode.InitFrost -> "init-frost"
        SyncMode.SyncUserDict -> "sync-user-dict"
    }
}
