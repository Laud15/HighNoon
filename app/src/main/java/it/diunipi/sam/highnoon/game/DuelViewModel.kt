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

    // Application context, safe to keep in the ViewModel (it lives as much as the app).
    private val appContext = application.applicationContext

    // The ViewModel has western music.
    private val westernMusic = WesternMusic(appContext)
    private val soundEffects = SoundEffects(appContext)

    // --- SENSOR ---
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // --- STATE ---
    var phase by mutableStateOf(DuelPhase.IDLE)
        private set

    var reactionMs by mutableLongStateOf(0L)
        private set

    var falseStart by mutableStateOf(false)
        private set

    private var signalTime = 0L
    private var musicFinished = false
    private var countdownJob: Job? = null

    init {
       // We connect the callback at the end of the music: the western notifies the ViewModel.
        westernMusic.onFinished = {
            musicFinished = true
        }
    }

    // --- EVENTS / LOGIC ---

    // The listener: interprets readings based on the phase (the logic that before it was in DuelScreen,
    // now it's here where it belongs).
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

    // Called by the UI when the screen becomes visible.
    fun startListening() {
        if (linearAccelSensor != null) {
            sensorManager.registerListener(
                sensorListener,
                linearAccelSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    // Called by the UI when the screen is no longer visible.
    fun stopListening() {
        sensorManager.unregisterListener(sensorListener)
    }


    fun startDuel() {
        falseStart = false
        musicFinished = false
        westernMusic.play()
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
            soundEffects.playCheater()
            falseStart = true
            phase = DuelPhase.RESULT
        }
    }

    fun onValidDraw() {
        if (phase == DuelPhase.DRAW) {
            reactionMs = SystemClock.elapsedRealtime() - signalTime
            soundEffects.playGunshot()
            phase = DuelPhase.RESULT
        }
    }

    fun reset() {
        phase = DuelPhase.IDLE
    }

    // Cleanup: When the ViewModel is destroyed, it releases the music.
    override fun onCleared() {
        super.onCleared()
        stopListening()          // Security: Deregisters the sensor
        westernMusic.stop()
        soundEffects.release()
    }
}