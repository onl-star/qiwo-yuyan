package com.yuyan.inputmethod

import android.view.KeyEvent
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.manager.InputModeSwitcher
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.StringUtils
import com.yuyan.inputmethod.core.CandidateListItem
import com.yuyan.inputmethod.core.Rime
import com.yuyan.inputmethod.data.InputKey
import com.yuyan.inputmethod.data.KeyRecordStack
import com.yuyan.inputmethod.util.DoublePinYinUtils
import com.yuyan.inputmethod.util.LX17PinYinUtils
import com.yuyan.inputmethod.util.PinyinSegmentation
import com.yuyan.inputmethod.util.QwertyPinYinUtils
import com.yuyan.inputmethod.util.T9PinYinUtils
import java.util.Locale

object RimeEngine {
    private val keyRecordStack = KeyRecordStack()
    private var pinyins: Array<String> = emptyArray() // 候选词界面的候选拼音列表
    var showCandidates: List<CandidateListItem> = emptyList() // 所有待展示的候选词
    var showComposition: String = "" // 候选词上方展示的拼音
    var preCommitText: String = "" // 待提交的文字
    private var compositionCaretActive = false
    private var compositionCaret: Int? = null
    private var rawPinyinComposition: String = ""
    private data class ConfirmedPinyinSyllable(
        val syllable: String,
        val sourceText: String,
        val sourceLength: Int,
        val isCorrection: Boolean
    )
    private var confirmedPinyinSyllables: List<ConfirmedPinyinSyllable> = emptyList()
    private var visibleRawPinyinComposition: String = ""
    private var internalPinyinQuery: String = ""
    private var pinyinSegmentationStepChoices: List<PinyinSegmentation.SyllableChoice> = emptyList()
    private var pinyinSegmentationChoices: List<String> = emptyList()
    private var activePinyinSegmentationIndex: Int = -1
    private var customPhraseSize: Int = 0 // 自定义引擎候选词长度
    const val MASK_CASE_LOWER = 0
    private var charCase = 0x0000
    fun init() {
        Rime.getInstance(false)
    }

    fun selectSchema(mod: String): Boolean {
        clearCompositionCaret()
        clearPinyinSegmentation()
        keyRecordStack.clear()
        charCase = MASK_CASE_LOWER
        Rime.startup(Launcher.instance.context, false)
        return Rime.selectSchema(mod)
    }

    fun getCurrentRimeSchema(): String {
        return Rime.getCurrentRimeSchema()
    }

    /**
     * 是否输入完毕
     */
    fun isFinish(): Boolean {
        return keyRecordStack.isEmpty() && !Rime.isComposing
    }

    fun onNormalKey(event: KeyEvent) {
        restoreVisibleRawPinyinCompositionIfNeeded()
        val keyCode = event.keyCode
        val keyChar = if(keyCode == KeyEvent.KEYCODE_APOSTROPHE || keyCode == KeyEvent.KEYCODE_SEMICOLON) if(isFinish()) '/'.code else '\''.code
            else event.unicodeChar
        if (keyRecordStack.pushKey(event))Rime.processKey(keyChar, event.action)
        updateCandidatesOrCommitText()
    }

    fun onDeleteKey() {
        restoreVisibleRawPinyinCompositionIfNeeded()
        processDelAction()
        updateCandidatesOrCommitText()
    }

    fun selectCandidate(index: Int): String? {
        val indexReal = index - customPhraseSize
        Rime.selectCandidate(indexReal)
        keyRecordStack.pushCandidateSelectAction()
        return updateCandidatesOrCommitText()
    }

