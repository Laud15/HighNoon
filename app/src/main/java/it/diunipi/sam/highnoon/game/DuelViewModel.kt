package it.diunipi.sam.highnoon.game

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.diunipi.sam.highnoon.audio.SoundEffects
import it.diunipi.sam.highnoon.audio.WesternMusic
import it.diunipi.sam.highnoon.network.SocketConnection
import it.diunipi.sam.highnoon.network.WifiDirectConnection
import it.diunipi.sam.highnoon.notification.fireSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

private const val DRAW_THRESHOLD = 12f

class DuelViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val westernMusic = WesternMusic(appContext)
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
        // so we don't fight an in-progress group negotiation on the receiving phone (Lez. 21).
        viewModelScope.launch {
            delay(3000)
            if (searching && !connecting && !socketConnected) wifi.startDiscovery()
        }
    }

    private fun startConnectTimeout() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = viewModelScope.launch {
            delay(25_000)
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
            delay(250)                              // let DECLINE flush before closing the socket
            startTeardown()
        }
    }

    // --- Incoming messages ---------------------------------------------------

    private fun handleMessage(line: String) {
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
        pendingLobbyMessage = null
        pendingResumeSearch = false
        startTeardown()
    }

    @SuppressLint("MissingPermission")
    private fun onSocketClosed() {
        connectTimeoutJob?.cancel()
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
            viewModelScope.launch { delay(1200); wifi.startDiscovery() }  // after removeGroup settles
        }
    }

    // --- Sensor + lifecycle --------------------------------------------------

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (abs(event.values[1]) <= DRAW_THRESHOLD) return
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
        phase = DuelPhase.WAITING
        socket.send(DuelProtocol.WAIT)
        westernMusic.play()
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (!musicFinished && phase == DuelPhase.WAITING) delay(50)
            if (phase != DuelPhase.WAITING) return@launch
            delay(Random.nextLong(1000, 4000))
            if (phase == DuelPhase.WAITING && !resolved) {
                signalTime = SystemClock.elapsedRealtime()
                fireSignal(appContext)
                phase = DuelPhase.DRAW
                socket.send(DuelProtocol.GO)
            }
        }
    }

    private fun beginWaiting() {   // client side, on WAIT
        resetRoundState()
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
        outcome = o
        phase = DuelPhase.RESULT
    }

    private fun resetRoundState() {
        falseStart = false; reactionMs = 0L; outcome = null; musicFinished = false
        resolved = false; goDrewMs = null; goFalse = false; clientDrewMs = null; clientFalse = false
    }

    fun playAgain() { if (isGroupOwner) startDuel() else phase = DuelPhase.IDLE }

    override fun onCleared() {
        super.onCleared()
        connectTimeoutJob?.cancel()
        countdownJob?.cancel()
        sensorManager.unregisterListener(sensorListener)
        if (wifiRegistered) { wifi.unregister(); wifiRegistered = false }
        westernMusic.stop()
        soundEffects.release()
        socket.release()
    }
}