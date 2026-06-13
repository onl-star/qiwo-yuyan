package com.yuyan.inputmethod.core

import android.content.Context
import android.util.Log
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.application.Launcher
import kotlin.system.measureTimeMillis

class Rime(fullCheck: Boolean) {

    init {
        startup(Launcher.instance.context, fullCheck)
    }

    companion object {
        private const val TAG = "QiwoRimeCore"
        private var instance: Rime? = null
        private var mContext: RimeContext? = null
        private var mStatus: RimeStatus? = null
        private var directCompositionCaretAvailable: Boolean? = null

        @JvmStatic
        fun getInstance(fullCheck: Boolean = false): Rime {
            if (instance == null) instance = Rime(fullCheck)
            return instance!!
        }

        init {
            try {
                Log.i(TAG, "loading JNI library yuyanime")
                System.loadLibrary("yuyanime")
                Log.i(TAG, "loaded JNI library yuyanime")
            } catch (error: UnsatisfiedLinkError) {
                Log.e(TAG, "failed to load JNI library yuyanime", error)
                throw error
            }
        }

        fun startup(context: Context, fullCheck: Boolean) {
            Log.i(TAG, "startupRime sharedDir=${CustomConstant.RIME_DICT_PATH} userDir=${CustomConstant.RIME_DICT_PATH} fullCheck=$fullCheck")
            startupRime(context, CustomConstant.RIME_DICT_PATH, CustomConstant.RIME_DICT_PATH, fullCheck)
            updateStatus()
        }

        @JvmStatic
        fun destroy() {
            exitRime()
            instance = null
        }

        fun updateStatus() {
            measureTimeMillis {
                mStatus = getRimeStatus() ?: RimeStatus()
            }
        }

        fun updateContext() {
            measureTimeMillis {
                mContext = getRimeContext() ?: RimeContext()
            }
            updateStatus()
        }

        @JvmStatic
        val isComposing get() = mStatus?.isComposing == true

        @JvmStatic
        fun hasMenu(): Boolean {
            return isComposing && mContext?.menu?.numCandidates != 0
        }

        @JvmStatic
        fun hasRight(): Boolean {
            return hasMenu() && mContext?.menu?.isLastPage == false
        }

        @JvmStatic
        val composition: RimeComposition?
            get() = mContext?.composition

        @JvmStatic
        val compositionText: String
            get() = composition?.preedit ?: ""

        @JvmStatic
        fun processKey(keycode: Int, mask: Int): Boolean {
            if (keycode <= 0 || keycode == 0xffffff) return false
            setRimePageSize(100)
            return processRimeKey(keycode, mask).also {
                updateContext()
            }
        }

        @JvmStatic
        fun replaceKey(caretPos: Int, length: Int, key: String): Boolean {
            return replaceRimeKey(caretPos, length, key).also {
                updateContext()
            }
        }

        @JvmStatic
        fun clearComposition() { clearRimeComposition()
            updateContext()
        }

        @JvmStatic
        fun selectCandidate(index: Int): Boolean {
            val selected = selectRimeCandidate(index)
            Log.i(TAG, "selectRimeCandidate index=$index result=$selected")
            updateContext()
            return selected
        }

        @JvmStatic
        fun setOption(option: String, value: Boolean) {
            setRimeOption(option, value)
        }

        @JvmStatic
        fun selectSchema(schemaId: String): Boolean {
            return selectRimeSchema(schemaId).also {
                updateContext()
            }
        }

        fun getAssociateList(key: String?): Array<String?> {
            return getRimeAssociateList(key)
        }

        fun chooseAssociate(index: Int): Boolean {
            return selectRimeAssociate(index)
        }

        @JvmStatic
        fun rawInput(): String {
            if (!hasDirectCompositionCaret()) return compositionText
            return try {
                getRimeRawInput()
            } catch (_: UnsatisfiedLinkError) {
                Log.w(TAG, "direct raw input JNI is unavailable; using composition preedit")
                directCompositionCaretAvailable = false
                compositionText
            }
        }

        @JvmStatic
        fun compositionCaret(): Int {
            if (!hasDirectCompositionCaret()) {
                return composition?.cursorPos?.coerceIn(0, compositionText.length) ?: compositionText.length
            }
            return try {
                getRimeCompositionCaret()
            } catch (_: UnsatisfiedLinkError) {
                Log.w(TAG, "direct composition caret JNI is unavailable; using context cursor")
                directCompositionCaretAvailable = false
                composition?.cursorPos?.coerceIn(0, compositionText.length) ?: compositionText.length
            }
        }

        @JvmStatic
        fun setCompositionCaret(caretPos: Int): Int {
            if (!hasDirectCompositionCaret()) {
                return moveLegacyCompositionCaret(caretPos)
            }
            return try {
                Log.i(TAG, "set direct composition caret target=$caretPos")
                setRimeCompositionCaret(caretPos).also {
                    Log.i(TAG, "set direct composition caret result=$it")
                    updateContext()
                }
            } catch (_: UnsatisfiedLinkError) {
                Log.w(TAG, "direct composition caret JNI is unavailable; using legacy caret movement")
                directCompositionCaretAvailable = false
                moveLegacyCompositionCaret(caretPos)
            }
        }

        private fun hasDirectCompositionCaret(): Boolean {
            directCompositionCaretAvailable?.let { return it }
            return try {
                getRimeCompositionCaret()
                directCompositionCaretAvailable = true
                Log.i(TAG, "direct composition caret JNI is available")
                true
            } catch (_: UnsatisfiedLinkError) {
                directCompositionCaretAvailable = false
                Log.w(TAG, "direct composition caret JNI is unavailable")
                false
            }
        }

        private fun moveLegacyCompositionCaret(caretPos: Int): Int {
            val length = compositionText.length
            val target = caretPos.coerceIn(0, length)
            val current = composition?.cursorPos?.coerceIn(0, length)

            if (current == null) {
                moveLegacyCaret("End", 1)
                moveLegacyCaret("Left", length - target)
            } else if (target < current) {
                moveLegacyCaret("Left", current - target)
            } else if (target > current) {
                moveLegacyCaret("Right", target - current)
            }

            updateContext()
            val result = composition?.cursorPos?.coerceIn(0, compositionText.length) ?: target
            Log.i(TAG, "moved legacy composition caret target=$target result=$result length=${compositionText.length}")
            return result
        }

        private fun moveLegacyCaret(keyName: String, steps: Int) {
            if (steps <= 0) return
            val keyCode = getRimeKeycodeByName(keyName)
            if (keyCode == 0) return
            repeat(steps) {
                processRimeKey(keyCode, 0)
            }
        }

        @JvmStatic
        external fun startupRime(context: Context, sharedDir: String, userDir: String, fullCheck: Boolean, )

        @JvmStatic
        external fun exitRime()

        @JvmStatic
        external fun setRimePageSize(pageSize:Int)

        @JvmStatic
        external fun processRimeKey(keycode: Int, mask: Int): Boolean

        @JvmStatic
        external fun replaceRimeKey(caretPos: Int, length: Int, key: String?): Boolean

        @JvmStatic
        external fun clearRimeComposition()

        @JvmStatic
        external fun getRimeCommit(): RimeCommit?

        @JvmStatic
        external fun getRimeContext(): RimeContext?

        @JvmStatic
        external fun getRimeStatus(): RimeStatus?

        @JvmStatic
        external fun setRimeOption(option: String, value: Boolean, )

        @JvmStatic
        external fun getCurrentRimeSchema(): String

        @JvmStatic
        external fun selectRimeSchema(schemaId: String): Boolean

        @JvmStatic
        external fun selectRimeCandidate(index: Int): Boolean

        @JvmStatic
        external fun getRimeKeycodeByName(name: String): Int

        @JvmStatic
        external fun getRimeAssociateList(key: String?): Array<String?>

        @JvmStatic
        external fun selectRimeAssociate(index: Int): Boolean

        @JvmStatic
        external fun getRimeRawInput(): String

        @JvmStatic
        external fun getRimeCompositionCaret(): Int

        @JvmStatic
        external fun setRimeCompositionCaret(caretPos: Int): Int
    }
}
