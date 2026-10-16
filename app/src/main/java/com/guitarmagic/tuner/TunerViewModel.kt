package com.guitarmagic.tuner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guitarmagic.tuner.audio.AudioPitchEngine
import com.guitarmagic.tuner.data.SettingsRepository
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.NoteInfo
import com.guitarmagic.tuner.pitch.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt

data class TunerUiState(
    val a4: Double = MusicTheory.DEFAULT_A4,
    val tuning: Tuning = Tuning.STANDARD,
    /** 手动锁定的弦（0 = 6 弦），null 表示自动识别 */
    val lockedString: Int? = null,
    /** 平滑后的实时频率，null 表示没有声音 */
    val frequency: Double? = null,
    /** 当前显示的是信号消失后保留的读数 */
    val holding: Boolean = false,
    val levelDb: Double = -120.0,
    val listening: Boolean = false,
    val error: String? = null,
)

/** 界面需要显示的全部数值，由 [TunerUiState] 推导。 */
data class TunerDisplay(
    val frequency: Double? = null,
    /** 最近的标准音 */
    val note: NoteInfo? = null,
    /** 指针偏差（音分）：自动模式下相对最近标准音，锁定模式下相对目标弦 */
    val cents: Double? = null,
    /** 指针所参照的标准音及其理论频率 */
    val referenceLabel: String? = null,
    val referenceFrequency: Double? = null,
    /** 高亮的弦 */
    val activeString: Int? = null,
    /** 高亮弦的目标音与偏差，用于“请调紧/调松”提示 */
    val stringLabel: String? = null,
    val stringCents: Double? = null,
) {
    val inTune: Boolean get() = cents != null && abs(cents) <= IN_TUNE_CENTS

    companion object {
        const val IN_TUNE_CENTS = 5.0
    }
}

fun TunerUiState.toDisplay(): TunerDisplay {
    val f = frequency ?: return TunerDisplay(activeString = lockedString)
    val note = MusicTheory.nearestNote(f, a4)
    val stringIndex = lockedString ?: tuning.closestString(f, a4)
    val stringMidi = tuning.strings[stringIndex]
    val stringTarget = MusicTheory.midiToFrequency(stringMidi, a4)
    val stringCents = MusicTheory.cents(f, stringTarget)
    val stringLabel = "${6 - stringIndex}弦 ${MusicTheory.label(stringMidi)}"
    return if (lockedString != null) {
        TunerDisplay(
            frequency = f,
            note = note,
            cents = stringCents,
            referenceLabel = MusicTheory.label(stringMidi),
            referenceFrequency = stringTarget,
            activeString = stringIndex,
            stringLabel = stringLabel,
            stringCents = stringCents,
        )
    } else {
        TunerDisplay(
            frequency = f,
            note = note,
            cents = note.cents,
            referenceLabel = note.label,
            referenceFrequency = note.frequency,
            activeString = stringIndex,
            stringLabel = stringLabel,
            stringCents = stringCents,
        )
    }
}

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val engine = AudioPitchEngine(application)

    private val _state = MutableStateFlow(TunerUiState(a4 = settings.a4, tuning = settings.tuning))
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    var permissionRequested: Boolean
        get() = settings.permissionRequested
        set(value) {
            settings.permissionRequested = value
        }

    fun startListening() {
        if (engine.isRunning) return
        _state.update { it.copy(listening = true, error = null) }
        engine.start(viewModelScope) { event ->
            when (event) {
                is AudioPitchEngine.Event.Pitch -> _state.update {
                    it.copy(
                        frequency = event.output.frequency,
                        holding = event.output.holding,
                        levelDb = event.output.levelDb,
                    )
                }
                is AudioPitchEngine.Event.Error -> _state.update {
                    it.copy(error = event.message, listening = false, frequency = null)
                }
            }
        }
    }

    fun stopListening() {
        engine.stop()
        _state.update { it.copy(listening = false, frequency = null, holding = false, levelDb = -120.0) }
    }

    fun retry() {
        stopListening()
        startListening()
    }

    fun setA4(value: Double) {
        val v = value.roundToInt().toDouble().coerceIn(MusicTheory.MIN_A4, MusicTheory.MAX_A4)
        settings.a4 = v
        _state.update { it.copy(a4 = v) }
    }

    fun setTuning(tuning: Tuning) {
        settings.tuning = tuning
        _state.update { it.copy(tuning = tuning, lockedString = null) }
    }

    /** 点击某根弦：锁定；再次点击同一根弦：恢复自动识别。 */
    fun toggleLock(index: Int) {
        _state.update { it.copy(lockedString = if (it.lockedString == index) null else index) }
    }

    override fun onCleared() {
        engine.stop()
    }
}