    fun getNextPageCandidates(): Array<CandidateListItem> {
        return if (Rime.hasRight()) {
            Rime.processKey(getRimeKeycodeByName("Page_Down"), 0)
           val candidates = Rime.getRimeContext()!!.candidates
            when (charCase) {
                KeyEvent.META_SHIFT_ON -> {
                    for (item in candidates) {
                        item.text = item.text.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                }
                KeyEvent.META_CAPS_LOCK_ON -> {
                    for (item in candidates) {
                        item.text = item.text.uppercase()
                    }
                }
                else -> {
                    for (item in candidates) {
                        item.text = item.text.lowercase()
                    }
                }
            }
            candidates
        } else emptyArray()
    }

    fun isFullKeyboardPinyinCompositionEditable(): Boolean {
        val schema = Rime.getCurrentRimeSchema()
        return isFullKeyboardPinyinSchema(schema) &&
                Rime.compositionText.isNotEmpty()
    }

    fun isCompositionCaretActive(): Boolean {
        return compositionCaretActive && compositionCaret != null && isFullKeyboardPinyinCompositionEditable()
    }

    fun isCompositionEditingAvailable(): Boolean {
        return isFullKeyboardPinyinCompositionEditable()
    }

    fun isPinyinSegmentationSelectorAvailable(): Boolean {
        refreshPinyinSegmentation()
        return pinyinSegmentationChoices.isNotEmpty()
    }

    fun pinyinSegmentationChoices(): Array<String> {
        refreshPinyinSegmentation()
        return pinyinSegmentationChoices.toTypedArray()
    }

    fun pinyinSegmentationDisplayChoices(): Array<String> {
        refreshPinyinSegmentation()
        return pinyinSegmentationStepChoices.map { pinyinSegmentationDisplayLabel(it) }.toTypedArray()
    }

    fun activePinyinSegmentationIndex(): Int {
        refreshPinyinSegmentation()
        return activePinyinSegmentationIndex
    }

    fun pinyinSegmentationContextLabel(): String {
        refreshPinyinSegmentation()
        return if (confirmedPinyinSyllables.isEmpty()) {
            ""
        } else {
            confirmedPinyinSyllables.joinToString("'") { it.syllable } + "'"
        }
    }

    fun selectPinyinSegmentation(index: Int): Boolean {
        refreshPinyinSegmentation()
        val selected = pinyinSegmentationStepChoices.getOrNull(index) ?: return false
        if (!isFullKeyboardPinyinCompositionEditable()) {
            clearPinyinSegmentation()
            return false
        }
        val visibleRaw = normalizedVisibleRawComposition() ?: return false
        val consumedLength = confirmedPinyinSyllables.sumOf { it.sourceLength }
        val sourceText = visibleRaw.substring(consumedLength, (consumedLength + selected.sourceLength).coerceAtMost(visibleRaw.length))
        confirmedPinyinSyllables = confirmedPinyinSyllables + ConfirmedPinyinSyllable(
            syllable = selected.syllable,
            sourceText = sourceText,
            sourceLength = selected.sourceLength,
            isCorrection = selected.isCorrection
        )
        visibleRawPinyinComposition = visibleRaw
        internalPinyinQuery = buildInternalPinyinQuery(visibleRaw)
        val currentComposition = Rime.compositionText
        val replaced = Rime.replaceKey(0, currentComposition.length, internalPinyinQuery)
        if (!replaced) return false
        updateCandidatesOrCommitText()
        refreshPinyinSegmentation()
        return true
    }

    fun clearPinyinSegmentation() {
        rawPinyinComposition = ""
        confirmedPinyinSyllables = emptyList()
        visibleRawPinyinComposition = ""
        internalPinyinQuery = ""
        pinyinSegmentationStepChoices = emptyList()
        pinyinSegmentationChoices = emptyList()
        activePinyinSegmentationIndex = -1
    }

    private fun refreshPinyinSegmentation() {
        if (!isFullKeyboardPinyinCompositionEditable()) {
            clearPinyinSegmentation()
            return
        }
        val rawComposition = normalizedVisibleRawComposition() ?: run {
            clearPinyinSegmentation()
            return
        }
        trimConfirmedPinyinToVisibleRaw(rawComposition)
        val consumedLength = confirmedPinyinSyllables.sumOf { it.sourceLength }
        val choices = PinyinSegmentation.currentStepChoices(rawComposition, consumedLength)
        if (choices.isEmpty()) {
            pinyinSegmentationStepChoices = emptyList()
            pinyinSegmentationChoices = emptyList()
            activePinyinSegmentationIndex = -1
            rawPinyinComposition = rawComposition
            return
        }
        if (confirmedPinyinSyllables.isEmpty() && choices.size <= 1) {
            clearPinyinSegmentation()
            rawPinyinComposition = rawComposition
            return
        }
        rawPinyinComposition = rawComposition
        pinyinSegmentationStepChoices = choices
        pinyinSegmentationChoices = choices.map { it.label }
        activePinyinSegmentationIndex = 0
    }

    private fun normalizedVisibleRawComposition(): String? {
        val visibleRaw = visibleRawPinyinComposition.takeIf { it.isNotEmpty() } ?: Rime.compositionText
        return PinyinSegmentation.normalizeInput(visibleRaw)
    }

    private fun trimConfirmedPinyinToVisibleRaw(rawComposition: String) {
        if (confirmedPinyinSyllables.isEmpty()) return
        val kept = mutableListOf<ConfirmedPinyinSyllable>()
        var consumedLength = 0
        for (confirmed in confirmedPinyinSyllables) {
            val end = consumedLength + confirmed.sourceLength
            if (end > rawComposition.length) break
            if (rawComposition.substring(consumedLength, end) != confirmed.sourceText) break
            kept.add(confirmed)
            consumedLength = end
        }
        if (kept.size != confirmedPinyinSyllables.size) {
            confirmedPinyinSyllables = kept
            if (kept.isEmpty()) {
                visibleRawPinyinComposition = ""
                internalPinyinQuery = ""
            }
        }
    }

    private fun buildInternalPinyinQuery(visibleRaw: String): String {
        val consumedLength = confirmedPinyinSyllables.sumOf { it.sourceLength }
        val confirmedQuery = confirmedPinyinSyllables.joinToString("'") { it.syllable }
        val remaining = visibleRaw.drop(consumedLength)
        return listOf(confirmedQuery, remaining)
            .filter { it.isNotEmpty() }
            .joinToString("'")
    }

    private fun pinyinSegmentationDisplayLabel(choice: PinyinSegmentation.SyllableChoice): String {
        val visibleRaw = normalizedVisibleRawComposition().orEmpty()
        val consumedLength = confirmedPinyinSyllables.sumOf { it.sourceLength }
        val confirmedPrefix = confirmedPinyinSyllables
            .joinToString("'") { it.syllable }
            .takeIf { it.isNotEmpty() }
            ?.let { "$it'" }
            .orEmpty()
        val remaining = visibleRaw.drop(consumedLength + choice.sourceLength)
        return buildString {
            append(confirmedPrefix)
            append('[')
            append(choice.label)
            append(']')
            if (remaining.isNotEmpty()) {
                append('\'')
                append(remaining)
            }
        }
    }

    private fun restoreVisibleRawPinyinCompositionIfNeeded() {
        val visibleRaw = visibleRawPinyinComposition.takeIf { it.isNotEmpty() } ?: return
        if (Rime.compositionText != visibleRaw) {
            Rime.replaceKey(0, Rime.compositionText.length, visibleRaw)
            showComposition = visibleRaw
        }
        clearPinyinSegmentation()
    }

    fun compositionTextForEditing(): String {
        return if (isFullKeyboardPinyinCompositionEditable()) Rime.compositionText else ""
    }

    fun compositionTextForCaretDisplay(): String {
        return compositionTextForEditing()
    }

    fun compositionCaretBoundary(): Int? {
        val caret = compositionCaret ?: return null
        val composition = compositionTextForEditing()
        if (composition.isEmpty()) return null
        return caret.coerceIn(0, composition.length)
    }

    fun setCompositionCaret(caret: Int): Boolean {
        restoreVisibleRawPinyinCompositionIfNeeded()
        if (!isFullKeyboardPinyinCompositionEditable()) {
            clearCompositionCaret()
            return false
        }
        val composition = compositionTextForEditing()
        if (composition.isEmpty()) {
            clearCompositionCaret()
            return false
        }
        compositionCaret = caret.coerceIn(0, composition.length)
        compositionCaretActive = true
        return true
    }

    fun clearCompositionCaret() {
        compositionCaretActive = false
        compositionCaret = null
    }

    fun compositionTextForDisplay(): String {
        val composition = compositionTextForEditing()
        if (composition.isEmpty() || !isCompositionCaretActive()) return composition
        val displayCaret = compositionCaretBoundary() ?: return composition
        return composition.substring(0, displayCaret) + "|" + composition.substring(displayCaret)
    }

    fun insertCompositionAtCaret(key: String): Boolean {
        if (!isCompositionCaretActive()) return false
        restoreVisibleRawPinyinCompositionIfNeeded()
        val caret = clampCompositionCaret(compositionCaret ?: Rime.compositionText.length)
        val nextCaret = replaceCompositionTextAtCaret(caret, insertedText = key) ?: return false
        updateCandidatesOrCommitText()
        keyRecordStack.clear()
        syncCompositionCaretAfterTextReplace(nextCaret)
        return true
    }

    fun deleteCompositionBeforeCaret(): Boolean {
        if (!isCompositionCaretActive()) return false
        restoreVisibleRawPinyinCompositionIfNeeded()
        val caret = clampCompositionCaret(compositionCaret ?: Rime.compositionText.length)
        if (caret <= 0) return true
        val nextCaret = replaceCompositionTextAtCaret(caret, deleteBeforeCaret = true) ?: return false
        updateCandidatesOrCommitText()
        keyRecordStack.clear()
        syncCompositionCaretAfterTextReplace(nextCaret)
        return true
    }

    private fun clampCompositionCaret(caret: Int): Int {
        return caret.coerceIn(0, Rime.compositionText.length)
    }

    private fun replaceCompositionTextAtCaret(
        caret: Int,
        insertedText: String = "",
        deleteBeforeCaret: Boolean = false
    ): Int? {
        val composition = Rime.compositionText
        val safeCaret = caret.coerceIn(0, composition.length)
        val deleteStart = if (deleteBeforeCaret) (safeCaret - 1).coerceAtLeast(0) else safeCaret
        val updatedComposition = composition.substring(0, deleteStart) +
                insertedText +
                composition.substring(safeCaret)
        val replaced = if (updatedComposition.isEmpty()) {
            Rime.replaceKey(0, composition.length, "").also {
                if (!it) Rime.clearComposition()
            }
            true
        } else {
            Rime.replaceKey(0, composition.length, updatedComposition)
        }
        return if (replaced) deleteStart + insertedText.length else null
    }

    private fun syncCompositionCaretAfterTextReplace(targetCaret: Int) {
        if (isFullKeyboardPinyinCompositionEditable()) {
            compositionCaret = targetCaret.coerceIn(0, Rime.compositionText.length)
            compositionCaretActive = true
        } else {
            clearCompositionCaret()
        }
    }

    private fun isFullKeyboardPinyinSchema(schema: String): Boolean {
        return schema == CustomConstant.SCHEMA_ZH_QWERTY || schema == CustomConstant.SCHEMA_FROST
    }

    fun selectPinyin(index: Int) {
        val pinyinKey = keyRecordStack.pushPinyinSelectAction(pinyins[index]) ?: return
        Rime.replaceKey(pinyinKey.posInInput, pinyinKey.t9Keys().length, pinyinKey.pinyin())
        updateCandidatesOrCommitText()
    }

    fun predictAssociationWords(text: String) {
        pinyins = emptyArray()
        if (text.isNotEmpty()) {
            showCandidates = buildList {
                val words = Rime.getAssociateList(text)
                val firstFive = words.take(5)
                addAll(firstFive.filterNotNull().map { CandidateListItem("", it) })
                addAll(CustomEngine.predictAssociationWordsChinese(text).map { CandidateListItem("", it) })
                val remaining = words.drop(5)
                addAll(remaining.filterNotNull().map { CandidateListItem("", it) })
            }
            showComposition = ""
        }
    }

    fun selectAssociation(index: Int) {
        val indexReal = index - customPhraseSize
        Rime.chooseAssociate(indexReal)
        updateCandidatesOrCommitText()
        preCommitText = showCandidates.getOrNull(indexReal)?.text?:""
    }

    fun reset() {
        showCandidates = emptyList()
        pinyins = emptyArray()
        showComposition = ""
        preCommitText = ""
        clearCompositionCaret()
        clearPinyinSegmentation()
        keyRecordStack.clear()
        Rime.clearComposition()
        if(charCase == KeyEvent.META_SHIFT_ON) charCase = MASK_CASE_LOWER
    }

    fun destroy() = Rime.destroy()

    fun processDelAction() {
        when (val lastKey = keyRecordStack.pop()) {
            is InputKey.PinyinKey -> {
                val pinyinKey = keyRecordStack.restorePinyinToT9Key(lastKey) ?: return
                replacePinyinWithT9Keys(pinyinKey)
            }
            InputKey.SelectPinyinAction -> {
                val pinyinKey = keyRecordStack.restorePinyinToT9Key() ?: return
                replacePinyinWithT9Keys(pinyinKey)
            }
            is InputKey.Apostrophe -> {
                if (!lastKey.dummy) {
                    Rime.processKey(getRimeKeycodeByName("BackSpace"), 0)
                }
            }
            else -> {
                Rime.processKey(getRimeKeycodeByName("BackSpace"), 0)
            }
        }
    }

    private fun replacePinyinWithT9Keys(pinyinKey: InputKey.PinyinKey) {
        /**
         * 当前输入状态是“你h”时，引擎默认删除行为是“ni”（删除h并且删除“你”的选中状态）
         * 可能存在引擎操作栈与记录的操作栈不一样的问题
         * 临时方案，尝试不同长度的替换，至少保证可以把拼音回退成9键
         */
        if (!Rime.replaceKey(pinyinKey.posInInput, pinyinKey.inputKeyLength, pinyinKey.t9Keys())) {
            Rime.replaceKey(pinyinKey.posInInput, pinyinKey.pinyinLength, pinyinKey.t9Keys())
        }
    }

    private fun updateCandidatesOrCommitText(): String? {
        val rimeCommit = Rime.getRimeCommit()
        if (rimeCommit != null) {
            clearCompositionCaret()
            clearPinyinSegmentation()
            keyRecordStack.clear()
            preCommitText = rimeCommit.commitText
            preCommitText = if (charCase == KeyEvent.META_SHIFT_ON) {
                preCommitText.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } else if (charCase == KeyEvent.META_CAPS_LOCK_ON) {
                preCommitText.uppercase()
            } else {
                preCommitText.lowercase()
            }
            showComposition = ""
            showCandidates = emptyList()
            return preCommitText
        }
        val candidates = Rime.getRimeContext()?.candidates?.asList() ?: emptyList()
        customPhraseSize = 0
        val compositionText = Rime.compositionText
        if (compositionText.isEmpty()) {
            clearCompositionCaret()
            clearPinyinSegmentation()
        }
        showCandidates = when {
            compositionText.isNotBlank() -> {
                val phrase = CustomEngine.processPhrase(compositionText.replace("\'", ""))
                if(InputModeSwitcher.isEnglish && StringUtils.isLetter(compositionText) &&
                    !compositionText.equals(candidates.first().text, ignoreCase = true) ){
                    phrase.add(0, compositionText)
                }
                customPhraseSize = phrase.size
                phrase.map { content -> CandidateListItem("📋", content) }.toMutableList().plus(candidates)
            }
            else -> candidates
        }
        var count = Rime.compositionText.count { it in 'A'..'Z' }
        if (count > 0) {
            keyRecordStack.forEachReversed { inputKey ->
                if (inputKey is InputKey.T9Key) inputKey.consumed = count-- <= 0
            }
        }
        var composition = getCurrentComposition(candidates)
        when (charCase) {
            KeyEvent.META_SHIFT_ON -> {
                for (item in showCandidates) item.text = item.text.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                composition = composition.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            KeyEvent.META_CAPS_LOCK_ON -> {
                for (item in showCandidates) item.text = item.text.uppercase()
                composition = composition.uppercase()
            }
            else -> {
                for (item in showCandidates) item.text = item.text.lowercase()
                composition = composition.lowercase()
            }
        }
        val rimeSchema = Rime.getCurrentRimeSchema()
        pinyins = when (rimeSchema) {
            CustomConstant.SCHEMA_ZH_T9 -> {
                T9PinYinUtils.t9KeyToPinyin(compositionText.filter { it.isUpperCase() })
            }
            CustomConstant.SCHEMA_ZH_DOUBLE_LX17 -> {
                LX17PinYinUtils.lx17KeyToPinyin(compositionText.filter { it.isUpperCase() })
            }
            else -> {
                emptyArray()
            }
        }
        showComposition = composition
        preCommitText = ""
        refreshPinyinSegmentation()
        return null
    }

    /**
     * 拿到候选词拼音组合
     */
    fun getPrefixs(): Array<String> {
        return pinyins
    }

    private fun getCurrentComposition(candidates: List<CandidateListItem>): String {
        val composition = Rime.compositionText
        val rimeSchema = Rime.getCurrentRimeSchema()
        if(rimeSchema == CustomConstant.SCHEMA_EN) return ""
        if(composition.isEmpty()) return ""
        if(candidates.isEmpty()) return composition
        val comment = candidates.first().comment
        val result =  when {
            comment.isNotBlank() && comment.startsWith("~") -> composition
            rimeSchema == CustomConstant.SCHEMA_ZH_T9 -> {
                T9PinYinUtils.getT9Composition(composition, comment)
            }
            rimeSchema.startsWith(CustomConstant.SCHEMA_ZH_DOUBLE_FLYPY) -> {
                if(!AppPrefs.getInstance().keyboardSetting.keyboardDoubleInputKey.getValue()) composition
                else DoublePinYinUtils.getDoublePinYinComposition(rimeSchema, composition, comment)
            }
            else -> {
                QwertyPinYinUtils.getQwertyComposition(composition, comment)
            }
        }
        return if (!composition.endsWith("'") && result.endsWith("'")) result.dropLast(1) else result
    }

    /**
     * 设置输入法搜索参数
     */
    fun setImeOption(option: String, value: Boolean) {
        Rime.setOption(option, value)
    }

    /**
     * 获取Rime定义键值
     */
    private fun getRimeKeycodeByName(name: String) : Int {
        return Rime.getRimeKeycodeByName(name)
    }

    fun setCharCase(charCase: Int) {
        this.charCase = charCase
    }

}
