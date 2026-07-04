package it.diunipi.sam.highnoon.ui


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diunipi.sam.highnoon.game.ChallengeState
import it.diunipi.sam.highnoon.game.DuelPhase
import it.diunipi.sam.highnoon.game.DuelViewModel
import it.diunipi.sam.highnoon.game.Outcome
import java.io.File

private fun requiredRuntimePermissions() = arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES) // minSdk 33
private fun hasAllPermissions(context: Context, perms: Array<String>) = perms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    viewModel: DuelViewModel = viewModel()
) {
    val context = LocalContext.current
    var permsGranted by remember {
        mutableStateOf(hasAllPermissions(context, requiredRuntimePermissions()))
    }

    // Sensor + Wi-Fi Direct receiver live only while this screen is shown.
    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> permsGranted = result.values.all { it } }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!viewModel.socketConnected) {
            Text(text = "HIGH NOON", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(24.dp))

            if (!permsGranted) {
                Button(onClick = { permissionLauncher.launch(requiredRuntimePermissions()) }) {
                    Text(text = "Grant permissions")
                }
            } else if (viewModel.connecting) {
                // A connection is being established (we tapped a peer, or the other side did).
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Connecting…", fontSize = 16.sp)
            } else {
                OutlinedTextField(
                    value = viewModel.nickname,
                    onValueChange = { viewModel.updateNickname(it) },
                    label = { Text("Your nickname") },
                    singleLine = true,
                    enabled = !viewModel.searching
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (viewModel.searching) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Searching for opponents…", fontSize = 14.sp)
                    TextButton(onClick = { viewModel.stopSearching() }) { Text(text = "Cancel") }
                } else {
                    Button(onClick = { viewModel.startDiscovery() }) { Text(text = "Find an opponent") }
                }

                viewModel.connectionError?.let { err ->        // <-- NEW
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (viewModel.peers.isNotEmpty()) {
                    Text(
                        text = "Tap an opponent to challenge (only one of you needs to):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    viewModel.peers.forEach { device ->
                        Text(
                            text = "• ${device.deviceName}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.connect(device) }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            when (viewModel.challengeState) {
                ChallengeState.ACCEPTED -> DuelContent(viewModel)

                ChallengeState.OUTGOING -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Waiting for opponent to accept…", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.leaveGame() }) { Text(text = "Cancel") }
                }

                ChallengeState.INCOMING -> {
                    Text(text = "Incoming challenge…", fontSize = 16.sp)
                    AlertDialog(
                        onDismissRequest = { },                 // force an explicit choice
                        title = { Text(text = "Challenge!") },
                        text = { Text(text = "${viewModel.opponentName.ifBlank { "Someone" }} wants to duel you.") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.acceptChallenge() }) { Text(text = "Accept") }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.declineChallenge() }) { Text(text = "Decline") }
                        }
                    )
                }

                ChallengeState.NONE -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Connecting…", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.leaveGame() }) { Text(text = "Cancel") }
                }
            }
        }
    }
}

@Composable
private fun DuelContent(viewModel: DuelViewModel) {
    val opponent = viewModel.opponentName.ifBlank { "opponent" }
    when (viewModel.phase) {
        DuelPhase.IDLE -> {
            Text(text = "Connected to $opponent", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(24.dp))
            if (viewModel.isGroupOwner) {
                Text(text = "You are the host", fontSize = 16.sp)
                Button(onClick = { viewModel.startDuel() }) { Text(text = "Start duel") }
            } else {
                Text(text = "Waiting for the host to start…", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.leaveGame() }) { Text(text = "Leave") }
        }
        DuelPhase.WAITING -> {
            Text(text = "Hold still…", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Draw ONLY on the signal!",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DuelPhase.DRAW -> {
            Text(text = "DRAW!", fontSize = 64.sp, color = MaterialTheme.colorScheme.primary)
        }
        DuelPhase.RESOLVING -> {
            Text(text = "Waiting for $opponent…", fontSize = 20.sp)
            if (viewModel.falseStart) {
                Text(text = "False start!", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
            } else if (viewModel.reactionMs > 0) {
                Text(text = "Your time: ${viewModel.reactionMs} ms", fontSize = 16.sp)
            }
        }
        DuelPhase.RESULT -> {
            val (text, color) = when (viewModel.outcome) {
                Outcome.WIN -> "YOU WIN" to MaterialTheme.colorScheme.primary
                Outcome.LOSE -> "YOU LOSE" to MaterialTheme.colorScheme.error
                Outcome.DRAW -> "DRAW" to MaterialTheme.colorScheme.onSurface
                null -> "" to MaterialTheme.colorScheme.onSurface
            }
            Text(text = text, fontSize = 44.sp, color = color)
            Spacer(modifier = Modifier.height(8.dp))
            if (viewModel.falseStart) {
                Text(text = "False start!", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
            } else if (viewModel.reactionMs > 0) {
                Text(text = "Your time: ${viewModel.reactionMs} ms", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // WINNER: in-app camera (front then back). App stays foregrounded -> socket stays alive.
            if (viewModel.outcome == Outcome.WIN) {
                WinnerPhotoSection(viewModel)
            }

            // LOSER: show the winner's photos as they arrive.
            if (viewModel.outcome == Outcome.LOSE) {
                if (viewModel.receivedSelfie == null && viewModel.receivedPhoto == null) {
                    Text(text = "Waiting for the winner's photos…", fontSize = 14.sp)
                }
                viewModel.receivedSelfie?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "winner selfie",
                        modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }
                viewModel.receivedPhoto?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "winner photo",
                        modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            if (viewModel.isGroupOwner) {
                val canRestart = viewModel.iAmReady && viewModel.opponentReady
                Button(onClick = { viewModel.playAgain() }, enabled = canRestart) {
                    Text(text = if (canRestart) "Play again" else "Waiting for photos…")
                }
            } else {
                Text(text = "Waiting for the next round…", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { viewModel.leaveGame() }) { Text(text = "Leave") }
        }
    }
}


@Composable
private fun WinnerPhotoSection(viewModel: DuelViewModel) {
    val context = LocalContext.current

    // Runtime CAMERA permission (dangerous, Lez. 04), same launcher pattern as elsewhere.
    var camGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> camGranted = granted }

    var step by remember { mutableStateOf(0) }   // 0 selfie, 1 photo, 2 sent

    fun fileIn(name: String) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)

    if (!camGranted) {
        Text(text = "Grab your victory shots!", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
            Text(text = "Enable camera")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { viewModel.skipVictoryPhotos() }) { Text(text = "Skip") }
        return
    }

    when (step) {
        0 -> {
            Text(text = "1/2 — Victory selfie", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            CameraCapture(
                lensFacing = CameraSelector.LENS_FACING_FRONT,
                outputFile = fileIn(viewModel.selfieFileName()),
                captureLabel = "Capture selfie",
                onCaptured = { step = 1 }
            )
            TextButton(onClick = { viewModel.skipVictoryPhotos() }) { Text(text = "Skip") }
        }
        1 -> {
            Text(text = "2/2 — A photo for the loser", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            CameraCapture(
                lensFacing = CameraSelector.LENS_FACING_BACK,
                outputFile = fileIn(viewModel.photoFileName()),
                captureLabel = "Capture photo",
                onCaptured = {
                    step = 2
                    viewModel.sendVictoryPhotos()
                }
            )
        }
        2 -> Text(text = "Photos sent to your opponent!", fontSize = 14.sp)
    }
}