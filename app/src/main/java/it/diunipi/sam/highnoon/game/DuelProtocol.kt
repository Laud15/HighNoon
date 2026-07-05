package it.diunipi.sam.highnoon.game

// Shared "language" the two phones speak over the socket. The transport (SocketConnection)
// is a dumb pipe; it knows nothing of these messages
object DuelProtocol {
    // Control, host -> client
    const val WAIT = "WAIT"
    const val GO = "GO"

    // Pre-duel challenge handshake
    private const val CHALLENGE = "CHALLENGE:"   // CHALLENGE:<nick>
    private const val ACCEPT = "ACCEPT:"          // ACCEPT:<nick>
    const val DECLINE = "DECLINE"

    // Player result, client -> host
    private const val TIME = "TIME:"
    const val FALSE_START = "FALSE_START"

    // Verdict, host -> client
    private const val VERDICT = "VERDICT:"

    // Winner's photos, host<->client (either direction), base64-encoded JPEG
    private const val SELFIE = "SELFIE:"
    private const val PHOTO = "PHOTO:"

    const val READY = "READY"

    fun challenge(nick: String) = CHALLENGE + nick
    fun parseChallenge(msg: String) = if (msg.startsWith(CHALLENGE)) msg.removePrefix(CHALLENGE) else null

    fun accept(nick: String) = ACCEPT + nick
    fun parseAccept(msg: String) = if (msg.startsWith(ACCEPT)) msg.removePrefix(ACCEPT) else null

    fun time(ms: Long) = TIME + ms
    fun parseTime(msg: String) = if (msg.startsWith(TIME)) msg.removePrefix(TIME).toLongOrNull() else null

    fun verdict(outcome: Outcome) = VERDICT + outcome.name
    fun parseVerdict(msg: String) =
        if (msg.startsWith(VERDICT)) runCatching { Outcome.valueOf(msg.removePrefix(VERDICT)) }.getOrNull() else null


    fun selfie(data: String) = SELFIE + data
    fun parseSelfie(msg: String) = if (msg.startsWith(SELFIE)) msg.removePrefix(SELFIE) else null

    fun photo(data: String) = PHOTO + data
    fun parsePhoto(msg: String) = if (msg.startsWith(PHOTO)) msg.removePrefix(PHOTO) else null
}