package com.yuyan.inputmethod.util

import java.util.Locale

object PinyinSegmentation {
    data class SyllableChoice(
        val syllable: String,
        val sourceLength: Int,
        val isCorrection: Boolean = false,
        val editCost: Int = if (isCorrection) 1 else 0,
        val totalEditCost: Int = editCost
    ) {
        val label: String
            get() = if (isCorrection) "$syllable*" else syllable
    }

    private data class SearchPath(
        val offset: Int,
        val firstChoice: SyllableChoice?,
        val totalEditCost: Int,
        val steps: Int
    )

    private const val maxTotalEditCost = 2
    private const val maxEditCostPerSyllable = 2
    private const val maxCurrentStepEditCost = 1
    private const val maxBeamWidth = 32

    private val syllables = setOf(
        "a", "ai", "an", "ang", "ao",
        "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
        "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
        "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
        "e", "ei", "en", "eng", "er",
        "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
        "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
        "ka", "kai", "kan", "kang", "kao", "ke", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "luan", "lue", "lun", "luo", "lv",
        "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
        "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nuo", "nv",
        "o", "ou",
        "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
        "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
        "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "ruan", "rui", "run", "ruo",
        "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
        "ta", "tai", "tan", "tang", "tao", "te", "tei", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
        "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
        "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
        "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
        "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo"
    )

    fun segmentations(raw: String): List<String> {
        val normalized = normalizeInput(raw) ?: return emptyList()
        return splitAll(normalized)
            .map { it.joinToString("'") }
            .distinct()
            .sorted()
    }

    fun currentStepChoices(raw: String, consumedLength: Int = 0, maxChoices: Int = 8): List<SyllableChoice> {
        val normalized = normalizeInput(raw) ?: return emptyList()
        val start = consumedLength.coerceIn(0, normalized.length)
        val paths = completePaths(normalized, start)
        if (paths.isEmpty()) return emptyList()
        val bestTotalCost = paths.minOf { it.totalEditCost }

        return paths
            .mapNotNull { path ->
                path.firstChoice?.copy(totalEditCost = path.totalEditCost)
            }
            .filter { choice ->
                // If exact full paths exist, keep the parsing area focused on the exact
                // syllable steps. Fuzzy choices become useful when the full raw input
                // cannot be resolved exactly, such as "makgguo".
                bestTotalCost != 0 || choice.totalEditCost == 0
            }
            .groupBy { it.label }
            .map { (_, choices) ->
                choices.sortedWith(choiceComparator).first()
            }
            .sortedWith(choiceComparator)
            .take(maxChoices)
    }

    fun hasSegmentableTail(raw: String): Boolean {
        val normalized = normalizeInput(raw) ?: return raw.isEmpty()
        return completePaths(normalized, 0, maxPaths = 1).isNotEmpty()
    }

    fun editDistanceAtMostOne(left: String, right: String): Boolean {
        return editDistance(left, right, maxEdits = 1) != null
    }

    private fun editDistance(left: String, right: String, maxEdits: Int): Int? {
        if (left == right) return 0
        if (kotlin.math.abs(left.length - right.length) > maxEdits) return null
        var leftIndex = 0
        var rightIndex = 0
        var edits = 0
        while (leftIndex < left.length || rightIndex < right.length) {
            if (leftIndex < left.length && rightIndex < right.length && left[leftIndex] == right[rightIndex]) {
                leftIndex += 1
                rightIndex += 1
                continue
            }
            edits += 1
            if (edits > maxEdits) return null
            when {
                left.length > right.length -> leftIndex += 1
                right.length > left.length -> rightIndex += 1
                else -> {
                    leftIndex += 1
                    rightIndex += 1
                }
            }
        }
        return edits
    }

    fun normalizeInput(raw: String): String? {
        val normalized = raw.replace("'", "").lowercase(Locale.ROOT)
        return normalized.takeIf { it.isNotEmpty() && it.all { char -> char in 'a'..'z' } }
    }

