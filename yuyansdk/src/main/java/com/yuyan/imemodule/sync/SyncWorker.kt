package com.yuyan.imemodule.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.inputmethod.core.Rime
import java.io.File

/**
 * WorkManager Worker：在后台执行 WebDAV 同步。
 * 由 [SyncScheduler] 按配置的间隔调度。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "QiwoSyncWorker"
    }

    override suspend fun doWork(): Result {
        val prefs = AppPrefs.getInstance().sync
        val serverUrl: String = prefs.webdavUrl.trimEnd('/')
        val folder: String = prefs.syncFolder.trim('/')
        val username: String = prefs.webdavUsername
        val password: String = prefs.webdavPassword
        val device: String = prefs.deviceName

        if (serverUrl.isBlank()) {
            Log.d(TAG, "WebDAV URL not configured, skipping sync")
            return Result.success()
        }

        val autoSyncEnabled: Boolean = prefs.autoSyncEnabled.getValue()
        if (!autoSyncEnabled) {
            Log.d(TAG, "Auto-sync disabled, skipping")
            return Result.success()
        }

        val fullUrl = "$serverUrl/$folder"
        Log.i(TAG, "Starting background sync to $fullUrl")

        val request = SyncRequest(
            deviceId = device,
            rimeUserDir = File(CustomConstant.RIME_DICT_PATH),
            remoteUrl = fullUrl,
            username = username.ifBlank { null },
            password = password.ifBlank { null },
            mode = SyncMode.Sync,
            dryRun = false
        )

        val engine = SyncEngine()
        val summary = engine.execute(request)

        if (summary.hasErrors) {
            Log.e(TAG, "Sync failed: ${summary.errors.first()}")
            return Result.retry()
        }

        Log.i(TAG, "Sync completed: uploaded=${summary.uploaded}, " +
            "downloaded=${summary.downloaded}, conflicts=${summary.conflictsBackedUp}")

        // 如果有文件变更，触发 Rime 重新部署
        if (summary.totalFiles > 0) {
            try {
                Rime.destroy()
                Rime.getInstance(true)
                Log.i(TAG, "Rime redeployed after sync")
            } catch (e: Exception) {
                Log.e(TAG, "Rime redeploy failed", e)
            }
        }

        return Result.success()
    }

    private fun getDeviceId(): String {
        return try {
            android.provider.Settings.Secure.getString(
                applicationContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown-android"
        } catch (e: Exception) {
            "unknown-android"
        }
    }
}
