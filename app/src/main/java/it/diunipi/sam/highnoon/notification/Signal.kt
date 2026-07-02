package it.diunipi.sam.highnoon.notification

import android.os.VibrationAttributes
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// Fa vibrare il telefono. La via per ottenere il Vibrator e' cambiata
// in Android 12 (API 31), quindi gestiamo entrambi i casi (Lez. 01: frammentazione).
fun vibrate(context: Context) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Da Android 13: specifichiamo l'ATTRIBUTO della vibrazione.
            // USAGE_ALARM = vibrazione importante, il sistema la rispetta
            // anche in modalita' silenziosa (e' un "allarme", il via del duello).
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            vibrator.vibrate(effect)
        }
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }
}


// Il "via" del duello: per ora solo vibrazione.
fun fireSignal(context: Context) {
    vibrate(context)
}