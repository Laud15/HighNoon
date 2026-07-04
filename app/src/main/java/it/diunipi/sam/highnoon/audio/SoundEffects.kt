package it.diunipi.sam.highnoon.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import it.diunipi.sam.highnoon.R

// Has SoundPool and short effects (shot, cheater).
// It loads sounds ONCE and plays them without creating new objects.
class SoundEffects(context: Context) {

    // Audio Type Configuration: These are game/UI sounds.
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)   // how many sounds can overlap at most
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    //The IDs of the uploaded sounds. -1 = not yet loaded.
    private var gunshotId: Int = -1
    private var cheaterId: Int = -1

    // We keep track of which sounds are ready (asynchronous loading).
    private val readySounds = mutableSetOf<Int>()

    init {
        // Listener: Called when a sound finishes loading.
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {          // 0 = Successful upload
                readySounds.add(sampleId)
            }
        }
        // Starts loading (asynchronously) the two effects.
        gunshotId = soundPool.load(context, R.raw.gunshot, 1)
        cheaterId = soundPool.load(context, R.raw.cheater, 1)
    }

    fun playGunshot() {
        playIfReady(gunshotId)
    }

    fun playCheater() {
        playIfReady(cheaterId)
    }

    // Play a sound only if it has already been loaded.
    private fun playIfReady(soundId: Int) {
        if (soundId != -1 && readySounds.contains(soundId)) {
            // play(id, volSinistro, volDestro, priorita', loop, velocita')
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    // Release resources when they are no longer needed.
    fun release() {
        soundPool.release()
    }
}