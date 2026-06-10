package com.qiwo.inputformat

object QiwoInputFormat {
    init {
        System.loadLibrary("qiwo_input_format")
    }

    fun formatCommitText(commitText: String, beforeCursor: String?, afterCursor: String?, enabled: Boolean): String {
        return nativeFormatCommitText(commitText, beforeCursor, afterCursor, enabled)
    }

    @JvmStatic
    private external fun nativeFormatCommitText(
        commitText: String,
        beforeCursor: String?,
        afterCursor: String?,
        enabled: Boolean
    ): String
}
