package it.diunipi.sam.highnoon.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

// The two shots the winner takes, in order.
private enum class ShotStep { IDLE, SELFIE, PHOTO, DONE }

private const val SELFIE_FILE = "winner_selfie.jpg"
private const val PHOTO_FILE = "winner_photo.jpg"

// FileProvider content:// URI for a given file name in our app-specific Pictures dir.
private fun uriFor(context: Context, fileName: String): Uri {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(dir, fileName))
}

// Decode a saved photo DOWNSAMPLED, so full-res camera images don't blow up memory.
private fun loadBitmap(context: Context, fileName: String) =
    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { dir ->
        val f = File(dir, fileName)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
        else null
    }

@Composable
fun PhotoTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(ShotStep.IDLE) }

    // One launcher, reused for both shots. On return we advance the sequence.
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        step = when (step) {
            ShotStep.SELFIE -> if (success) {
                takePictureNext(context) { ShotStep.PHOTO }   // selfie done -> shoot the normal photo
            } else ShotStep.IDLE                              // cancelled -> reset
            ShotStep.PHOTO -> if (success) ShotStep.DONE else ShotStep.IDLE
            else -> step
        }
    }

    // Helper closures to launch each shot (defined here so they capture the launcher).
    fun launchSelfie() {
        step = ShotStep.SELFIE
        takePicture.launch(uriFor(context, SELFIE_FILE))
    }
    fun launchPhoto() {
        step = ShotStep.PHOTO
        takePicture.launch(uriFor(context, PHOTO_FILE))
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Photo test — two shots", fontSize = 22.sp)
        Spacer(modifier = Modifier.height(16.dp))

        when (step) {
            ShotStep.IDLE -> Button(onClick = { launchSelfie() }) { Text("Start: selfie, then photo") }
            ShotStep.SELFIE -> Text("Taking selfie… (flip to the front camera)")
            ShotStep.PHOTO -> Button(onClick = { launchPhoto() }) { Text("Now take the second photo") }
            ShotStep.DONE -> {
                Text("Both shots taken:", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                loadBitmap(context, SELFIE_FILE)?.asImageBitmap()?.let {
                    Image(bitmap = it, contentDescription = "selfie", modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(12.dp))
                loadBitmap(context, PHOTO_FILE)?.asImageBitmap()?.let {
                    Image(bitmap = it, contentDescription = "photo", modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launchSelfie() }) { Text("Retake") }
            }
        }
    }
}

// Advances into the second shot right after the first returns. Kept top-level to avoid
// capturing Compose state; it just launches — the state transition is returned by the caller.
private fun takePictureNext(context: Context, next: () -> ShotStep): ShotStep {
    // NOTE: the actual launch of the 2nd shot is triggered by the button in the PHOTO step,
    // so the user gets a beat between the two camera openings (less jarring than auto-chaining).
    return next()
}