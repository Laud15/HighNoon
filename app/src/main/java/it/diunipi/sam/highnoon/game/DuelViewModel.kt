package it.diunipi.sam.highnoon.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.diunipi.sam.highnoon.audio.SoundEffects
import it.diunipi.sam.highnoon.audio.WesternMusic
import it.diunipi.sam.highnoon.notification.fireSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val DRAW_THRESHOLD = 12f
class DuelViewModel(application: Application) : AndroidViewModel(application) {

    // Context applicativo, sicuro da tenere nel ViewModel (vive quanto l'app).
    private val appContext = application.applicationContext

    // Il ViewModel possiede la musica western (prima stava nella UI).
    private val westernMusic = WesternMusic(appContext)
    private val soundEffects = SoundEffects(appContext)

    // --- SENSORE ---
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // --- STATO ---
    var phase by mutableStateOf(DuelPhase.IDLE)
        private set

    var reactionMs by mutableLongStateOf(0L)
        private set

    var falseStart by mutableStateOf(false)
        private set

    private var signalTime = 0L
    private var musicFinished = false      // ora e' interno, non piu' letto dalla UI
    private var countdownJob: Job? = null

    init {
        // Colleghiamo la callback di fine musica: la western avvisa il ViewModel.
        westernMusic.onFinished = {
            musicFinished = true
        }
    }

    // --- EVENTI / LOGICA ---

    // Il listener: interpreta le letture in base alla fase (la logica che
    // prima stava in DuelScreen, ora e' qui dove appartiene).
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val y = event.values[1]
            when (phase) {
                DuelPhase.WAITING -> {
                    if (abs(y) > DRAW_THRESHOLD) {
                        onEarlyMovement()
                    }
                }
                DuelPhase.DRAW -> {
                    if (abs(y) > DRAW_THRESHOLD) {
                        onValidDraw()
                    }
                }
                else -> {}
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Chiamato dalla UI quando la schermata diventa visibile.
    fun startListening() {
        if (linearAccelSensor != null) {
            sensorManager.registerListener(
                sensorListener,
                linearAccelSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    // Chiamato dalla UI quando la schermata non e' piu' visibile.
    fun stopListening() {
        sensorManager.unregisterListener(sensorListener)
    }


    fun startDuel() {
        falseStart = false
        musicFinished = false
        westernMusic.play()                // il ViewModel suona la western
        phase = DuelPhase.WAITING
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (!musicFinished && phase == DuelPhase.WAITING) {
                delay(50)
            }
            if (phase != DuelPhase.WAITING) return@launch
            val extraRandomMs = Random.nextLong(1000, 4000)
            delay(extraRandomMs)
            if (phase == DuelPhase.WAITING) {
                signalTime = SystemClock.elapsedRealtime()
                fireSignal(appContext)
                phase = DuelPhase.DRAW
            }
        }
    }

    fun onEarlyMovement() {
        if (phase == DuelPhase.WAITING) {
            westernMusic.stop()
            soundEffects.playCheater()      // era: playCheater(appContext)
            falseStart = true
            phase = DuelPhase.RESULT
        }
    }

    fun onValidDraw() {
        if (phase == DuelPhase.DRAW) {
            reactionMs = SystemClock.elapsedRealtime() - signalTime
            soundEffects.playGunshot()      // era: playGunshot(appContext)
            phase = DuelPhase.RESULT
        }
    }

    fun reset() {
        phase = DuelPhase.IDLE
    }

    // Pulizia: quando il ViewModel viene distrutto, rilascia la musica.
    override fun onCleared() {
        super.onCleared()
        stopListening()          // sicurezza: deregistra il sensore
        westernMusic.stop()
        soundEffects.release()
    }
}