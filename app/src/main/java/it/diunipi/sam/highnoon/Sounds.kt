package it.diunipi.sam.highnoon

import android.content.Context
import android.media.MediaPlayer

// --- Suoni "spara e dimentica" (per lo sparo): crea, suona, si auto-rilascia ---
fun playSoundRes(context: Context, resId: Int) {
    val mediaPlayer = MediaPlayer.create(context, resId)
    mediaPlayer.setOnCompletionListener { mp -> mp.release() }
    mediaPlayer.start()
}

fun playGunshot(context: Context) {
    playSoundRes(context, R.raw.gunshot)
}

// --- Gestione dedicata della musica western ---
// Una classe che "possiede" il MediaPlayer della western e lo controlla.
// Tutta la complessita' (creare, rilasciare, evitare player doppi) sta qui dentro.
class WesternMusic(private val context: Context) {
    private var player: MediaPlayer? = null

    // Callback opzionale: viene chiamata quando la musica finisce.
    var onFinished: (() -> Unit)? = null

    fun play() {
        // Se c'era un player precedente, lo fermiamo e rilasciamo:
        // garantisce UN SOLO player alla volta -> niente race condition.
        stop()

        val mp = MediaPlayer.create(context, R.raw.western_start)
        mp.setOnCompletionListener {
            it.release()
            player = null
            onFinished?.invoke()    // avvisa, se qualcuno e' interessato
        }
        player = mp
        mp.start()
    }

    fun stop() {
        player?.release()
        player = null
    }
}