package com.guitarmagic.tuner.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong

class MetronomeModelTest {

    private val sr = 48000

    private fun collectTicks(
        scheduler: ClickScheduler,
        totalFrames: Int,
        chunk: Int = 512,
        ticks: MutableList<Tick> = ArrayList(),
    ): MutableList<Tick> {
        val buf = FloatArray(chunk)
        var done = 0
        while (done < totalFrames) {
            val n = minOf(chunk, totalFrames - done)
            scheduler.render(buf, n) { ticks += it }
            done += n
        }
        return ticks
    }

    @Test
    fun ticksAreSampleAccurate_noDriftOverTenMinutes() {
        // 97 BPM 的间隔不是整数个采样，长时间累计也不能漂移
        val config = MetronomeConfig(bpm = 97, beatsPerBar = 4)
        val s = ClickScheduler(sr, config)
        val frames = sr * 600
        val ticks = collectTicks(s, frames, chunk = 1000)
        val interval = sr * 60.0 / 97
        var expected = 0
        while ((expected * interval).roundToLong() < frames) expected++
        assertEquals(expected, ticks.size)
        ticks.forEachIndexed { i, t ->
            assertEquals("tick $i", (i * interval).roundToLong(), t.frame)
        }
    }

    @Test
    fun chunkSize_doesNotAffectTiming() {
        val config = MetronomeConfig(bpm = 133, beatsPerBar = 3, subdivision = Subdivision.TRIPLET)
        val a = collectTicks(ClickScheduler(sr, config), sr * 20, chunk = 64)
        val b = collectTicks(ClickScheduler(sr, config), sr * 20, chunk = 4097)
        assertEquals(a, b)
    }

    @Test
    fun accentPattern_andBeatCounter() {
        val s = ClickScheduler(sr, MetronomeConfig(bpm = 120, beatsPerBar = 3))
        val ticks = collectTicks(s, sr * 3) // 6 拍 + 起点
        val types = ticks.take(6).map { it.type }
        assertEquals(
            listOf(ClickType.ACCENT, ClickType.BEAT, ClickType.BEAT, ClickType.ACCENT, ClickType.BEAT, ClickType.BEAT),
            types,
        )
        assertEquals(listOf(0, 1, 2, 0, 1, 2), ticks.take(6).map { it.beat })
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L, 5L), ticks.take(6).map { it.beatSerial })
    }

    @Test
    fun noAccent_whenDisabledOrSingleBeat() {
        val off = collectTicks(ClickScheduler(sr, MetronomeConfig(bpm = 120, accentFirst = false)), sr * 2)
        assertTrue(off.all { it.type == ClickType.BEAT })
        val single = collectTicks(ClickScheduler(sr, MetronomeConfig(bpm = 120, beatsPerBar = 1)), sr * 2)
        assertTrue(single.all { it.type == ClickType.BEAT })
    }

    @Test
    fun subdivisions() {
        val s = ClickScheduler(sr, MetronomeConfig(bpm = 60, beatsPerBar = 2, subdivision = Subdivision.SIXTEENTH))
        val ticks = collectTicks(s, sr * 2 - 1)
        assertEquals(8, ticks.size)
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), ticks.map { it.sub })
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1), ticks.map { it.beat })
        assertEquals(ClickType.ACCENT, ticks[0].type)
        assertEquals(ClickType.SUB, ticks[1].type)
        assertEquals(ClickType.BEAT, ticks[4].type)
        ticks.zipWithNext().forEach { (a, b) -> assertEquals(sr / 4L, b.frame - a.frame) }
    }

    @Test
    fun tempoChange_takesEffectFromLastTick() {
        val s = ClickScheduler(sr, MetronomeConfig(bpm = 60))
        val ticks = collectTicks(s, sr / 2) // 只有第 0 拍
        assertEquals(1, ticks.size)
        s.updateConfig(MetronomeConfig(bpm = 240)) // 间隔 0.25 秒，已经过了 0.5 秒 → 立即响
        collectTicks(s, sr, ticks = ticks)
        assertEquals(sr / 2L, ticks[1].frame)
        assertEquals(sr / 2L + sr / 4L, ticks[2].frame)
    }

    @Test
    fun beatsPerBarShrink_wrapsSafely() {
        val s = ClickScheduler(sr, MetronomeConfig(bpm = 120, beatsPerBar = 7))
        val ticks = collectTicks(s, sr * 3 - 10) // 拍 0..5
        assertEquals(5, ticks.last().beat)
        s.updateConfig(MetronomeConfig(bpm = 120, beatsPerBar = 3))
        collectTicks(s, sr, ticks = ticks)
        val after = ticks.drop(6)
        assertEquals(0, after.first().beat)
        assertTrue(after.all { it.beat in 0..2 })
    }

    @Test
    fun subdivisionChangeMidBeat_restartsOnNextBeat() {
        val s = ClickScheduler(sr, MetronomeConfig(bpm = 60, beatsPerBar = 4, subdivision = Subdivision.SIXTEENTH))
        val ticks = collectTicks(s, sr / 3) // 拍 0 的前两个细分
        assertEquals(2, ticks.size)
        s.updateConfig(MetronomeConfig(bpm = 60, beatsPerBar = 4, subdivision = Subdivision.NONE))
        collectTicks(s, sr * 3, ticks = ticks)
        val after = ticks.drop(2)
        assertTrue(after.all { it.sub == 0 })
        assertEquals(1, after.first().beat)
    }

    @Test
    fun outputIsBoundedAndNonSilent() {
        val s = ClickScheduler(sr, MetronomeConfig(bpm = 250, subdivision = Subdivision.SIXTEENTH))
        val buf = FloatArray(sr)
        s.render(buf)
        val peak = buf.maxOf { abs(it) }
        assertTrue("peak=$peak", peak in 0.3f..1.0f)
    }

    @Test
    fun configIsClamped() {
        val c = MetronomeConfig(bpm = 999, beatsPerBar = 0).normalized()
        assertEquals(MetronomeConfig.MAX_BPM, c.bpm)
        assertEquals(MetronomeConfig.MIN_BEATS, c.beatsPerBar)
    }

    @Test
    fun tapTempo() {
        val t = TapTempo()
        assertNull(t.tap(1000))
        assertEquals(120, t.tap(1500))
        assertEquals(120, t.tap(2000))
        assertEquals(120, t.tap(2510)) // 少量抖动
        // 间隔太久重新开始
        assertNull(t.tap(6000))
        assertEquals(60, t.tap(7000))
        // 极快点击被限制在最大值
        val fast = TapTempo()
        fast.tap(0)
        assertEquals(MetronomeConfig.MAX_BPM, fast.tap(50))
    }

    @Test
    fun tapTempo_ignoresSingleOutlier() {
        val t = TapTempo()
        listOf(0L, 500L, 1000L, 1500L, 2400L, 2900L).forEach { t.tap(it) }
        assertEquals(120, t.tap(3400))
    }

    @Test
    fun tempoMarkings() {
        assertEquals("Andante 行板", TempoMarkings.of(90))
        assertEquals("Allegro 快板", TempoMarkings.of(120))
        assertEquals("Grave 庄板", TempoMarkings.of(30))
        assertEquals("Prestissimo 最急板", TempoMarkings.of(250))
    }
}
