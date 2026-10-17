package com.guitarmagic.tuner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.guitarmagic.tuner.data.SettingsRepository
import com.guitarmagic.tuner.metronome.BpmFavorites
import com.guitarmagic.tuner.metronome.MetronomeConfig
import com.guitarmagic.tuner.metronome.MetronomeEngine
import com.guitarmagic.tuner.metronome.SpeedTrainer
import com.guitarmagic.tuner.metronome.Subdivision
import com.guitarmagic.tuner.metronome.TapTempo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MetronomeUiState(
    val config: MetronomeConfig = MetronomeConfig(),
    val running: Boolean = false,
    /** 收藏的常用速度 */
    val favorites: List<Int> = emptyList(),
    /** 每拍闪屏提示 */
    val flash: Boolean = false,
    /** 每拍振动 */
    val vibrate: Boolean = false,
    /** 速度训练开始时的速度（训练中显示进度用；停止后恢复到这个速度） */
    val trainingFromBpm: Int? = null,
)

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val engine = MetronomeEngine()
    private val tapTempo = TapTempo()

    private val _state = MutableStateFlow(
        MetronomeUiState(
            config = settings.metronomeConfig,
            favorites = settings.metronomeFavorites,
            flash = settings.metronomeFlash,
            vibrate = settings.metronomeVibrate,
        ),
    )
    val state: StateFlow<MetronomeUiState> = _state.asStateFlow()

    init {
        // 速度训练自动加速时同步到界面（不写入设置：停止后恢复为开始时的速度）
        engine.onTempoChanged = { bpm -> _state.update { s -> s.copy(config = s.config.copy(bpm = bpm)) } }
    }

    /** 当前播放位置（用于动画），未播放时为 null。 */
    fun visual(): MetronomeEngine.Visual? = engine.visual()

    fun toggle() = if (_state.value.running) stop() else start()

    fun start() {
        val config = _state.value.config
        engine.start(config)
        _state.update {
            it.copy(running = true, trainingFromBpm = if (config.trainer.enabled) config.bpm else null)
        }
    }

    fun stop() {
        engine.stop()
        tapTempo.reset()
        val from = _state.value.trainingFromBpm
        _state.update { s ->
            val restored = if (from != null) s.config.copy(bpm = from) else s.config
            s.copy(running = false, trainingFromBpm = null, config = restored)
        }
        if (from != null) settings.metronomeConfig = _state.value.config
    }

    fun setBpm(bpm: Int) = updateConfig { it.copy(bpm = bpm) }

    fun changeBpm(delta: Int) = updateConfig { it.copy(bpm = it.bpm + delta) }

    fun setBeatsPerBar(beats: Int) = updateConfig { it.copy(beatsPerBar = beats) }

    fun setSubdivision(subdivision: Subdivision) = updateConfig { it.copy(subdivision = subdivision) }

    fun setAccentFirst(accent: Boolean) = updateConfig { it.copy(accentFirst = accent) }

    fun tap() {
        tapTempo.tap(SystemClock.elapsedRealtime())?.let { setBpm(it) }
    }

    // ---------- 速度训练 ----------

    fun setTrainerEnabled(enabled: Boolean) = updateTrainer { it.copy(enabled = enabled) }

    fun changeTrainerStep(delta: Int) = updateTrainer { it.copy(stepBpm = it.stepBpm + delta) }

    fun changeTrainerBars(delta: Int) = updateTrainer { it.copy(everyBars = it.everyBars + delta) }

    fun changeTrainerTarget(delta: Int) = updateTrainer { it.copy(targetBpm = it.targetBpm + delta) }

    private fun updateTrainer(transform: (SpeedTrainer) -> SpeedTrainer) =
        updateConfig { it.copy(trainer = transform(it.trainer)) }

    // ---------- 收藏 ----------

    fun addFavorite() {
        val list = BpmFavorites.add(_state.value.favorites, _state.value.config.bpm)
        settings.metronomeFavorites = list
        _state.update { it.copy(favorites = list) }
    }

    fun removeFavorite(bpm: Int) {
        val list = BpmFavorites.remove(_state.value.favorites, bpm)
        settings.metronomeFavorites = list
        _state.update { it.copy(favorites = list) }
    }

    // ---------- 提示方式 ----------

    fun setFlash(enabled: Boolean) {
        settings.metronomeFlash = enabled
        _state.update { it.copy(flash = enabled) }
    }

    fun setVibrate(enabled: Boolean) {
        settings.metronomeVibrate = enabled
        _state.update { it.copy(vibrate = enabled) }
    }

    private fun updateConfig(transform: (MetronomeConfig) -> MetronomeConfig) {
        val old = _state.value.config
        val new = transform(old).normalized()
        if (new == old) return
        _state.update { it.copy(config = new) }
        // 训练进行中不保存被自动加快的速度
        settings.metronomeConfig = _state.value.trainingFromBpm?.let { new.copy(bpm = it) } ?: new
        engine.updateConfig(new)
    }

    override fun onCleared() {
        engine.stop()
    }
}
