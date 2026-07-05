package it.diunipi.sam.highnoon

// Single place for the tunable constants that were scattered across the code:
// network, duel timing, photo settings.
object Config {

    object Network {
        const val PORT = 8988                        // TCP port for the duel socket
        const val SOCKET_CONNECT_TIMEOUT_MS = 5000   // client connect() timeout (Int, ms)
        const val CONNECT_RETRIES = 5                // client attempts before giving up
        const val RETRY_DELAY_MS = 1000L             // pause between client attempts
    }

    object Duel {
        const val DRAW_THRESHOLD = 12f               // linear-accel magnitude for a "draw"
        const val COUNTDOWN_MIN_MS = 1000L           // random wait before the signal: lower bound
        const val COUNTDOWN_MAX_MS = 4000L           // ... upper bound
        const val CONNECTION_TIMEOUT_MS = 25_000L    // give up "Connecting…" after this
        const val DECLINE_FLUSH_MS = 250L            // let DECLINE flush before closing the socket
        const val RESUME_SEARCH_DELAY_MS = 1200L     // wait after removeGroup before re-searching
        const val DISCOVERY_RESTART_DELAY_MS = 3000L // debounce before relaunching discovery
        const val COUNTDOWN_POLL_MS = 50L            // how often the countdown re-checks the music/phase
    }

    object Photo {
        const val MAX_SIDE = 720                   // longest edge after downscaling
        const val JPEG_QUALITY = 60                  // JPEG compression quality
        const val SELFIE_FILE = "winner_selfie.jpg"
        const val PHOTO_FILE = "winner_photo.jpg"
    }
}