package com.qiwo.sync

import org.json.JSONObject

/**
 * JNI bridge to Rust qiwo-sync-core.
 * All sync operations are dispatched through nativeSync() with a JSON request.
 */
object QiwoSync {

  init {
    System.loadLibrary("qiwo_sync")
  }

  /** Execute a sync request. Returns the JSON result string. */
  @JvmStatic
  external fun nativeSync(jsonRequest: String): String

  /** Trigger Rime's native user data export/import through libyuyanime. */
  @JvmStatic
  external fun nativeSyncUserData(): Boolean

  fun syncUserData(): Boolean {
    return try {
      nativeSyncUserData()
    } catch (_: UnsatisfiedLinkError) {
      false
    } catch (_: Exception) {
      false
    }
  }

  /** Execute sync with typed parameters. Returns success + message. */
  fun execute(
    rimeUserDir: String,
    remoteUrl: String,
    username: String?,
    password: String?,
    deviceId: String,
    mode: String = "sync",
    dryRun: Boolean = false
  ): SyncResult {
    val request = JSONObject().apply {
      put("rimeUserDir", rimeUserDir)
      put("frontend", "qiwo-yuyan")
      put("remoteUrl", remoteUrl)
      if (!username.isNullOrEmpty()) put("username", username)
      if (!password.isNullOrEmpty()) put("password", password)
      put("deviceId", deviceId)
      put("mode", mode)
      put("dryRun", dryRun)
    }

    return try {
      val json = nativeSync(request.toString())
      val result = JSONObject(json)
      if (result.has("error")) {
        SyncResult(success = false, message = result.getString("error"))
      } else {
        val uploaded = result.optInt("uploaded", 0)
        val downloaded = result.optInt("downloaded", 0)
        val conflicts = result.optInt("conflictsBackedUp", 0)
        SyncResult(
          success = true,
          message = "Synced: ↑$uploaded ↓$downloaded conflicts:$conflicts",
          uploaded = uploaded,
          downloaded = downloaded,
          conflicts = conflicts
        )
      }
    } catch (e: Exception) {
      SyncResult(success = false, message = "Sync failed: ${e.message}")
    }
  }
}

data class SyncResult(
  val success: Boolean,
  val message: String = "",
  val uploaded: Int = 0,
  val downloaded: Int = 0,
  val conflicts: Int = 0
)
