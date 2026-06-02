package com.yuyan.imemodule.ui.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.sync.InstallationHelper
import com.yuyan.imemodule.sync.SyncEngine
import com.yuyan.imemodule.sync.SyncMode
import com.yuyan.imemodule.sync.SyncRequest
import com.yuyan.imemodule.sync.SyncScheduler
import com.yuyan.imemodule.ui.fragment.base.ManagedPreferenceFragment
import com.yuyan.imemodule.view.preference.ManagedPreference
import com.yuyan.inputmethod.core.Rime
import kotlinx.coroutines.launch
import java.io.File

/**
 * WebDAV 同步设置页面。
 */
class SyncSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().sync) {

    private val syncPrefs = AppPrefs.getInstance().sync
    private val engine = SyncEngine()
    private val sp: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(Launcher.instance.context)

    override fun onStart() {
        super.onStart()
        try {
            syncPrefs.autoSyncEnabled.registerOnChangeListener(autoSyncListener)
            syncPrefs.syncIntervalHours.registerOnChangeListener(intervalListener)
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            syncPrefs.autoSyncEnabled.unregisterOnChangeListener(autoSyncListener)
            syncPrefs.syncIntervalHours.unregisterOnChangeListener(intervalListener)
        } catch (_: Exception) {}
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val ctx = screen.context

        // WebDAV 地址
        val urlPref = EditTextPreference(ctx).apply {
            key = "sync_webdav_url"
            title = getString(com.yuyan.imemodule.R.string.webdav_url)
            summary = getString(com.yuyan.imemodule.R.string.webdav_url_summary)
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                syncPrefs.webdavUrl = newValue as String
                SyncScheduler.applySchedule()
                true
            }
        }
        // 加载当前值
        urlPref.text = syncPrefs.webdavUrl
        urlPref.summary = syncPrefs.webdavUrl.ifBlank {
            getString(com.yuyan.imemodule.R.string.webdav_url_summary)
        }
        screen.addPreference(urlPref)

        // 用户名
        val userPref = EditTextPreference(ctx).apply {
            key = "sync_webdav_username"
            title = getString(com.yuyan.imemodule.R.string.webdav_username)
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                syncPrefs.webdavUsername = newValue as String
                true
            }
        }
        userPref.text = syncPrefs.webdavUsername
        screen.addPreference(userPref)

        // 密码
        val passPref = EditTextPreference(ctx).apply {
            key = "sync_webdav_password"
            title = getString(com.yuyan.imemodule.R.string.webdav_password)
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                syncPrefs.webdavPassword = newValue as String
                true
            }
        }
        passPref.text = syncPrefs.webdavPassword
        screen.addPreference(passPref)

        // 同步目录
        val folderPref = EditTextPreference(ctx).apply {
            key = "sync_folder"
            title = getString(com.yuyan.imemodule.R.string.sync_folder)
            summary = getString(com.yuyan.imemodule.R.string.sync_folder_summary)
            setDefaultValue("qiwo-rime-sync")
            setOnPreferenceChangeListener { _, newValue ->
                syncPrefs.syncFolder = newValue as String
                SyncScheduler.applySchedule()
                true
            }
        }
        folderPref.text = syncPrefs.syncFolder
        folderPref.summary = syncPrefs.syncFolder
        screen.addPreference(folderPref)

        // 设备名称
        val devicePref = EditTextPreference(ctx).apply {
            key = "sync_device_name"
            title = getString(com.yuyan.imemodule.R.string.device_name)
            summary = getString(com.yuyan.imemodule.R.string.device_name_summary)
            setDefaultValue(syncPrefs.deviceName)
            setOnPreferenceChangeListener { _, newValue ->
                syncPrefs.deviceName = newValue as String
                true
            }
        }
        devicePref.text = syncPrefs.deviceName
        devicePref.summary = syncPrefs.deviceName
        screen.addPreference(devicePref)

        // 立即同步按钮
        val syncNowPref = Preference(ctx).apply {
            key = "sync_now"
            title = getString(com.yuyan.imemodule.R.string.sync_now)
            summary = getString(com.yuyan.imemodule.R.string.sync_now_summary)
            setOnPreferenceClickListener {
                performSync(SyncMode.Sync)
                true
            }
        }
        screen.addPreference(syncNowPref)

        // 推送到远端按钮
        val pushPref = Preference(ctx).apply {
            key = "sync_push"
            title = getString(com.yuyan.imemodule.R.string.sync_push)
            setOnPreferenceClickListener {
                performSync(SyncMode.Push)
                true
            }
        }
        screen.addPreference(pushPref)

        // 从远端拉取按钮
        val pullPref = Preference(ctx).apply {
            key = "sync_pull"
            title = getString(com.yuyan.imemodule.R.string.sync_pull)
            setOnPreferenceClickListener {
                performSync(SyncMode.Pull)
                true
            }
        }
        screen.addPreference(pullPref)
    }

    private fun performSync(mode: SyncMode) {
        val serverUrl: String = syncPrefs.webdavUrl.trimEnd('/')
        val folder: String = syncPrefs.syncFolder.trim('/')
        val username: String = syncPrefs.webdavUsername
        val password: String = syncPrefs.webdavPassword
        val device: String = syncPrefs.deviceName

        if (serverUrl.isBlank()) {
            Toast.makeText(
                requireContext(),
                getString(com.yuyan.imemodule.R.string.sync_no_config),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val rimeUserDir = File(CustomConstant.RIME_DICT_PATH)

        // 确保 installation.yaml 配置正确（本地管理，不同步）
        InstallationHelper.ensure(rimeUserDir, device)

        val fullUrl = "$serverUrl/$folder"

        Toast.makeText(
            requireContext(),
            getString(com.yuyan.imemodule.R.string.sync_in_progress),
            Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            val request = SyncRequest(
                deviceId = device,
                rimeUserDir = rimeUserDir,
                remoteUrl = fullUrl,
                username = username.ifBlank { null },
                password = password.ifBlank { null },
                mode = mode,
                dryRun = false
            )

            val summary = engine.execute(request)

            val message = when {
                summary.hasErrors -> summary.errors.joinToString("\n")
                summary.messages.isNotEmpty() -> summary.messages.joinToString("\n")
                else -> getString(com.yuyan.imemodule.R.string.sync_success)
            }

            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }

            // 同步完成后重新部署 Rime
            if (!summary.hasErrors && summary.totalFiles > 0) {
                Rime.destroy()
                Rime.getInstance(true)
            }
        }
    }

    companion object {
        private val autoSyncListener = ManagedPreference.OnChangeListener<Boolean> { _, _ ->
            SyncScheduler.applySchedule()
        }
        private val intervalListener = ManagedPreference.OnChangeListener<Int> { _, _ ->
            SyncScheduler.applySchedule()
        }
    }
}
