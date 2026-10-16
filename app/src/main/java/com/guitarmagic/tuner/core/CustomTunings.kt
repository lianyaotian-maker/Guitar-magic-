package com.guitarmagic.tuner.core

import com.guitarmagic.tuner.pitch.Tuning

/**
 * 用户自建调弦预设的序列化与校验。
 * 存储格式：每行一个预设，`id<TAB>名称<TAB>midi,midi,...`。
 * 不依赖 org.json，便于在纯 JVM 单元测试中运行。
 */
object CustomTunings {
    const val MAX_NAME_LENGTH = 16
    const val MAX_PRESETS = 50
    private const val ID_PREFIX = "custom_"

    fun encode(tunings: List<Tuning>): String =
        tunings.filter { it.isCustom }.joinToString("\n") { t ->
            "${t.id}\t${sanitizeName(t.displayName)}\t${t.strings.joinToString(",")}"
        }

    /** 解析存储的字符串；损坏或不合法的行会被跳过，不会抛异常。 */
    fun decode(text: String?): List<Tuning> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<Tuning>()
        for (line in text.split('\n')) {
            val parts = line.split('\t')
            if (parts.size != 3) continue
            val id = parts[0].trim()
            if (!id.startsWith(ID_PREFIX) || !seen.add(id)) continue
            val name = sanitizeName(parts[1]).ifEmpty { "未命名" }
            val strings = parts[2].split(',').map { it.trim().toIntOrNull() }
            if (strings.size != Tuning.STRING_COUNT || strings.any { it == null || !isValidMidi(it) }) continue
            result += Tuning(id, name, strings.map { it!! }, isCustom = true)
            if (result.size >= MAX_PRESETS) break
        }
        return result
    }

    fun isValidMidi(midi: Int): Boolean = midi in Tuning.MIN_STRING_MIDI..Tuning.MAX_STRING_MIDI

    /** 去掉换行、制表符等控制字符，合并空白，限制长度。 */
    fun sanitizeName(name: String): String {
        val cleaned = buildString {
            for (ch in name) append(if (ch.isISOControl() || ch.isWhitespace()) ' ' else ch)
        }
        return cleaned.split(' ').filter { it.isNotEmpty() }.joinToString(" ").take(MAX_NAME_LENGTH).trim()
    }

    fun newId(existing: Collection<Tuning>, nowMillis: Long): String {
        var candidate = nowMillis
        val ids = existing.mapTo(HashSet()) { it.id }
        while ("$ID_PREFIX$candidate" in ids) candidate++
        return "$ID_PREFIX$candidate"
    }

    /** 新预设的默认名称：“我的调弦 1”“我的调弦 2”…… 不与已有名称重复。 */
    fun defaultName(existing: Collection<Tuning>): String {
        val names = existing.mapTo(HashSet()) { it.displayName }
        var n = 1
        while ("我的调弦 $n" in names) n++
        return "我的调弦 $n"
    }

    /** 用户编辑后的预设；不合法时返回 null。 */
    fun build(id: String, name: String, strings: List<Int>): Tuning? {
        val clean = sanitizeName(name)
        if (clean.isEmpty()) return null
        if (strings.size != Tuning.STRING_COUNT || strings.any { !isValidMidi(it) }) return null
        return Tuning(id, clean, strings.toList(), isCustom = true)
    }
}
