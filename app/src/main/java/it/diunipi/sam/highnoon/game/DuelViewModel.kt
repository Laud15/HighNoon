package it.diunipi.sam.highnoon.game

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Environment
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.diunipi.sam.highnoon.R
import it.diunipi.sam.highnoon.audio.MusicPlayer
import it.diunipi.sam.highnoon.audio.SoundEffects
import it.diunipi.sam.highnoon.network.SocketConnection
import it.diunipi.sam.highnoon.network.WifiDirectConnection
import it.diunipi.sam.highnoon.notification.fireSignal
import it.diunipi.sam.highnoon.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.random.Random


class DuelViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val westernMusic = MusicPlayer(appContext, R.raw.western_start)
    private val victoryMusic = MusicPlayer(appContext, R.raw.victory)
    private val defeatMusic = MusicPlayer(appContext, R.raw.defeat)
    private val soundEffects = SoundEffects(appContext)

    // The VM owns every resource and bridges every callback to state:
    // sensor, audio, Wi-Fi Direct (lobby) and socket (transport).
    private val wifi = WifiDirectConnection(appContext)
    private val socket = SocketConnection()

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // --- STATE observed by the UI ---
    var phase by mutableStateOf(DuelPhase.IDLE); private set
    var reactionMs by mutableLongStateOf(0L); private set
    var falseStart by mutableStateOf(false); private set

    var nickname by mutableStateOf(""); private set
    var peers by mutableStateOf<List<WifiP2pDevice>>(emptyList()); private set
    var socketConnected by mutableStateOf(false); private set
    var isGroupOwner by mutableStateOf(false); private set
    var opponentName by mutableStateOf(""); private set
    var outcome by mutableStateOf<Outcome?>(null); private set

    var searching by mutableStateOf(false); private set
    var connecting by mutableStateOf(false); private set
    var connectionError by mutableStateOf<String?>(null); private set
    var challengeState by mutableStateOf(ChallengeState.NONE); private set

    var receivedSelfie by mutableStateOf<Bitmap?>(null); private set
    var receivedPhoto by mutableStateOf<Bitmap?>(null); private set

    // --- internals ---
    private var signalTime = 0L
    private var musicFinished = false
    private var countdownJob: Job? = null
    private var wifiRegistered = false
    private var socketStarted = false
    private var connectTimeoutJob: Job? = null

    private var iInitiated = false                  // I tapped -> I'm the challenger
    private var pendingLobbyMessage: String? = null
    private var pendingResumeSearch = false

    // Referee bookkeeping (meaningful only on the host = Group Owner).
    private var resolved = false
    private var goDrewMs: Long? = null
    private var goFalse = false
    private var clientDrewMs: Long? = null
    private var clientFalse = false

    var iAmReady by mutableStateOf(true); private set
    var opponentReady by mutableStateOf(true); private set


    init {
        westernMusic.onFinished = { musicFinished = true }

        wifi.onPeersAvailable = { peers = it }
        wifi.onConnectionInfo = { info -> handleConnectionInfo(info) }
        wifi.onDiscoveryChanged = { active -> handleDiscoveryChanged(active) }
        wifi.onConnectRequestFailed = { onConnectFailed("Connection request failed — try again") }

        socket.onConnected = {
            connectTimeoutJob?.cancel()
            socketConnected = true
            connecting = false
            connectionError = null
            if (iInitiated) {                       // challenger opens the app-level handshake
                socket.send(DuelProtocol.challenge(nickname.ifBlank { "Player" }))
                challengeState = ChallengeState.OUTGOING
            }
            // Non-initiator: wait for the incoming CHALLENGE, then show the dialog.
        }
        socket.onDisconnected = { onSocketClosed() }
        socket.onMessage = { line -> handleMessage(line) }
        socket.onError = { onSocketClosed() }
    }

    // --- Lobby ---------------------------------------------------------------

    fun updateNickname(name: String) { nickname = name }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        searching = true
        connecting = false
        connectionError = null
        wifi.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: WifiP2pDevice) {
        iInitiated = true
        searching = false
        connecting = true
        connectionError = null
        wifi.connect(device)
        startConnectTimeout()
    }

    fun stopSearching() {
        searching = false
        connectionError = null
        wifi.stopDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun handleDiscoveryChanged(active: Boolean) {
        if (active) return
        // Discovery also stops when a connection starts forming. Wait a moment and re-check:
        // relaunch ONLY if we're genuinely still just searching (not connecting/connected),
        // so we don't fight an in-progress group negotiation on the receiving phone
        viewModelScope.launch {
            delay(Config.Duel.DISCOVERY_RESTART_DELAY_MS)
            if (searching && !connecting && !socketConnected) wifi.startDiscovery()
        }
    }

    private fun startConnectTimeout() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = viewModelScope.launch {
            delay(Config.Duel.CONNECTION_TIMEOUT_MS)
            if (connecting && !socketConnected) {
                onConnectFailed("Couldn't connect — was the invitation accepted on the other phone?")
            }
        }
    }

    private fun onConnectFailed(message: String) {
        connectTimeoutJob?.cancel()
        connecting = false
        socketStarted = false
        iInitiated = false
        connectionError = message
        wifi.disconnect()
    }

    private fun handleConnectionInfo(info: WifiP2pInfo) {
        if (info.groupFormed && !socketStarted) {
            socketStarted = true
            searching = false
            wifi.stopDiscovery()              //  group formed, stop scanning cleanly
            connecting = true                 // the OTHER side may have initiated
            connectionError = null
            isGroupOwner = info.isGroupOwner
            if (info.isGroupOwner) socket.startServer()
            else info.groupOwnerAddress?.hostAddress?.let { socket.connectToServer(it) }
        }
    }

    // --- Challenge handshake -------------------------------------------------

    fun acceptChallenge() {
        socket.send(DuelProtocol.accept(nickname.ifBlank { "Player" }))
        challengeState = ChallengeState.ACCEPTED
    }

    fun declineChallenge() {
        socket.send(DuelProtocol.DECLINE)
        challengeState = ChallengeState.NONE
        pendingLobbyMessage = null
        pendingResumeSearch = true                  // back to searching, as requested
        viewModelScope.launch {
            delay(Config.Duel.DECLINE_FLUSH_MS)                              // let DECLINE flush before closing the socket
            startTeardown()
        }
    }

    // --- Incoming messages ---------------------------------------------------

    private fun handleMessage(line: String) {
        // Incoming winner photos (base64). Decode off the UI thread, then publish to state.
        DuelProtocol.parseSelfie(line)?.let { b64 ->
            viewModelScope.launch {
                val bmp = withContext(Dispatchers.Default) { PhotoCodec.decodeBase64(b64) }
                receivedSelfie = bmp
            }
            return
        }
        DuelProtocol.parsePhoto(line)?.let { b64 ->
            viewModelScope.launch {
                val bmp = withContext(Dispatchers.Default) { PhotoCodec.decodeBase64(b64) }
                receivedPhoto = bmp
            }
            return
        }
        if (line == DuelProtocol.READY) { opponentReady = true; return }
        // Pre-duel challenge handshake.
        DuelProtocol.parseChallenge(line)?.let { challengerNick ->
            opponentName = challengerNick
            if (iInitiated) {
                // Both tapped almost together: resolve into a game, don't deadlock.
                socket.send(DuelProtocol.accept(nickname.ifBlank { "Player" }))
                challengeState = ChallengeState.ACCEPTED
            } else {
                challengeState = ChallengeState.INCOMING
            }
            return
        }
        DuelProtocol.parseAccept(line)?.let { accepterNick ->
            opponentName = accepterNick
            challengeState = ChallengeState.ACCEPTED
            return
        }
        if (line == DuelProtocol.DECLINE) {
            pendingLobbyMessage = "${opponentName.ifBlank { "Opponent" }} declined"
            pendingResumeSearch = false
            startTeardown()
            return
        }

        // Duel messages only once the challenge is accepted.
        if (challengeState != ChallengeState.ACCEPTED) return
        if (isGroupOwner) {
            if (line == DuelProtocol.FALSE_START) onClientFalseStart()
            else DuelProtocol.parseTime(line)?.let { onClientDraw(it) }
        } else {
            when (line) {
                DuelProtocol.WAIT -> beginWaiting()
                DuelProtocol.GO -> beginDraw()
                else -> DuelProtocol.parseVerdict(line)?.let { showOutcome(it) }
            }
        }
    }

    // --- Teardown ------------------------------------------------------------

    private fun startTeardown() {
        connectTimeoutJob?.cancel()
        searching = false
        connecting = false
        wifi.stopDiscovery()
        socket.disconnect()
        wifi.disconnect()   // onSocketClosed() follows via socket.onDisconnected
    }

    fun leaveGame() {
        stopResultMusic()
        pendingLobbyMessage = null
        pendingResumeSearch = false
        startTeardown()
    }

    @SuppressLint("MissingPermission")
    private fun onSocketClosed() {
        connectTimeoutJob?.cancel()
        westernMusic.stop()
        stopResultMusic()
        socketConnected = false
        socketStarted = false
        searching = false
        connecting = false
        opponentName = ""
        challengeState = ChallengeState.NONE
        iInitiated = false
        phase = DuelPhase.IDLE
        resetRoundState()
        connectionError = pendingLobbyMessage
        pendingLobbyMessage = null
        if (pendingResumeSearch) {
            pendingResumeSearch = false
            searching = true
            viewModelScope.launch { delay(Config.Duel.RESUME_SEARCH_DELAY_MS); wifi.startDiscovery() }  // after removeGroup settles
        }
    }

    // --- Sensor + lifecycle --------------------------------------------------

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (abs(event.values[1]) <= Config.Duel.DRAW_THRESHOLD) return
            when (phase) {
                DuelPhase.WAITING -> onEarlyMovement()
                DuelPhase.DRAW -> onValidDraw()
                else -> {}
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun onScreenVisible() {
        if (!wifiRegistered) { wifi.register(); wifiRegistered = true }
        if (linearAccelSensor != null) {
            sensorManager.registerListener(sensorListener, linearAccelSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun onScreenHidden() {
        sensorManager.unregisterListener(sensorListener)
        if (wifiRegistered) { wifi.unregister(); wifiRegistered = false }
    }

    // --- Round lifecycle -----------------------------------------------------

    fun startDuel() {
        if (!isGroupOwner) return          // only the host (Group Owner) runs the round
        resetRoundState()
        stopResultMusic()            // NEW: silence any victory/defeat before a new round
        phase = DuelPhase.WAITING
        socket.send(DuelProtocol.WAIT)
        westernMusic.play()
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (!musicFinished && phase == DuelPhase.WAITING) delay(Config.Duel.COUNTDOWN_POLL_MS)
            if (phase != DuelPhase.WAITING) return@launch
            delay(Random.nextLong(Config.Duel.COUNTDOWN_MIN_MS, Config.Duel.COUNTDOWN_MAX_MS))
            if (phase == DuelPhase.WAITING && !resolved) {
                signalTime = SystemClock.elapsedRealtime()
                socket.send(DuelProtocol.GO)  // send FIRST so the client starts as early as possible
                fireSignal(appContext)   // then buzz locally
                phase = DuelPhase.DRAW
            }
        }
    }

    private fun beginWaiting() {   // client side, on WAIT
        resetRoundState()
        stopResultMusic()            // NEW: silence any victory/defeat before a new round
        phase = DuelPhase.WAITING
        westernMusic.play()
    }

    private fun beginDraw() {      // client side, on GO
        // Fire FIRST — buzz + timestamp are time-critical. westernMusic.stop() can block the
        // main thread a few ms (MediaPlayer.release), so do it AFTER the buzz, or the client's
        // vibration lags by a variable amount (the desync you saw).
        signalTime = SystemClock.elapsedRealtime()
        fireSignal(appContext)
        phase = DuelPhase.DRAW
        westernMusic.stop()
    }

    // --- Local duel events (both roles) --------------------------------------

    private fun onEarlyMovement() {
        if (phase != DuelPhase.WAITING) return
        westernMusic.stop()
        soundEffects.playCheater()
        falseStart = true
        if (isGroupOwner) { goFalse = true; resolveAsReferee() }
        else { socket.send(DuelProtocol.FALSE_START); phase = DuelPhase.RESOLVING }
    }

    private fun onValidDraw() {
        if (phase != DuelPhase.DRAW) return
        reactionMs = SystemClock.elapsedRealtime() - signalTime
        soundEffects.playGunshot()
        if (isGroupOwner) {
            goDrewMs = reactionMs
            resolveAsReferee()
            if (!resolved) phase = DuelPhase.RESOLVING
        } else {
            socket.send(DuelProtocol.time(reactionMs))
            phase = DuelPhase.RESOLVING
        }
    }

    // --- Referee (host / Group Owner only) -----------------------------------

    private fun onClientFalseStart() { clientFalse = true; resolveAsReferee() }
    private fun onClientDraw(ms: Long) { clientDrewMs = ms; resolveAsReferee() }

    private fun resolveAsReferee() {
        if (resolved) return
        val g = goDrewMs
        val c = clientDrewMs
        val myOutcome: Outcome? = when {
            goFalse -> Outcome.LOSE
            clientFalse -> Outcome.WIN
            g != null && c != null -> when {
                g < c -> Outcome.WIN
                g > c -> Outcome.LOSE
                else -> Outcome.DRAW
            }
            else -> null
        }
        if (myOutcome != null) {
            resolved = true
            countdownJob?.cancel()
            socket.send(DuelProtocol.verdict(opposite(myOutcome)))   // client's point of view
            showOutcome(myOutcome)
        }
    }

    private fun opposite(o: Outcome) = when (o) {
        Outcome.WIN -> Outcome.LOSE
        Outcome.LOSE -> Outcome.WIN
        Outcome.DRAW -> Outcome.DRAW
    }

    private fun showOutcome(o: Outcome) {
        westernMusic.stop()
        outcome = o
        phase = DuelPhase.RESULT
        when (o) {
            Outcome.WIN -> {
                victoryMusic.play()
                iAmReady = false          // winner becomes ready only AFTER sending the photos
            }
            Outcome.LOSE -> {
                defeatMusic.play()
                iAmReady = true           // loser doesn't gate the next round
                socket.send(DuelProtocol.READY)
            }
            Outcome.DRAW -> {
                iAmReady = true
                socket.send(DuelProtocol.READY)
            }
        }
        // The other side is not ready until it tells us so.
        opponentReady = false
    }

    private fun resetRoundState() {
        falseStart = false; reactionMs = 0L; outcome = null; musicFinished = false
        resolved = false; goDrewMs = null; goFalse = false; clientDrewMs = null; clientFalse = false
        receivedSelfie = null; receivedPhoto = null
        iAmReady = true; opponentReady = true
    }

    private fun stopResultMusic() {
        victoryMusic.stop()
        defeatMusic.stop()
    }

    fun playAgain() {
        if (!isGroupOwner) { phase = DuelPhase.IDLE; return }
        if (iAmReady && opponentReady) startDuel()
    }

    override fun onCleared() {
        super.onCleared()
        connectTimeoutJob?.cancel()
        countdownJob?.cancel()
        sensorManager.unregisterListener(sensorListener)
        if (wifiRegistered) { wifi.unregister(); wifiRegistered = false }
        westernMusic.stop()
        victoryMusic.stop()
        defeatMusic.stop()
        soundEffects.release()
        socket.release()
    }


    fun sendVictoryPhotos() {
        viewModelScope.launch {
            val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val selfiePath = File(dir, Config.Photo.SELFIE_FILE).absolutePath
            val photoPath = File(dir, Config.Photo.PHOTO_FILE).absolutePath

            val selfieB64 = withContext(Dispatchers.Default) { PhotoCodec.encodeFileToBase64(selfiePath) }
            val photoB64 = withContext(Dispatchers.Default) { PhotoCodec.encodeFileToBase64(photoPath) }

            selfieB64?.let { socket.send(DuelProtocol.selfie(it)) }
            photoB64?.let { socket.send(DuelProtocol.photo(it)) }

            socket.send(DuelProtocol.READY)  // winner is done -> ok to start next round
            iAmReady = true
        }
    }

    // Winner chooses not to take photos: just declare readiness.
    fun skipVictoryPhotos() {
        socket.send(DuelProtocol.READY)
        iAmReady = true
    }

    fun selfieFileName() = Config.Photo.SELFIE_FILE
    fun photoFileName() = Config.Photo.PHOTO_FILE
}