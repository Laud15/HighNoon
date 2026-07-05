package it.diunipi.sam.highnoon.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import it.diunipi.sam.highnoon.Config

private const val TAG = "DuelSocket"
//private const val PORT = 8988

// Owns the TCP socket over the Wi-Fi Direct group and runs ALL its blocking I/O on
// Dispatchers.IO, never on the UI thread .
// Callbacks are marshalled to the MAIN thread, so the UI can use them directly.
// One owner of the resource -> it also closes everything (like WesternMusic).
class SocketConnection {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // GROUP OWNER role: listen and wait for the client. accept() BLOCKS -> IO.
    fun startServer() {
        scope.launch {
            try {
                val server = ServerSocket()
                server.reuseAddress = true               // covers the TIME_WAIT case
                server.bind(InetSocketAddress(Config.Network.PORT))
                serverSocket = server
                Log.d(TAG, "server: waiting for client on port ${Config.Network.PORT}")
                val client = server.accept()             // blocks until the client connects
                // A duel needs exactly ONE opponent: stop listening and free the port NOW,
                // so the listening socket can never be left bound across the session.
                server.close()
                serverSocket = null
                Log.d(TAG, "server: client connected")
                handleSocket(client)
            } catch (e: Exception) {
                Log.d(TAG, "server error: ${e.message}")
                notifyError("server: ${e.message}")
            }
        }
    }

    // CLIENT role: connect to the group owner's IP. connect() BLOCKS -> IO.
    // The server may not be listening the instant the group forms, so we retry.
    fun connectToServer(host: String) {
        scope.launch {
            var attempts = 0
            while (scope.isActive) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(host, Config.Network.PORT), Config.Network.SOCKET_CONNECT_TIMEOUT_MS)
                    Log.d(TAG, "client: connected to $host")
                    handleSocket(s)
                    return@launch
                } catch (e: Exception) {
                    attempts++
                    Log.d(TAG, "client: attempt $attempts failed (${e.message})")
                    if (attempts >= Config.Network.CONNECT_RETRIES) { notifyError("client: could not reach $host"); return@launch }
                    delay(Config.Network.RETRY_DELAY_MS)
                }
            }
        }
    }

    // Same code once the socket exists, for both server and client side.
    private suspend fun handleSocket(s: Socket) {
        socket = s
        s.tcpNoDelay = true  // real-time channel: disable Nagle so the tiny GO/result messages aren't buffered by TCP before going out

        writer = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(s.getInputStream()))

        withContext(Dispatchers.Main) { onConnected?.invoke() }

        try {
            // readLine() BLOCKS waiting for the next line -> stay on IO.
            while (scope.isActive) {
                val line = reader.readLine() ?: break    // null = the other side closed
                withContext(Dispatchers.Main) { onMessage?.invoke(line) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "read ended: ${e.message}")       // a closed socket lands here; not an error
        } finally {
            // Release resources on ANY exit path, not just the Disconnect button.
            closeSockets()
            withContext(Dispatchers.Main) { onDisconnected?.invoke() }
        }
    }

    // Send one line. Writing blocks too -> IO. The newline lets readLine() on the
    // other side know where the message ends.
    fun send(message: String) {
        scope.launch {
            try {
                val w = writer ?: return@launch
                w.write(message); w.newLine(); w.flush()
            } catch (e: Exception) {
                Log.d(TAG, "write error: ${e.message}")
                notifyError("write: ${e.message}")
            }
        }
    }

    private suspend fun notifyError(msg: String) {
        withContext(Dispatchers.Main) { onError?.invoke(msg) }
    }

    // Close whatever is open; safe to call multiple times. Also the way to UNBLOCK a
    // thread stuck in accept()/readLine(): closing the socket makes it throw.
    private fun closeSockets() {
        try { socket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        socket = null
        serverSocket = null
        writer = null
    }

    // Close the current connection but keep the object reusable (reconnect possible).
    fun disconnect() {
        closeSockets()
    }

    // Permanent teardown: also cancel the coroutines. Call when the object is discarded.
    fun release() {
        closeSockets()
        scope.cancel()
    }
}