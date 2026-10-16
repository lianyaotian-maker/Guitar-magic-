package com.guitarmagic.tuner.data

import android.content.Context
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.Tuning

/** 本地保存的设置（基准音、调弦方式、是否已申请过权限）。 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("tuner_settings", Context.MODE_PRIVATE)

    var a4: Double
        get() = prefs.getFloat(KEY_A4, MusicTheory.DEFAULT_A4.toFloat()).toDouble()
            .coerceIn(MusicTheory.MIN_A4, MusicTheory.MAX_A4)
        set(value) = prefs.edit().putFloat(KEY_A4, value.toFloat()).apply()

    var tuning: Tuning
        get() = Tuning.byId(prefs.getString(KEY_TUNING, null))
        set(value) = prefs.edit().putString(KEY_TUNING, value.id).apply()

    var permissionRequested: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, value).apply()

    private companion object {
        const val KEY_A4 = "a4"
        const val KEY_TUNING = "tuning"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }
}
