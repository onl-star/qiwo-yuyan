package com.yuyan.imemodule.sync

import android.util.Log
import java.io.File

object RimeUserDictDiagnostics {
    private const val TAG = "QiwoUserDict"
    private const val MAX_SAMPLE_SIZE = 8

    data class Snapshot(
        val localUserDbPaths: List<String>,
        val exportedUserDbPaths: List<String>,
        val deviceExportedUserDbPaths: List<String>
    ) {
        val hasLocalUserDb: Boolean get() = localUserDbPaths.isNotEmpty()
        val hasDeviceExportedUserDb: Boolean get() = deviceExportedUserDbPaths.isNotEmpty()
    }

    fun snapshot(rimeUserDir: File, deviceId: String? = null): Snapshot {
        val safeDeviceId = deviceId?.let { InstallationHelper.makeSafeId(it) }
        val localUserDb = mutableListOf<String>()
        val exportedUserDb = mutableListOf<String>()
        val deviceExportedUserDb = mutableListOf<String>()

        if (!rimeUserDir.exists()) {
            return Snapshot(emptyList(), emptyList(), emptyList())
        }

        rimeUserDir.walkTopDown()
            .onFail { file, error ->
                Log.w(TAG, "failed to inspect ${file.absolutePath}: ${error.message}")
            }
            .forEach { file ->
                val relativePath = relativePath(rimeUserDir, file) ?: return@forEach
                if (relativePath.isBlank()) return@forEach

                val lowerName = file.name.lowercase()
                val lowerRelativePath = relativePath.lowercase()
                if (lowerRelativePath.startsWith("sync/")) {
                    if (lowerName.endsWith(".userdb.txt")) {
                        exportedUserDb += relativePath
                        if (safeDeviceId != null && lowerRelativePath.startsWith("sync/$safeDeviceId/")) {
                            deviceExportedUserDb += relativePath
                        }
                    }
                } else if (lowerName.endsWith(".userdb") || lowerName.endsWith(".userdb.txt")) {
                    localUserDb += relativePath
                }
            }

        return Snapshot(
            localUserDb.sorted(),
            exportedUserDb.sorted(),
            deviceExportedUserDb.sorted()
        )
    }

    fun logSnapshot(stage: String, rimeUserDir: File, deviceId: String? = null): Snapshot {
        val snapshot = snapshot(rimeUserDir, deviceId)
        Log.i(
            TAG,
            "stage=$stage rimeDir=${rimeUserDir.absolutePath} " +
                "localUserDb=${snapshot.localUserDbPaths.size} " +
                "exportedUserDb=${snapshot.exportedUserDbPaths.size} " +
                "deviceExportedUserDb=${snapshot.deviceExportedUserDbPaths.size} " +
                "localSamples=${snapshot.localUserDbPaths.sample()} " +
                "deviceExportSamples=${snapshot.deviceExportedUserDbPaths.sample()}"
        )
        return snapshot
    }

    fun warningForMissingLocalUserDb(snapshot: Snapshot): String? {
        return if (!snapshot.hasLocalUserDb && !snapshot.hasDeviceExportedUserDb) {
            "Rime user dictionary not found; local user words were not exported."
        } else {
            null
        }
    }

    private fun relativePath(root: File, file: File): String? {
        return try {
            file.relativeTo(root).path.replace('\\', '/').takeIf { it != "." }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun List<String>.sample(): String {
        if (isEmpty()) return "[]"
        return take(MAX_SAMPLE_SIZE).joinToString(prefix = "[", postfix = "]")
    }
}
