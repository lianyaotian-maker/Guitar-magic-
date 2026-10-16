package com.guitarmagic.tuner.data

import android.content.Context
import com.guitarmagic.tuner.core.CustomTunings
import com.guitarmagic.tuner.metronome.MetronomeConfig
import com.guitarmagic.tuner.metronome.Subdivision
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning

/** 本地保存的设置。所有读取都做了容错，数据损坏时回到默认值。 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("tuner_settings", Context.MODE_PRIVATE)

    var a4: Double
        get() = prefs.getFloat(KEY_A4, MusicTheory.DEFAULT_A4.toFloat()).toDouble()
            .let { if (it.isNaN()) MusicTheory.DEFAULT_A4 else it }
            .coerceIn(MusicTheory.MIN_A4, MusicTheory.MAX_A4)
        set(value) = prefs.edit().putFloat(KEY_A4, value.toFloat()).apply()

    var selectedTuningId: String?
        get() = prefs.getString(KEY_TUNING, null)
        set(value) = prefs.edit().putString(KEY_TUNING, value).apply()

    var customTunings: List<Tuning>
        get() = CustomTunings.decode(prefs.getString(KEY_CUSTOM_TUNINGS, null))
        set(value) = prefs.edit().putString(KEY_CUSTOM_TUNINGS, CustomTunings.encode(value)).apply()

    var useFlats: Boolean
        get() = prefs.getBoolean(KEY_USE_FLATS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_FLATS, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var permissionRequested: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, value).apply()

    var metronomeConfig: MetronomeConfig
        get() = MetronomeConfig(
            bpm = prefs.getInt(KEY_BPM, 100),
            beatsPerBar = prefs.getInt(KEY_BEATS, 4),
            subdivision = Subdivision.entries.firstOrNull { it.name == prefs.getString(KEY_SUBDIVISION, null) }
                ?: Subdivision.NONE,
            accentFirst = prefs.getBoolean(KEY_ACCENT, true),
        ).normalized()
        set(value) = prefs.edit()
            .putInt(KEY_BPM, value.bpm)
            .putInt(KEY_BEATS, value.beatsPerBar)
            .putString(KEY_SUBDIVISION, value.subdivision.name)
            .putBoolean(KEY_ACCENT, value.accentFirst)
            .apply()

    private companion object {
        const val KEY_A4 = "a4"
        const val KEY_TUNING = "tuning"
        const val KEY_CUSTOM_TUNINGS = "custom_tunings_v1"
        const val KEY_USE_FLATS = "use_flats"
        const val KEY_HAPTICS = "haptics"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
        const val KEY_BPM = "metronome_bpm"
        const val KEY_BEATS = "metronome_beats"
        const val KEY_SUBDIVISION = "metronome_subdivision"
        const val KEY_ACCENT = "metronome_accent"
    }
}
