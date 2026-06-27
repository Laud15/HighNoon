package it.diunipi.sam.highnoon

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private const val DRAW_THRESHOLD = 12f

@Composable
fun DuelScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var phase by remember { mutableStateOf(DuelPhase.IDLE) }
    var reactionMs by remember { mutableLongStateOf(0L) }
    var signalTime by remember { mutableLongStateOf(0L) }
    var falseStart by remember { mutableStateOf(false) }
    var musicFinished by remember { mutableStateOf(false) }

    // Oggetto che possiede e gestisce la musica western (vedi Sounds.kt).
    // Creato una volta sola e ricordato tra le ricomposizioni.
    val westernMusic = remember { WesternMusic(context) }

    LaunchedEffect(Unit) {
        createSignalChannel(context)
        // Colleghiamo: quando la western finisce, segna musicFinished = true.
        westernMusic.onFinished = {
            musicFinished = true
        }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val y = event.values[1]
                val currentPhase: DuelPhase = phase
                when (currentPhase) {
                    DuelPhase.WAITING -> {
                        if (abs(y) > DRAW_THRESHOLD) {
                            falseStart = true
                            phase = DuelPhase.RESULT
                        }
                    }
                    DuelPhase.DRAW -> {
                        if (y > DRAW_THRESHOLD) {
                            reactionMs = SystemClock.elapsedRealtime() - signalTime
                            playGunshot(context)
                            phase = DuelPhase.RESULT
                        }
                    }
                    else -> {
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }
        }

        if (sensor != null) {
            sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Pulizia della musica quando la schermata sparisce.
    DisposableEffect(Unit) {
        onDispose {
            westernMusic.stop()
        }
    }

    LaunchedEffect(phase) {
        if (phase == DuelPhase.WAITING) {
            // 1. Aspetta che la musica western sia davvero finita.
            while (!musicFinished) {
                delay(50)
            }
            // 2. Attesa casuale aggiuntiva: il via resta imprevedibile.
            val extraRandomMs = Random.nextLong(1000, 4000)
            delay(extraRandomMs)
            // 3. Se siamo ancora in attesa (niente falsa partenza), dai il via.
            if (phase == DuelPhase.WAITING) {
                signalTime = SystemClock.elapsedRealtime()
                fireSignal(context)
                phase = DuelPhase.DRAW
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (phase) {
            DuelPhase.IDLE -> {
                Text(text = "Pronto al duello?", fontSize = 24.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    falseStart = false
                    musicFinished = false
                    westernMusic.play()      // suona la western — la classe fa il resto
                    phase = DuelPhase.WAITING
                }) {
                    Text(text = "Start")
                }
            }
            DuelPhase.WAITING -> {
                Text(text = "Tieni fermo...", fontSize = 24.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estrai SOLO al segnale!",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DuelPhase.DRAW -> {
                Text(
                    text = "DRAW!",
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            DuelPhase.RESULT -> {
                if (falseStart) {
                    Text(
                        text = "Falsa partenza!",
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(text = "Tempo di reazione", fontSize = 20.sp)
                    Text(text = "$reactionMs ms", fontSize = 40.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { phase = DuelPhase.IDLE }) {
                    Text(text = "Rigioca")
                }
            }
        }
    }
}