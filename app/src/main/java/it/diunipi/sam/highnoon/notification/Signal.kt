package it.diunipi.sam.highnoon.notification

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// Vibrate the phone. minSdk is 33, so the modern APIs are always available
// (VibratorManager since API 31, VibrationAttributes since API 33): no legacy branches.
fun vibrate(context: Context) {
    val vibratorManager =
        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    val vibrator: Vibrator = vibratorManager.defaultVibrator

    val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)

    // USAGE_ALARM: the system respects it even in silent mode — it's the duel's "go".
    val attributes = VibrationAttributes.Builder()
        .setUsage(VibrationAttributes.USAGE_ALARM)
        .build()

    vibrator.vibrate(effect, attributes)
}

// The duel "go": for now just vibration.
fun fireSignal(context: Context) {
    vibrate(context)
}