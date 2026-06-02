package com.yuyan.imemodule.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Manifest JSON 序列化器。
 * 格式与 qiwo-sync-core 兼容。
 */
object ManifestSerializer {

    fun toJsonBytes(manifest: SyncManifest): ByteArray {
        val json = JSONObject().apply {
            put("deviceId", manifest.deviceId)
            put("frontend", manifest.frontend)
            put("updatedAtUtc", manifest.updatedAtUtc.toString())
            val filesObj = JSONObject()
            for ((path, entry) in manifest.files) {
                filesObj.put(path, JSONObject().apply {
                    put("relativePath", entry.relativePath)
                    put("sha256", entry.sha256)
                    put("sizeBytes", entry.sizeBytes)
                    put("lastWriteUtc", entry.lastWriteUtc.toString())
                })
            }
            put("files", filesObj)
        }
        return json.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun fromJsonBytes(bytes: ByteArray): SyncManifest {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val filesObj = json.optJSONObject("files") ?: JSONObject()
        val files = mutableMapOf<String, SyncFileEntry>()
        for (key in filesObj.keys()) {
            val entryObj = filesObj.getJSONObject(key)
            files[key] = SyncFileEntry(
                relativePath = entryObj.optString("relativePath", key),
                sha256 = entryObj.optString("sha256", ""),
                sizeBytes = entryObj.optLong("sizeBytes", 0),
                lastWriteUtc = parseInstant(entryObj.optString("lastWriteUtc"))
            )
        }
        return SyncManifest(
            deviceId = json.optString("deviceId", ""),
            frontend = json.optString("frontend", "YuyanIme"),
            updatedAtUtc = parseInstant(json.optString("updatedAtUtc")),
            files = files
        )
    }

    private fun parseInstant(str: String): Instant {
        return try {
            Instant.parse(str)
        } catch (_: Exception) {
            Instant.EPOCH
        }
    }
}
