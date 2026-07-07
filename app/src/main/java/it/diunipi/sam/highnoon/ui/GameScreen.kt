package it.diunipi.sam.highnoon.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.graphics.Bitmap
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

import it.diunipi.sam.highnoon.R
import it.diunipi.sam.highnoon.game.ChallengeState
import it.diunipi.sam.highnoon.game.DuelPhase
import it.diunipi.sam.highnoon.game.DuelViewModel
import it.diunipi.sam.highnoon.game.GallerySaver
import it.diunipi.sam.highnoon.game.Outcome
import java.io.File

private fun requiredRuntimePermissions() = arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES) // minSdk 33
private fun hasAllPermissions(context: Context, perms: Array<String>) =
    perms.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

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

    ScreenBackground(imageRes = backgroundFor(viewModel), modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!viewModel.socketConnected) {
                DuelText(text = stringResource(R.string.app_title), fontSize = 48.sp)
                Spacer(modifier = Modifier.height(24.dp))

                if (!permsGranted) {
                    Button(onClick = { permissionLauncher.launch(requiredRuntimePermissions()) }) {
                        Text(text = stringResource(R.string.grant_permissions))
                    }
                } else if (viewModel.connecting) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    DuelText(text = stringResource(R.string.connecting), fontSize = 18.sp)
                } else {
                    OutlinedTextField(
                        value = viewModel.nickname,
                        onValueChange = { viewModel.updateNickname(it) },
                        label = { Text(stringResource(R.string.nickname_label)) },
                        singleLine = true,
                        enabled = !viewModel.searching
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (viewModel.searching) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        DuelText(text = stringResource(R.string.searching), fontSize = 16.sp)
                        TextButton(onClick = { viewModel.stopSearching() }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    } else {
                        Button(onClick = { viewModel.startDiscovery() }) {
                            Text(text = stringResource(R.string.find_opponent))
                        }
                    }

                    viewModel.connectionError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = err, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (viewModel.peers.isNotEmpty()) {
                        DuelText(text = stringResource(R.string.tap_to_challenge), fontSize = 14.sp)
                        viewModel.peers.forEach { device ->
                            DuelText(
                                text = "• ${device.deviceName}",
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.connect(device) }
                                    .padding(vertical = 8.dp)
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
                        DuelText(text = stringResource(R.string.waiting_accept), fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.leaveGame() }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }

                    ChallengeState.INCOMING -> {
                        DuelText(text = stringResource(R.string.incoming_challenge), fontSize = 16.sp)
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text(text = stringResource(R.string.challenge_title)) },
                            text = {
                                Text(
                                    text = stringResource(
                                        R.string.challenge_body,
                                        viewModel.opponentName.ifBlank { stringResource(R.string.someone) }
                                    )
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { viewModel.acceptChallenge() }) {
                                    Text(text = stringResource(R.string.accept))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.declineChallenge() }) {
                                    Text(text = stringResource(R.string.decline))
                                }
                            }
                        )
                    }

                    ChallengeState.NONE -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        DuelText(text = stringResource(R.string.connecting), fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.leaveGame() }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuelContent(viewModel: DuelViewModel) {
    val opponent = viewModel.opponentName.ifBlank { stringResource(R.string.default_opponent) }

    // While the winner's live camera is on screen, DON'T scroll: a live PreviewView and
    // verticalScroll don't coexist (the preview needs a bounded, non-scrolling slot).
    // Everything else (incl. the static result photos) scrolls normally.
    val winnerIsShooting = viewModel.phase == DuelPhase.RESULT && viewModel.outcome == Outcome.WIN && !viewModel.iAmReady

    val scrollModifier = if (winnerIsShooting) Modifier else Modifier.verticalScroll(rememberScrollState())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(scrollModifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (viewModel.phase) {
            DuelPhase.IDLE -> {
                DuelText(text = stringResource(R.string.connected_to, opponent), fontSize = 18.sp)
                Spacer(modifier = Modifier.height(24.dp))
                if (viewModel.isGroupOwner) {
                    DuelText(text = stringResource(R.string.you_are_host), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.startDuel() }) {
                        Text(text = stringResource(R.string.start_duel))
                    }
                } else {
                    DuelText(text = stringResource(R.string.waiting_host), fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.leaveGame() }) {
                    Text(text = stringResource(R.string.leave))
                }
            }

            DuelPhase.WAITING -> {
                DuelText(text = stringResource(R.string.hold_still), fontSize = 28.sp)
                Spacer(modifier = Modifier.height(8.dp))
                DuelText(text = stringResource(R.string.draw_only_signal), fontSize = 14.sp)
            }

            DuelPhase.DRAW -> {
                DuelText(
                    text = stringResource(R.string.draw),
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            DuelPhase.RESOLVING -> {
                DuelText(
                    text = stringResource(R.string.waiting_opponent, opponent),
                    fontSize = 20.sp
                )
                if (viewModel.falseStart) {
                    DuelText(
                        text = stringResource(R.string.false_start),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (viewModel.reactionMs > 0) {
                    DuelText(
                        text = stringResource(R.string.your_time, viewModel.reactionMs),
                        fontSize = 16.sp
                    )
                }
            }

            DuelPhase.RESULT -> {
                val (text, color) = when (viewModel.outcome) {
                    Outcome.WIN -> stringResource(R.string.you_win) to MaterialTheme.colorScheme.primary
                    Outcome.LOSE -> stringResource(R.string.you_lose) to MaterialTheme.colorScheme.error
                    Outcome.DRAW -> stringResource(R.string.result_draw) to Color.White
                    null -> "" to Color.White
                }
                DuelText(text = text, fontSize = 44.sp, color = color)
                Spacer(modifier = Modifier.height(8.dp))
                if (viewModel.falseStart) {
                    DuelText(
                        text = stringResource(R.string.false_start),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (viewModel.reactionMs > 0) {
                    DuelText(
                        text = stringResource(R.string.your_time, viewModel.reactionMs),
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // WINNER: in-app camera (front then back). App stays foregrounded -> socket stays alive.
                if (viewModel.outcome == Outcome.WIN) {
                    WinnerPhotoSection(viewModel)
                }

                // LOSER: show the winner's photos as they arrive, uniform size.
                // LOSER: show the winner's photos as they arrive, uniform size.
                if (viewModel.outcome == Outcome.LOSE) {
                    if (viewModel.receivedSelfie == null && viewModel.receivedPhoto == null) {
                        DuelText(text = stringResource(R.string.waiting_winner_photos), fontSize = 14.sp)
                    }
                    // Stacked, natural aspect ratio (Fit, not Crop): each photo shows in full, portrait or
                    // landscape. The screen scrolls, so height is free — no clipping, no stretching.
                    viewModel.receivedSelfie?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "winner selfie",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    viewModel.receivedPhoto?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "winner photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (viewModel.receivedSelfie != null || viewModel.receivedPhoto != null) {
                        val scope = rememberCoroutineScope()
                        val ctx = LocalContext.current

                        val savedMsg = stringResource(R.string.saved_to_gallery)
                        val failedMsg = stringResource(R.string.save_failed)
                        Button(onClick = {
                            scope.launch {
                                var ok = true
                                viewModel.receivedSelfie?.let {
                                    ok = GallerySaver.saveBitmapToGallery(ctx, it, "highnoon_selfie_${System.currentTimeMillis()}.jpg") && ok
                                }
                                viewModel.receivedPhoto?.let {
                                    ok = GallerySaver.saveBitmapToGallery(ctx, it, "highnoon_photo_${System.currentTimeMillis()}.jpg") && ok
                                }
                                Toast.makeText(ctx, if (ok) savedMsg else failedMsg, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text(text = stringResource(R.string.save_to_gallery))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Hide the bottom buttons while the winner is still taking photos (so the camera
                // preview doesn't squash them). They reappear once iAmReady == true.
                if (viewModel.iAmReady) {
                    if (viewModel.isGroupOwner) {
                        val canRestart = viewModel.iAmReady && viewModel.opponentReady
                        Button(onClick = { viewModel.playAgain() }, enabled = canRestart) {
                            Text(
                                text = if (canRestart) stringResource(R.string.play_again)
                                else stringResource(R.string.waiting_photos)
                            )
                        }
                    } else {
                        DuelText(
                            text = stringResource(R.string.waiting_next_round),
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.leaveGame() }) {
                        Text(text = stringResource(R.string.leave))
                    }
                }
            }
        }
    }
}

@Composable
private fun WinnerPhotoSection(viewModel: DuelViewModel) {
    val context = LocalContext.current

    var camGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> camGranted = granted }

    var step by remember { mutableStateOf(0) }   // 0 selfie, 1 photo, 2 sent
    var didSkip by remember { mutableStateOf(false) }

    fun fileIn(name: String) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)

    if (!camGranted) {
        DuelText(text = stringResource(R.string.grab_victory), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
            Text(text = stringResource(R.string.enable_camera))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = {
            viewModel.skipVictoryPhotos()
            didSkip = true
            step = 2
        }) {
            Text(text = stringResource(R.string.skip))
        }
        return
    }

    when (step) {
        0 -> {
            DuelText(text = stringResource(R.string.victory_selfie), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            CameraCapture(
                lensFacing = CameraSelector.LENS_FACING_FRONT,
                outputFile = fileIn(viewModel.selfieFileName()),
                captureLabel = stringResource(R.string.capture_selfie),
                onCaptured = { step = 1 }
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = {
                viewModel.skipVictoryPhotos()
                didSkip = true
                step = 2                      // leave the camera composable NOW -> unbindAll() happens here,
            }) {                             // not later during "Play again" (that was the lag)
                Text(text = stringResource(R.string.skip))
            }
        }
        1 -> {
            DuelText(text = stringResource(R.string.photo_for_loser), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            CameraCapture(
                lensFacing = CameraSelector.LENS_FACING_BACK,
                outputFile = fileIn(viewModel.photoFileName()),
                captureLabel = stringResource(R.string.capture_photo),
                onCaptured = {
                    step = 2
                    viewModel.sendVictoryPhotos()
                }
            )
        }
        2 -> {
            if (!didSkip) {
                DuelText(text = stringResource(R.string.photos_sent), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                val selfieBmp by produceState<Bitmap?>(initialValue = null, viewModel.selfieFileName()) {
                    value = withContext(Dispatchers.IO) { loadLocalBitmap(fileIn(viewModel.selfieFileName())) }
                }
                val photoBmp by produceState<Bitmap?>(initialValue = null, viewModel.photoFileName()) {
                    value = withContext(Dispatchers.IO) { loadLocalBitmap(fileIn(viewModel.photoFileName())) }
                }

                selfieBmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "my selfie",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                photoBmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "my photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )
                }

                val scope = rememberCoroutineScope()
                val ctx = LocalContext.current

                Spacer(modifier = Modifier.height(8.dp))

                val savedMsg = stringResource(R.string.saved_to_gallery)
                val failedMsg = stringResource(R.string.save_failed)
                Button(onClick = {
                    scope.launch {
                        val a = GallerySaver.saveToGallery(ctx, fileIn(viewModel.selfieFileName()), "highnoon_selfie_${System.currentTimeMillis()}.jpg")
                        val b = GallerySaver.saveToGallery(ctx, fileIn(viewModel.photoFileName()), "highnoon_photo_${System.currentTimeMillis()}.jpg")
                        Toast.makeText(ctx, if (a && b) savedMsg else failedMsg, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(text = stringResource(R.string.save_to_gallery))
                }
            }
            // se didSkip == true: non mostra nulla, restano solo i bottoni Play again / Leave sotto
        }
    }
}

// Picks the background for the current game state. The "gunshot" is an instant inside
// DRAW/RESOLVING, so for now it shares the DRAW background.
@DrawableRes
private fun backgroundFor(viewModel: DuelViewModel): Int {
    if (!viewModel.socketConnected) return R.drawable.bg_lobby        // start + search
    return when (viewModel.phase) {
        DuelPhase.IDLE -> R.drawable.bg_idle                          // pre-duel room
        DuelPhase.WAITING -> R.drawable.bg_waiting                    // music / hold still
        DuelPhase.DRAW -> R.drawable.bg_draw
        DuelPhase.RESOLVING -> R.drawable.bg_draw                     // still "in action"
        DuelPhase.RESULT -> when (viewModel.outcome) {
            Outcome.WIN -> R.drawable.bg_win
            Outcome.LOSE -> R.drawable.bg_lose
            else -> R.drawable.bg_idle                                // draw: neutral
        }
    }
}

private fun loadLocalBitmap(file: File): android.graphics.Bitmap? =
    if (file.exists())
        android.graphics.BitmapFactory.decodeFile(
            file.absolutePath,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
        )
    else null