package com.yuyan.inputmethod.util

import java.util.Locale

object PinyinSegmentation {
    data class SyllableChoice(
        val syllable: String,
        val sourceLength: Int,
        val isCorrection: Boolean = false
    ) {
        val label: String
            get() = if (isCorrection) "$syllable*" else syllable
    }

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
        val distinctChoices = splitAll(normalized)
            .map { it.joinToString("'") }
            .distinct()
            .sorted()
        return if (distinctChoices.size <= 1) emptyList() else distinctChoices
    }

    fun currentStepChoices(raw: String, consumedLength: Int = 0, maxChoices: Int = 8): List<SyllableChoice> {
        val normalized = normalizeInput(raw) ?: return emptyList()
        val start = consumedLength.coerceIn(0, normalized.length)
        val remaining = normalized.substring(start)
        if (remaining.isEmpty()) return emptyList()

        val exact = syllables
            .filter { syllable -> remaining.startsWith(syllable) && hasSegmentableTail(remaining.substring(syllable.length)) }
            .map { syllable -> SyllableChoice(syllable, syllable.length, isCorrection = false) }
            .sortedWith(compareBy<SyllableChoice> { it.sourceLength }.thenBy { it.syllable })

        val exactSyllables = exact.map { it.syllable }.toSet()
        val correction = if (exact.size == 1) {
            emptyList()
        } else {
            syllables
                .asSequence()
                .flatMap { syllable ->
                    ((syllable.length - 1)..(syllable.length + 1)).asSequence().map { sourceLength ->
                        syllable to sourceLength
                    }
                }
                .filter { (_, sourceLength) -> sourceLength > 0 && sourceLength <= remaining.length }
                .filter { (syllable, _) -> syllable !in exactSyllables }
                .filter { (syllable, sourceLength) -> hasSegmentableTail(remaining.substring(sourceLength)) && editDistanceAtMostOne(remaining.substring(0, sourceLength), syllable) }
                .map { (syllable, sourceLength) -> SyllableChoice(syllable, sourceLength, isCorrection = true) }
                .distinctBy { it.syllable to it.sourceLength }
                .sortedBy { it.syllable }
                .toList()
        }

        return (exact + correction)
            .distinctBy { it.label }
            .take(maxChoices)
    }

    fun hasSegmentableTail(raw: String): Boolean {
        val normalized = normalizeInput(raw) ?: return raw.isEmpty()
        return splitAll(normalized).isNotEmpty()
    }

    fun editDistanceAtMostOne(left: String, right: String): Boolean {
        if (left == right) return true
        if (kotlin.math.abs(left.length - right.length) > 1) return false
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
            if (edits > 1) return false
            when {
                left.length > right.length -> leftIndex += 1
                right.length > left.length -> rightIndex += 1
                else -> {
                    leftIndex += 1
                    rightIndex += 1
                }
            }
        }
        return true
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
}
