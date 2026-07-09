package it.diunipi.sam.highnoon.ui

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "CameraX"

// Get the CameraX provider as a suspend call (wraps its ListenableFuture), so we acquire it
// inside a Compose effect without blocking the main thread
private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }

// Reusable in-app camera: live preview + capture button. Because the camera lives INSIDE our
// screen (CameraX), the app never goes to the background, so an open Wi-Fi Direct socket stays
// alive — that's what the Intent approach could not guarantee
@Composable
fun CameraCapture(
    lensFacing: Int,  // CameraSelector.LENS_FACING_FRONT or LENS_FACING_BACK
    outputFile: File,
    captureLabel: String,
    modifier: Modifier = Modifier,
    onCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Hold the provider we obtain in the effect, so onDispose can reuse it instead of
    // requesting (and blocking on) a fresh one
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Bind Preview + ImageCapture to this lens; re-runs if the lens changes (front <-> back).
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProviderRef.value = cameraProvider
        cameraProvider.unbindAll()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        } catch (e: Exception) {
            Log.e(TAG, "bindToLifecycle failed: ${e.message}")
        }
    }

    // Release the camera when this composable leaves (e.g. the RESULT screen is dismissed).
    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProviderRef.value?.unbindAll() }
        }
    }

    fun takePhoto() {
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) = onCaptured(outputFile)
                override fun onError(exc: ImageCaptureException) { Log.e(TAG, "takePicture failed: ${exc.message}") }
            }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)     // portrait 4:3 camera: fills the width, height follows the ratio
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { takePhoto() }) { Text(text = captureLabel) }
    }
}