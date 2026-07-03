package it.diunipi.sam.highnoon.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.diunipi.sam.highnoon.network.SocketConnection
import it.diunipi.sam.highnoon.network.WifiDirectConnection

// minSdk 33: discovery needs only NEARBY_WIFI_DEVICES.
private fun requiredRuntimePermissions(): Array<String> =
    arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)

private fun hasAllPermissions(context: Context, perms: Array<String>): Boolean =
    perms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

private const val NICK_PREFIX = "NICK:"

@Composable
fun WifiTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var permsGranted by remember {
        mutableStateOf(hasAllPermissions(context, requiredRuntimePermissions()))
    }
    var nickname by remember { mutableStateOf("") }
    var peers by remember { mutableStateOf<List<WifiP2pDevice>>(emptyList()) }
    var connectionInfo by remember { mutableStateOf<WifiP2pInfo?>(null) }
    var socketConnected by remember { mutableStateOf(false) }
    var peerNickname by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    // Two owners, two resources (separation of concerns, Lez. 11):
    // P2P layer (discovery + group) and TCP layer (messaging over the group).
    val wifi = remember { WifiDirectConnection(context) }
    val socket = remember { SocketConnection() }

    // Bridge callback -> state: P2P layer.
    DisposableEffect(Unit) {
        wifi.onPeersAvailable = { list -> peers = list }
        wifi.onConnectionInfo = { info -> connectionInfo = info }
        wifi.register()
        onDispose {
            wifi.unregister()
            wifi.onPeersAvailable = null
            wifi.onConnectionInfo = null
        }
    }

    // Bridge callback -> state: TCP layer.
    DisposableEffect(Unit) {
        socket.onConnected = { socketConnected = true; status = "socket connected" }
        socket.onDisconnected = { socketConnected = false; peerNickname = "" }
        socket.onMessage = { line ->
            if (line.startsWith(NICK_PREFIX)) peerNickname = line.removePrefix(NICK_PREFIX)
        }
        socket.onError = { msg -> status = msg }
        onDispose {
            socket.release()                 // permanent teardown when leaving the screen
            socket.onConnected = null
            socket.onDisconnected = null
            socket.onMessage = null
            socket.onError = null
        }
    }

    val info = connectionInfo
    val groupFormed = info?.groupFormed == true

    // When the P2P group forms, start the socket. Runs once per formation (keyed on booleans).
    LaunchedEffect(groupFormed, info?.isGroupOwner) {
        if (groupFormed) {
            if (info?.isGroupOwner == true) {
                status = "group formed — starting server"; socket.startServer()
            } else {
                val host = info?.groupOwnerAddress?.hostAddress
                if (host != null) { status = "group formed — connecting to $host"; socket.connectToServer(host) }
            }
        }
    }

    // Once the socket is up, send our nickname as the first message (the handshake).
    LaunchedEffect(socketConnected) {
        if (socketConnected) socket.send(NICK_PREFIX + nickname.ifBlank { "Player" })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> permsGranted = result.values.all { it } }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Wi-Fi Direct — test", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(12.dp))

        if (!permsGranted) {
            Button(onClick = { permissionLauncher.launch(requiredRuntimePermissions()) }) {
                Text(text = "Grant permissions")
            }
        } else if (!groupFormed) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Your nickname") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { wifi.startDiscovery() }) { Text(text = "Discover peers") }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Peers found: ${peers.size} (tap to connect)", fontSize = 16.sp)
            peers.forEach { device ->
                Text(
                    text = "• ${device.deviceName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { wifi.connect(device) }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(text = "Role: " + if (info?.isGroupOwner == true) "Group Owner" else "Client")
            Spacer(modifier = Modifier.height(8.dp))
            if (socketConnected) {
                Text(text = "CONNECTED", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                Text(text = "Dueling against: ${peerNickname.ifBlank { "…" }}", fontSize = 18.sp)
            } else {
                Text(text = "Linking socket…")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { socket.disconnect(); wifi.disconnect() }) { Text(text = "Disconnect") }
        }

        if (status.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}