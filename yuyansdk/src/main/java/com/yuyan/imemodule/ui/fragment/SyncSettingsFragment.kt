package com.yuyan.imemodule.ui.fragment

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.sync.SyncEngine
import com.yuyan.imemodule.sync.SyncMode
import com.yuyan.imemodule.sync.SyncRequest
import com.yuyan.imemodule.ui.fragment.base.ManagedPreferenceFragment
import com.yuyan.inputmethod.core.Rime
import kotlinx.coroutines.launch
import java.io.File

/**
 * WebDAV 同步设置页面。
 */
class SyncSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().sync) {

    private val syncPrefs = AppPrefs.getInstance().sync
    private val engine = SyncEngine()

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        // 立即同步按钮
        val syncNowPref = Preference(screen.context).apply {
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
        val pushPref = Preference(screen.context).apply {
            key = "sync_push"
            title = getString(com.yuyan.imemodule.R.string.sync_push)
            setOnPreferenceClickListener {
                performSync(SyncMode.Push)
                true
            }
        }
        screen.addPreference(pushPref)

        // 从远端拉取按钮
        val pullPref = Preference(screen.context).apply {
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
        val url = syncPrefs.webdavUrl.getValue()
        val username = syncPrefs.webdavUsername.getValue()
        val password = syncPrefs.webdavPassword.getValue()

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
            val request = SyncRequest(
                deviceId = android.provider.Settings.Secure.getString(
                    requireContext().contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown-android",
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
}
