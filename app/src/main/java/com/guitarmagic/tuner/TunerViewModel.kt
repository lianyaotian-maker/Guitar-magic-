package com.guitarmagic.tuner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guitarmagic.tuner.audio.AudioPitchEngine
import com.guitarmagic.tuner.core.CustomTunings
import com.guitarmagic.tuner.core.TuneProgress
import com.guitarmagic.tuner.data.SettingsRepository
import com.guitarmagic.tuner.pitch.DetectionMode
import com.guitarmagic.tuner.pitch.DetectionRange
import com.guitarmagic.tuner.pitch.MusicTheory
import com.guitarmagic.tuner.pitch.NoteInfo
import com.guitarmagic.tuner.pitch.Sensitivity
import com.guitarmagic.tuner.pitch.Tuning
import com.guitarmagic.tuner.tone.ReferenceTonePlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class TunerUiState(
    val a4: Double = MusicTheory.DEFAULT_A4,
    val tuning: Tuning = Tuning.STANDARD,
    val customTunings: List<Tuning> = emptyList(),
    /** 手动锁定的弦（0 = 6 弦），null 表示自动识别 */
    val lockedString: Int? = null,
    /** 平滑后的实时频率，null 表示没有声音 */
    val frequency: Double? = null,
    /** 当前显示的是信号消失后保留的读数 */
    val holding: Boolean = false,
    val levelDb: Double = -120.0,
    val listening: Boolean = false,
    val error: String? = null,
    val useFlats: Boolean = false,
    val hapticsEnabled: Boolean = true,
    /** 本次已调准的弦 */
    val tunedStrings: Set<Int> = emptySet(),
    /** 正在播放参考音的音高（MIDI），播放期间暂停识别；null 表示没有播放 */
    val playingToneMidi: Int? = null,
    /** 节拍器正在运行（此时暂停识别，避免把滴答声当成音高） */
    val metronomeRunning: Boolean = false,
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val detectionMode: DetectionMode = DetectionMode.GUITAR,
) {
    /** 识别是否被暂停 */
    val paused: Boolean get() = playingToneMidi != null || metronomeRunning

    val allTuned: Boolean get() = tunedStrings.size == Tuning.STRING_COUNT
}

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
    val note = MusicTheory.nearestNote(f, a4, useFlats)
    val stringIndex = lockedString ?: tuning.closestString(f, a4)
    val stringMidi = tuning.strings[stringIndex]
    val stringTarget = MusicTheory.midiToFrequency(stringMidi, a4)
    val stringCents = MusicTheory.cents(f, stringTarget)
    val stringLabel = "${Tuning.STRING_COUNT - stringIndex}弦 ${MusicTheory.label(stringMidi, useFlats)}"
    return if (lockedString != null) {
        TunerDisplay(
            frequency = f,
            note = note,
            cents = stringCents,
            referenceLabel = MusicTheory.label(stringMidi, useFlats),
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
    private val tonePlayer = ReferenceTonePlayer()
    private val progress = TuneProgress()

    private val _state = MutableStateFlow(
        settings.customTunings.let { custom ->
            TunerUiState(
                a4 = settings.a4,
                tuning = Tuning.byId(settings.selectedTuningId, custom),
                customTunings = custom,
                useFlats = settings.useFlats,
                hapticsEnabled = settings.hapticsEnabled,
                sensitivity = settings.sensitivity,
                detectionMode = settings.detectionMode,
            )
        },
    )
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    init {
        // 调弦、锁定弦、基准音、识别模式或灵敏度变化时，同步更新识别引擎的音域与触发门限
        syncEngine(_state.value)
        viewModelScope.launch {
            _state
                .map { DetectionRange.of(it.tuning, it.a4, it.lockedString, it.detectionMode) to it.sensitivity }
                .distinctUntilChanged()
                .collect { (range, sensitivity) ->
                    engine.detectionRange = range
                    engine.sensitivity = sensitivity
                }
        }
    }

    private fun syncEngine(s: TunerUiState) {
        engine.detectionRange = DetectionRange.of(s.tuning, s.a4, s.lockedString, s.detectionMode)
        engine.sensitivity = s.sensitivity
    }

    fun setSensitivity(value: Sensitivity) {
        settings.sensitivity = value
        _state.update { it.copy(sensitivity = value) }
        syncEngine(_state.value)
    }

    fun setDetectionMode(value: DetectionMode) {
        settings.detectionMode = value
        _state.update { it.copy(detectionMode = value) }
        syncEngine(_state.value)
    }

    private val _stringTuned = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** 某根弦刚刚调准（用于振动反馈）。 */
    val stringTuned: SharedFlow<Int> = _stringTuned.asSharedFlow()

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
                is AudioPitchEngine.Event.Pitch -> onPitch(event)
                is AudioPitchEngine.Event.Error -> _state.update {
                    it.copy(error = event.message, listening = false, frequency = null)
                }
            }
        }
    }

    private fun onPitch(event: AudioPitchEngine.Event.Pitch) {
        val updated = _state.updateAndGet {
            it.copy(
                frequency = event.output.frequency,
                holding = event.output.holding,
                levelDb = event.output.levelDb,
            )
        }
        val display = updated.toDisplay()
        val fresh = !updated.holding && updated.frequency != null
        val newlyTuned = synchronized(progress) {
            val result = progress.update(
                if (fresh) display.activeString else null,
                if (fresh) display.stringCents else null,
            )
            if (result != null) progress.tunedStrings else null
        }
        if (newlyTuned != null) {
            _state.update { it.copy(tunedStrings = newlyTuned) }
            _stringTuned.tryEmit(newlyTuned.size)
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
        resetProgress()
    }

    fun setUseFlats(value: Boolean) {
        settings.useFlats = value
        _state.update { it.copy(useFlats = value) }
    }

    fun setHapticsEnabled(value: Boolean) {
        settings.hapticsEnabled = value
        _state.update { it.copy(hapticsEnabled = value) }
    }

    fun setTuning(tuning: Tuning) {
        settings.selectedTuningId = tuning.id
        _state.update { it.copy(tuning = tuning, lockedString = null) }
        resetProgress()
    }

    /** 点击某根弦：锁定；再次点击同一根弦：恢复自动识别。 */
    fun toggleLock(index: Int) {
        if (index !in 0 until Tuning.STRING_COUNT) return
        _state.update { it.copy(lockedString = if (it.lockedString == index) null else index) }
    }

    fun resetProgress() {
        synchronized(progress) { progress.reset() }
        _state.update { it.copy(tunedStrings = emptySet()) }
    }

    /**
     * 新建或修改自定义调弦。id 为 null 表示新建。
     * @return 保存后的预设；名称为空或音高不合法时返回 null
     */
    fun saveCustomTuning(id: String?, name: String, strings: List<Int>): Tuning? {
        val current = _state.value.customTunings
        if (id == null && current.size >= CustomTunings.MAX_PRESETS) return null
        val finalId = id ?: CustomTunings.newId(current, System.currentTimeMillis())
        val tuning = CustomTunings.build(finalId, name, strings) ?: return null
        val list = if (current.any { it.id == finalId }) {
            current.map { if (it.id == finalId) tuning else it }
        } else {
            current + tuning
        }
        settings.customTunings = list
        _state.update { it.copy(customTunings = list) }
        val selected = _state.value.tuning
        if (id == null || selected.id == finalId) setTuning(tuning)
        return tuning
    }

    fun deleteCustomTuning(id: String) {
        val list = _state.value.customTunings.filterNot { it.id == id }
        settings.customTunings = list
        _state.update { it.copy(customTunings = list) }
        if (_state.value.tuning.id == id) setTuning(Tuning.STANDARD)
    }

    fun defaultCustomName(): String = CustomTunings.defaultName(_state.value.customTunings)

    private var toneJob: Job? = null

    /**
     * 播放参考音。播放期间（再加一点房间余音时间）屏蔽麦克风识别，
     * 避免把手机自己发出的参考音误判为“已调准”。
     */
    fun playTone(midi: Int) {
        engine.suppressFor(ReferenceTonePlayer.TONE_MILLIS + TONE_TAIL_MILLIS)
        tonePlayer.play(MusicTheory.midiToFrequency(midi, _state.value.a4))
        _state.update { it.copy(playingToneMidi = midi, frequency = null, holding = false) }
        toneJob?.cancel()
        toneJob = viewModelScope.launch {
            delay(ReferenceTonePlayer.TONE_MILLIS + TONE_TAIL_MILLIS)
            _state.update { it.copy(playingToneMidi = null) }
        }
    }

    /** 提前停止参考音；只保留很短的余音屏蔽时间。 */
    fun stopTone() {
        tonePlayer.stop()
        toneJob?.cancel()
        toneJob = null
        if (_state.value.playingToneMidi != null) {
            engine.setSuppressionFor(TONE_TAIL_MILLIS)
            _state.update { it.copy(playingToneMidi = null) }
        }
    }

    /** 节拍器运行状态变化时由界面调用。 */
    fun setMetronomeRunning(running: Boolean) {
        engine.externallySuppressed = running
        _state.update {
            if (running) it.copy(metronomeRunning = true, frequency = null, holding = false) else it.copy(metronomeRunning = false)
        }
    }

    override fun onCleared() {
        engine.stop()
        tonePlayer.release()
    }

    private companion object {
        /** 参考音结束后继续屏蔽的时间，等房间里的余音消散 */
        const val TONE_TAIL_MILLIS = 400L
    }
}
