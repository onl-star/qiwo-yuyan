package com.yuyan.imemodule.application

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.yuyan.imemodule.data.emojicon.YuyanEmojiCompat
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.data.theme.ThemeManager.prefs
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.service.ClipboardHelper
import com.yuyan.imemodule.utils.AssetUtils.copyFileOrDir
import com.yuyan.imemodule.utils.thread.ThreadPoolUtils
import com.yuyan.inputmethod.core.Kernel
import java.io.File

class Launcher {
    private val tag = "QiwoLauncher"
    lateinit var context: Context
        private set

    fun initData(context: Context) {
        this.context = context
        currentInit()
        onInitDataChildThread()
    }

    private fun currentInit() {
        AppPrefs.init(PreferenceManager.getDefaultSharedPreferences(context))
        ThemeManager.init(context.resources.configuration)
        DataBaseKT.instance.sideSymbolDao().getAllSideSymbolPinyin()  //操作一次查询，提前创建数据库，避免使用时才创建数据库
        ClipboardHelper.init()
    }

    /**
     * 可以在子线程初始化的操作
     */
    private fun onInitDataChildThread() {
        ThreadPoolUtils.executeSingleton {
            // 复制词库文件
            val rimeDataVersion = AppPrefs.getInstance().internal.rimeDictDataVersion.getValue()
            val requiresFullRimeCheck = rimeDataVersion != CustomConstant.CURRENT_RIME_DICT_DATA_VERSION
            if (requiresFullRimeCheck) {
                refreshPackagedRimeResources("version $rimeDataVersion -> ${CustomConstant.CURRENT_RIME_DICT_DATA_VERSION}")
            }
            val rimeReady = Kernel.resetIme(requiresFullRimeCheck)  // 解决词库复制慢，导致先调用初始化问题
            if (requiresFullRimeCheck && rimeReady) {
                AppPrefs.getInstance().internal.rimeDictDataVersion.setValue(CustomConstant.CURRENT_RIME_DICT_DATA_VERSION)
            }
            YuyanEmojiCompat.init(context)
            //初始化键盘主题
            val isFollowSystemDayNight = prefs.followSystemDayNightTheme.getValue()
            if (isFollowSystemDayNight) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    fun recoverRimeResourcesAfterStartupFailure(): Boolean {
        return try {
            refreshPackagedRimeResources("startup recovery")
            true
        } catch (error: Exception) {
            Log.e(tag, "Failed to recover packaged Rime resources", error)
            false
        }
    }

    private fun refreshPackagedRimeResources(reason: String) {
        Log.i(tag, "Refreshing packaged Rime resources: $reason")
        cleanGeneratedRimeResidues()
        // rime词库（含 build/ 预编译 schema）
        copyFileOrDir(context, "rime", "", CustomConstant.RIME_DICT_PATH, true)
        copyFileOrDir(context, "hw", "", CustomConstant.HW_DICT_PATH, true)
        // rime-frost 白霜拼音方案。升级 full Rime 后需要覆盖旧版方案文件，用户词库不在这里。
        copyFileOrDir(context, "rime_frost", "", CustomConstant.RIME_DICT_PATH, true)
        // 写入 default.custom.yaml
        writeDefaultCustom()
    }

    private fun cleanGeneratedRimeResidues() {
        deleteIfExists(File(CustomConstant.RIME_DICT_PATH, "build"))
        deleteIfExists(File(CustomConstant.RIME_DICT_PATH, "default.yaml"))
    }

    private fun deleteIfExists(file: File) {
        if (!file.exists()) return
        val deleted = if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
        if (!deleted) {
            Log.w(tag, "Failed to delete stale Rime file: ${file.absolutePath}")
        }
    }

    /**
     * 写入 default.custom.yaml，包含所有可用输入方案。
     * 默认优先使用完整白霜全拼。
     */
    private fun writeDefaultCustom() {
        val customYaml = """
patch:
  schema_list:
    - schema: rime_frost
    - schema: pinyin
    - schema: t9_pinyin
    - schema: double_pinyin_natural
    - schema: double_pinyin_mspy
    - schema: double_pinyin_sogou
    - schema: double_pinyin_flypy
    - schema: double_pinyin_abc
    - schema: double_pinyin_ziguang
    - schema: double_pinyin_ls17
    - schema: stroke
    - schema: english
  "menu/page_size": 8
""".trimIndent()
        val customFile = File(CustomConstant.RIME_DICT_PATH, "default.custom.yaml")
        try {
            customFile.writeText(customYaml)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        val instance = Launcher()
    }
}
