package com.yuyan.imemodule.application

import android.annotation.SuppressLint
import android.content.Context
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

class Launcher {
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
            val dataDictVersion = AppPrefs.getInstance().internal.dataDictVersion.getValue()
            if (dataDictVersion < CustomConstant.CURRENT_RIME_DICT_DATA_VERSIOM) {
                // 清理旧版残留：删除 rime_frost 遗留的 root default.yaml
                val oldDefault = java.io.File(CustomConstant.RIME_DICT_PATH, "default.yaml")
                if (oldDefault.exists() && !oldDefault.delete()) {
                    android.util.Log.w("QiwoLauncher", "Failed to delete stale root default.yaml")
                }
                //rime词库（含 build/ 预编译 schema）
                copyFileOrDir(context, "rime", "", CustomConstant.RIME_DICT_PATH, true)
                copyFileOrDir(context, "hw", "", CustomConstant.HW_DICT_PATH, true)
                // rime-frost 白霜拼音方案（不覆盖已有文件，保护原预编译方案）
                copyFileOrDir(context, "rime_frost", "", CustomConstant.RIME_DICT_PATH, false)
                // 写入 default.custom.yaml
                writeDefaultCustom()
                migrateUnsupportedFrostSchemasToLegacy()
                AppPrefs.getInstance().internal.dataDictVersion.setValue(CustomConstant.CURRENT_RIME_DICT_DATA_VERSIOM)
            }
            Kernel.resetIme()  // 解决词库复制慢，导致先调用初始化问题
            YuyanEmojiCompat.init(context)
            //初始化键盘主题
            val isFollowSystemDayNight = prefs.followSystemDayNightTheme.getValue()
            if (isFollowSystemDayNight) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    /**
     * 写入 default.custom.yaml，包含所有可用输入方案。
     * 默认使用稳定的旧方案。
     * 白霜方案目前不能进入 legacy native runtime：完整白霜依赖 librime-lua，
     * CI 跨版本预编译的白霜 table 也会让旧 marisa reader 崩溃。
     */
    private fun writeDefaultCustom() {
        val customYaml = """
patch:
  schema_list:
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
        val customFile = java.io.File(CustomConstant.RIME_DICT_PATH, "default.custom.yaml")
        try {
            customFile.writeText(customYaml)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun migrateUnsupportedFrostSchemasToLegacy() {
        val internalPrefs = AppPrefs.getInstance().internal
        val currentSchema = internalPrefs.pinyinModeRime.getValue()
        val stableSchema = CustomConstant.stableSchemaForLegacyRime(currentSchema)
        if (stableSchema != currentSchema) {
            internalPrefs.pinyinModeRime.setValue(stableSchema)
            android.util.Log.i(
                "QiwoLauncher",
                "Migrated unsupported Rime schema from $currentSchema to $stableSchema"
            )
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        val instance = Launcher()
    }
}
