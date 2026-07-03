package it.diunipi.sam.highnoon.audio

import android.content.Context
import android.media.MediaPlayer
import it.diunipi.sam.highnoon.R

//--- Dedicated management of western music ---
// A class that "owns" the Western MediaPlayer and controls it.
// All the complexity (creating, releasing, avoiding duplicate players) is in here.
class WesternMusic(private val context: Context) {
    private var player: MediaPlayer? = null

    // Optional callback: Called when the music ends.
    var onFinished: (() -> Unit)? = null

    fun play() {
       // If there was a previous player,
        // we stop it and release: guarantees ONLY ONE player at a time -> no race conditions.
        stop()

        val mp = MediaPlayer.create(context, R.raw.western_start)
        mp.setOnCompletionListener {
            it.release()
            player = null
            onFinished?.invoke()   // notifies, if anyone is interested
        }
        player = mp
        mp.start()
    }

    fun stop() {
        player?.release()
        player = null
    }
}