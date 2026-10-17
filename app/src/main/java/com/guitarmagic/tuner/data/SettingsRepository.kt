package com.guitarmagic.tuner.data

import android.content.Context
import com.guitarmagic.tuner.core.CustomTunings
import com.guitarmagic.tuner.core.TuneTolerance
import com.guitarmagic.tuner.metronome.BpmFavorites
import com.guitarmagic.tuner.metronome.MetronomeConfig
import com.guitarmagic.tuner.metronome.SpeedTrainer
import com.guitarmagic.tuner.metronome.Subdivision
import com.guitarmagic.tuner.pitch.DetectionMode
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Sensitivity
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

    var sensitivity: Sensitivity
        get() = Sensitivity.entries.firstOrNull { it.name == prefs.getString(KEY_SENSITIVITY, null) } ?: Sensitivity.MEDIUM
        set(value) = prefs.edit().putString(KEY_SENSITIVITY, value.name).apply()

    var detectionMode: DetectionMode
        get() = DetectionMode.entries.firstOrNull { it.name == prefs.getString(KEY_DETECTION_MODE, null) } ?: DetectionMode.GUITAR
        set(value) = prefs.edit().putString(KEY_DETECTION_MODE, value.name).apply()

    var chimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHIME, true)
        set(value) = prefs.edit().putBoolean(KEY_CHIME, value).apply()

    var tolerance: TuneTolerance
        get() = TuneTolerance.entries.firstOrNull { it.name == prefs.getString(KEY_TOLERANCE, null) } ?: TuneTolerance.STANDARD
        set(value) = prefs.edit().putString(KEY_TOLERANCE, value.name).apply()

    var metronomeFavorites: List<Int>
        get() = BpmFavorites.decode(prefs.getString(KEY_FAVORITES, null))
        set(value) = prefs.edit().putString(KEY_FAVORITES, BpmFavorites.encode(value)).apply()

    var metronomeFlash: Boolean
        get() = prefs.getBoolean(KEY_FLASH, false)
        set(value) = prefs.edit().putBoolean(KEY_FLASH, value).apply()

    var metronomeVibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, false)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

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
            trainer = SpeedTrainer(
                enabled = prefs.getBoolean(KEY_TRAINER_ON, false),
                stepBpm = prefs.getInt(KEY_TRAINER_STEP, 2),
                everyBars = prefs.getInt(KEY_TRAINER_BARS, 4),
                targetBpm = prefs.getInt(KEY_TRAINER_TARGET, 160),
            ),
        ).normalized()
        set(value) = prefs.edit()
            .putInt(KEY_BPM, value.bpm)
            .putInt(KEY_BEATS, value.beatsPerBar)
            .putString(KEY_SUBDIVISION, value.subdivision.name)
            .putBoolean(KEY_ACCENT, value.accentFirst)
            .putBoolean(KEY_TRAINER_ON, value.trainer.enabled)
            .putInt(KEY_TRAINER_STEP, value.trainer.stepBpm)
            .putInt(KEY_TRAINER_BARS, value.trainer.everyBars)
            .putInt(KEY_TRAINER_TARGET, value.trainer.targetBpm)
            .apply()

    private companion object {
        const val KEY_A4 = "a4"
        const val KEY_TUNING = "tuning"
        const val KEY_CUSTOM_TUNINGS = "custom_tunings_v1"
        const val KEY_USE_FLATS = "use_flats"
        const val KEY_HAPTICS = "haptics"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_DETECTION_MODE = "detection_mode"
        const val KEY_BPM = "metronome_bpm"
        const val KEY_BEATS = "metronome_beats"
        const val KEY_SUBDIVISION = "metronome_subdivision"
        const val KEY_ACCENT = "metronome_accent"
        const val KEY_TRAINER_ON = "trainer_enabled"
        const val KEY_TRAINER_STEP = "trainer_step"
        const val KEY_TRAINER_BARS = "trainer_bars"
        const val KEY_TRAINER_TARGET = "trainer_target"
        const val KEY_FAVORITES = "metronome_favorites"
        const val KEY_FLASH = "metronome_flash"
        const val KEY_VIBRATE = "metronome_vibrate"
        const val KEY_CHIME = "chime"
        const val KEY_TOLERANCE = "tolerance"
    }
}
