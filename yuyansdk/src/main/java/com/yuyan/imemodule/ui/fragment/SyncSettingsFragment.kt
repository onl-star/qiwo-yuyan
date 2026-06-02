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
        val url: String = syncPrefs.webdavUrl
        val username: String = syncPrefs.webdavUsername
        val password: String = syncPrefs.webdavPassword

        if (url.isBlank()) {
            Toast.makeText(
                requireContext(),
                getString(com.yuyan.imemodule.R.string.sync_no_config),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(
            requireContext(),
            getString(com.yuyan.imemodule.R.string.sync_in_progress),
            Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            val deviceId: String = android.provider.Settings.Secure.getString(
                requireContext().contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown-android"
            val request = SyncRequest(
                deviceId = deviceId,
                rimeUserDir = File(CustomConstant.RIME_DICT_PATH),
                remoteUrl = url,
                username = username.ifBlank { null },
                password = password.ifBlank { null },
                mode = mode,
                dryRun = false
            )

            val summary = engine.execute(request)

            val message = if (summary.hasErrors) {
                summary.errors.first()
            } else if (summary.totalFiles > 0 || summary.messages.isNotEmpty()) {
                summary.messages.firstOrNull() ?: getString(com.yuyan.imemodule.R.string.sync_success)
            } else {
                getString(com.yuyan.imemodule.R.string.sync_success)
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
