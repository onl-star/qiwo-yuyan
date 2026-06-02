package com.yuyan.imemodule.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.prefs.AppPrefs
import java.util.concurrent.TimeUnit

/**
 * 同步调度器：根据用户配置管理 WorkManager 定时同步任务。
 */
object SyncScheduler {

    private const val WORK_NAME = "qiwo_webdav_sync"
    private const val TAG = "QiwoSyncScheduler"

    /**
     * 根据当前配置启动或取消定时同步。
     * 在应用启动和同步设置变更时调用。
     */
    fun applySchedule() {
        val prefs = AppPrefs.getInstance().sync
        val enabled = prefs.autoSyncEnabled.getValue()
        val url = prefs.webdavUrl

        if (enabled && url.isNotBlank()) {
            schedule(prefs.syncIntervalHours.getValue())
        } else {
            cancel()
        }
    }

    /**
     * 启动定时同步。
     * @param intervalHours 同步间隔（小时），最小 1 小时（WorkManager 限制）。
     */
    fun schedule(intervalHours: Int) {
        val context = Launcher.instance.context
        val workManager = WorkManager.getInstance(context)

        // WorkManager 最小间隔为 15 分钟，我们使用 1 小时下限以保证电池友好
        val interval = intervalHours.coerceAtLeast(1).toLong()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            interval, TimeUnit.HOURS,
            15, TimeUnit.MINUTES  // flex interval
        )
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,  // 更新现有策略
            workRequest
        )

        Log.i(TAG, "Scheduled WebDAV sync every $interval hours")
    }

    /**
     * 取消定时同步。
     */
    fun cancel() {
        val context = Launcher.instance.context
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "Cancelled WebDAV sync schedule")
    }

    /**
     * 执行一次即时同步（前台触发）。
     */
    fun enqueueOneShot() {
        val context = Launcher.instance.context
        val workManager = WorkManager.getInstance(context)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag("${WORK_NAME}_oneshot")
            .build()

        workManager.enqueue(workRequest)
        Log.i(TAG, "Enqueued one-shot sync")
    }
}
