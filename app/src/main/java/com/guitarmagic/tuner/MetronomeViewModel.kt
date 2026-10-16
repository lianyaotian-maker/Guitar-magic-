package com.guitarmagic.tuner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.guitarmagic.tuner.data.SettingsRepository
import com.guitarmagic.tuner.metronome.MetronomeConfig
import com.guitarmagic.tuner.metronome.MetronomeEngine
import com.guitarmagic.tuner.metronome.Subdivision
import com.guitarmagic.tuner.metronome.TapTempo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MetronomeUiState(
    val config: MetronomeConfig = MetronomeConfig(),
    val running: Boolean = false,
)

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val engine = MetronomeEngine()
    private val tapTempo = TapTempo()

    private val _state = MutableStateFlow(MetronomeUiState(config = settings.metronomeConfig))
    val state: StateFlow<MetronomeUiState> = _state.asStateFlow()

    /** 当前播放位置（用于动画），未播放时为 null。 */
    fun visual(): MetronomeEngine.Visual? = engine.visual()

    fun toggle() = if (_state.value.running) stop() else start()

    fun start() {
        engine.start(_state.value.config)
        _state.update { it.copy(running = true) }
    }

    fun stop() {
        engine.stop()
        tapTempo.reset()
        _state.update { it.copy(running = false) }
    }

    fun setBpm(bpm: Int) = updateConfig { it.copy(bpm = bpm) }

    fun changeBpm(delta: Int) = updateConfig { it.copy(bpm = it.bpm + delta) }

    fun setBeatsPerBar(beats: Int) = updateConfig { it.copy(beatsPerBar = beats) }

    fun setSubdivision(subdivision: Subdivision) = updateConfig { it.copy(subdivision = subdivision) }

    fun setAccentFirst(accent: Boolean) = updateConfig { it.copy(accentFirst = accent) }

    fun tap() {
        tapTempo.tap(SystemClock.elapsedRealtime())?.let { setBpm(it) }
    }

    private fun updateConfig(transform: (MetronomeConfig) -> MetronomeConfig) {
        val old = _state.value.config
        val new = transform(old).normalized()
        if (new == old) return
        _state.update { it.copy(config = new) }
        settings.metronomeConfig = new
        engine.updateConfig(new)
    }

    override fun onCleared() {
        engine.stop()
    }
}
