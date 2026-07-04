package it.diunipi.sam.highnoon.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission

private const val TAG = "WifiDirect"

// Expert class: OWNS the whole Wi-Fi Direct connection (manager, channel, receiver)
// and hides it behind simple methods. Talks outward via callbacks (callback -> state
// bridge), like WesternMusic.onFinished.
class WifiDirectConnection(private val context: Context) {

    // System service: the entry point for every Wi-Fi Direct operation (Lez. 21).
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    // The channel: our handle to talk to the framework, bound to the main Looper.
    private val channel = manager.initialize(context, context.mainLooper, null)

    // --- Callbacks the outside subscribes to ---
    var onWifiP2pEnabled: ((Boolean) -> Unit)? = null
    var onPeersAvailable: ((List<WifiP2pDevice>) -> Unit)? = null
    var onThisDeviceChanged: ((WifiP2pDevice) -> Unit)? = null
    var onConnectionInfo: ((WifiP2pInfo) -> Unit)? = null

    var onDiscoveryChanged: ((Boolean) -> Unit)? = null   // true = discovery active

    var onConnectRequestFailed: (() -> Unit)? = null   // the framework rejected the connect() request

    // These two listeners fire REPEATEDLY (on every peer/connection change), so we
    // create them ONCE as fields and reuse them (criterio prof n.3, fewer objects).
    private val peerListListener = WifiP2pManager.PeerListListener { peers ->
        onPeersAvailable?.invoke(peers.deviceList.toList())
    }
    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        onConnectionInfo?.invoke(info)
    }

    // The framework "calls us back" here on P2P events (Lez. 17). Keep onReceive light.
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    onWifiP2pEnabled?.invoke(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    // Peer list changed: ask for it (async, arrives on peerListListener).
                    manager.requestPeers(channel, peerListListener)
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    // Connection changed: ask WHO is group owner and the IP
                    // (async, arrives on connectionInfoListener).
                    manager.requestConnectionInfo(channel, connectionInfoListener)
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // getParcelableExtra(name) is deprecated since API 33: branch by
                    // version like the rest of the app (Lez. 01: fragmentation).
                    // minSdk 33: the Class<T> overload is always available (no deprecated fallback).
                    val me = intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java
                    )
                    if (me != null) onThisDeviceChanged?.invoke(me)
                }
                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                    onDiscoveryChanged?.invoke(state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED)
                }
            }
        }
    }

    // The P2P events we care about (dynamic registration, Lez. 17).
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
    }

    fun register() {
        // minSdk 33: the exported flag is always required, no legacy branch.
        context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun unregister() {
        context.unregisterReceiver(receiver)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    fun startDiscovery() {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "discoverPeers: request accepted") }
            override fun onFailure(reason: Int) {
                Log.d(TAG, "discoverPeers: request failed, reason=$reason")
            }
        })
    }

    // Ask the framework to form a group with the given peer.
    // ASYNC: onSuccess only means the REQUEST was accepted; the real outcome arrives
    // via WIFI_P2P_CONNECTION_CHANGED_ACTION (same double-bounce as discovery).
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC   // push-button pairing, no PIN
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "connect: request initiated to ${device.deviceName}")
            }
            override fun onFailure(reason: Int) {
                Log.d(TAG, "connect: request failed, reason=$reason")
                onConnectRequestFailed?.invoke()
            }
        })
    }

    // Tear down the current group. IMPORTANT: Wi-Fi Direct groups PERSIST; leaving one
    // behind causes "already connected" / stale-group problems on the next run.
    // (Same "one owner cleans up its resource" idea as WesternMusic stopping its player.)
    fun disconnect() {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "removeGroup: success") }
            override fun onFailure(reason: Int) {
                Log.d(TAG, "removeGroup: failed, reason=$reason")
            }
        })
    }


    fun stopDiscovery() {
        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "stopPeerDiscovery: ok") }
            override fun onFailure(reason: Int) { Log.d(TAG, "stopPeerDiscovery: failed, reason=$reason") }
        })
    }
}