package it.diunipi.sam.highnoon.audio

import android.content.Context
import android.media.MediaPlayer

// Owns ONE MediaPlayer for a GIVEN raw track and controls its whole life cycle
// (create, release, avoid double players). Generalized from the old WesternMusic so the
// same clean "one owner" logic serves the western intro AND the victory/defeat tracks
// (no duplication / separation of concerns, Lez. 11 + 20).
class MusicPlayer(private val context: Context, private val resId: Int) {
    private var player: MediaPlayer? = null

    // Optional: fired when the track finishes on its own (used by the western countdown).
    var onFinished: (() -> Unit)? = null

    fun play() {
        stop()   // one player at a time -> no race
        val mp = MediaPlayer.create(context, resId) ?: return   // null if the raw file is missing
        mp.setOnCompletionListener {
            it.release()
            player = null
            onFinished?.invoke()
        }
        player = mp
        mp.start()
    }

    fun stop() {
        player?.release()
        player = null
    }
}