package com.yuyan

import android.app.Application
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.sync.SyncScheduler
import com.yuyan.imemodule.utils.CrashHandler

/**
 * Application 入口 — 齐我输入法。
 * @since 2019/6/18
 */
class BaseApplication : Application(){
    override fun onCreate() {
        super.onCreate()
        CrashHandler.instance.init(this)
        Launcher.instance.initData(baseContext)
        // 启动 WebDAV 定时同步（如果用户已配置）
        SyncScheduler.applySchedule()
    }
}
