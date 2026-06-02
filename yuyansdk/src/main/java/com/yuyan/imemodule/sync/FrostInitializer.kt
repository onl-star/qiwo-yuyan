package com.yuyan.imemodule.sync

import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.utils.AssetUtils
import java.io.File

/**
 * rime-frost 初始化器。
 * 将内置 rime-frost 方案文件复制到 Rime 用户目录。
 * 与 qiwo-sync-core 的 FrostInitializer 对应。
 */
object FrostInitializer {

    /**
     * 初始化 rime-frost：将 assets/rime_frost 下的文件复制到 Rime 用户目录。
     * @return true 如果初始化成功
     */
    fun initialize(rimeUserDir: File): Boolean {
        return try {
            val context = Launcher.instance.context
            // 使用 AssetUtils 复制整个 rime_frost 目录
            AssetUtils.copyFileOrDir(
                context,
                "rime_frost",
                "",
                rimeUserDir.absolutePath,
                false // 不覆盖已存在的文件
            )
            // 写入 default.custom.yaml
            writeDefaultCustom(rimeUserDir)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 更新 rime-frost：覆盖复制（用于 WebDAV 同步后更新方案）。
     */
    fun update(rimeUserDir: File): Boolean {
        return try {
            val context = Launcher.instance.context
            AssetUtils.copyFileOrDir(
                context,
                "rime_frost",
                "",
                rimeUserDir.absolutePath,
                true // 覆盖
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun writeDefaultCustom(rimeUserDir: File) {
        val customYaml = """
patch:
  schema_list:
    - schema: rime_frost
    - schema: t9_pinyin
    - schema: pinyin
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
        val customFile = File(rimeUserDir, "default.custom.yaml")
        try {
            if (!customFile.exists()) {
                customFile.writeText(customYaml)
            }
        } catch (_: Exception) {
        }
    }
}