    private fun splitAll(normalized: String): List<List<String>> {
        val memo = HashMap<Int, List<List<String>>>()

        fun splitFrom(index: Int): List<List<String>> {
            memo[index]?.let { return it }
            if (index == normalized.length) return listOf(emptyList())
            val result = mutableListOf<List<String>>()
            for (end in index + 1..normalized.length) {
                val syllable = normalized.substring(index, end)
                if (syllable !in syllables) continue
                for (tail in splitFrom(end)) {
                    result.add(listOf(syllable) + tail)
                }
            }
            memo[index] = result
            return result
        }

        return splitFrom(0)
    }

    private val orderedSyllables: List<String> = syllables.sortedWith(compareBy<String> { it.length }.thenBy { it })

    private val choiceComparator = compareBy<SyllableChoice> { if (it.isCorrection) 1 else 0 }
        .thenBy { it.totalEditCost }
        .thenBy { it.editCost }
        .thenBy { it.sourceLength }
        .thenBy { it.syllable }

    private val pathComparator = compareBy<SearchPath> { it.totalEditCost }
        .thenBy { it.steps }
        .thenByDescending { it.offset }
        .thenBy { it.firstChoice?.let { choice -> if (choice.isCorrection) 1 else 0 } ?: 0 }
        .thenBy { it.firstChoice?.syllable.orEmpty() }

    private fun completePaths(
        normalized: String,
        start: Int,
        maxPaths: Int = maxBeamWidth
    ): List<SearchPath> {
        if (start >= normalized.length) return emptyList()
        val completed = mutableListOf<SearchPath>()
        var frontier = listOf(SearchPath(offset = start, firstChoice = null, totalEditCost = 0, steps = 0))
        val maxSteps = normalized.length - start

        while (frontier.isNotEmpty() && completed.size < maxPaths) {
            val next = mutableListOf<SearchPath>()
            for (path in frontier) {
                if (path.offset == normalized.length) {
                    completed.add(path)
                    continue
                }
                if (path.steps >= maxSteps) continue

                val maxEdgeEditCost = if (path.firstChoice == null) maxCurrentStepEditCost else maxEditCostPerSyllable
                for (choice in choicesAt(normalized, path.offset, maxEdgeEditCost, isCurrentStep = path.firstChoice == null)) {
                    val nextTotalCost = path.totalEditCost + choice.editCost
                    if (nextTotalCost > maxTotalEditCost) continue
                    val firstChoice = path.firstChoice ?: choice
                    next.add(
                        SearchPath(
                            offset = path.offset + choice.sourceLength,
                            firstChoice = firstChoice,
                            totalEditCost = nextTotalCost,
                            steps = path.steps + 1
                        )
                    )
                }
            }
            frontier = next
                .sortedWith(pathComparator)
                .take(maxBeamWidth)
        }

        return completed
            .sortedWith(pathComparator)
            .take(maxPaths)
    }

    private fun choicesAt(
        normalized: String,
        offset: Int,
        maxEdgeEditCost: Int,
        isCurrentStep: Boolean
    ): List<SyllableChoice> {
        val remaining = normalized.substring(offset)
        val choices = mutableListOf<SyllableChoice>()
        for (syllable in orderedSyllables) {
            val minSourceLength = (syllable.length - maxEdgeEditCost).coerceAtLeast(1)
            val maxSourceLength = (syllable.length + maxEdgeEditCost).coerceAtMost(remaining.length)
            for (sourceLength in minSourceLength..maxSourceLength) {
                val source = remaining.substring(0, sourceLength)
                val editCost = editDistance(source, syllable, maxEdgeEditCost) ?: continue
                if (isCurrentStep && editCost > 0 && source.firstOrNull() != syllable.firstOrNull()) {
                    continue
                }
                if (isCurrentStep && editCost > 0 && sourceLength != syllable.length) {
                    continue
                }
                if (editCost > 0 && sourceLength > syllable.length && source.startsWith(syllable)) {
                    continue
                }
                choices.add(
                    SyllableChoice(
                        syllable = syllable,
                        sourceLength = sourceLength,
                        isCorrection = editCost > 0,
                        editCost = editCost
                    )
                )
            }
        }
        return choices
            .distinctBy { "${it.label}:${it.sourceLength}:${it.editCost}" }
            .sortedWith(choiceComparator)
    }
}
