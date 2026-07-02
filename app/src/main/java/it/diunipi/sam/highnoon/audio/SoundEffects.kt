package it.diunipi.sam.highnoon.audio

import it.diunipi.sam.highnoon.R
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

// Possiede il SoundPool e gli effetti brevi (sparo, cheater).
// Carica i suoni UNA volta e li riproduce senza creare nuovi oggetti.
class SoundEffects(context: Context) {

    // Configurazione del tipo di audio: sono suoni di gioco/UI.
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)   // quanti suoni possono sovrapporsi al massimo
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Gli ID dei suoni caricati. -1 = non ancora caricato.
    private var gunshotId: Int = -1
    private var cheaterId: Int = -1

    // Teniamo traccia di quali suoni sono pronti (caricamento asincrono).
    private val readySounds = mutableSetOf<Int>()

    init {
        // Listener: chiamato quando un suono finisce di caricare.
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {          // 0 = caricamento riuscito
                readySounds.add(sampleId)
            }
        }
        // Avvia il caricamento (asincrono) dei due effetti.
        gunshotId = soundPool.load(context, R.raw.gunshot, 1)
        cheaterId = soundPool.load(context, R.raw.cheater, 1)
    }

    fun playGunshot() {
        playIfReady(gunshotId)
    }

    fun playCheater() {
        playIfReady(cheaterId)
    }

    // Suona un suono solo se e' gia' stato caricato.
    private fun playIfReady(soundId: Int) {
        if (soundId != -1 && readySounds.contains(soundId)) {
            // play(id, volSinistro, volDestro, priorita', loop, velocita')
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    // Rilascia le risorse quando non servono piu'.
    fun release() {
        soundPool.release()
    }
}