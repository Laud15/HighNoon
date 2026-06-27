package it.diunipi.sam.highnoon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

// Identificatori del nostro canale e della notifica.
// L'ID del canale e' una stringa che scegliamo noi; l'ID della notifica
// e' un intero che serve al NotificationManager per distinguerla (Lez. 12).
private const val CHANNEL_ID = "duel_signal_channel"
private const val NOTIFICATION_ID = 1

// Crea il canale di notifica (Lez. 12). Va fatto prima di mostrare notifiche,
// ma e' innocuo richiamarlo piu' volte: se il canale esiste gia', non fa nulla.
// Serve solo da Android 8 (API 26) in su; sotto, i canali non esistono.
fun createSignalChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Segnale del duello",                       // nome visibile all'utente
            NotificationManager.IMPORTANCE_HIGH         // alta priorita' = piu' invasiva
        ).apply {
            description = "Notifica che segnala il momento di estrarre"
            enableVibration(true)
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

fun showSignalNotification(context: Context) {
    // Costruzione con il Builder (Lez. 12: si usano molti metadati).
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert) // icona di sistema, per ora
        .setContentTitle("DRAW!")
        .setContentText("Estrai adesso!")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)   // si chiude da sola quando l'utente la tocca
        .build()

    // Consegna al NotificationManager con l'ID (Lez. 12).
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID, notification)
}

fun vibrate(context: Context) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Vibra per 500 ms. VibrationEffect e' il modo moderno (da API 26).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }
}

fun playSound(context: Context) {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val ringtone = RingtoneManager.getRingtone(context, uri)
    ringtone.play()
}

fun fireSignal(context: Context) {
    vibrate(context)
}


