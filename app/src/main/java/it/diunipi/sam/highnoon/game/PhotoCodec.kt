package it.diunipi.sam.highnoon.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import it.diunipi.sam.highnoon.Config
import java.io.ByteArrayOutputStream
import kotlin.math.max

// Turns captured photos into a compact base64 STRING for the socket, and back into Bitmaps.
// base64 = arbitrary bytes encoded as plain text, so a photo travels like any other text
// message (no binary framing needed). We downscale + compress first so the string stays small.
object PhotoCodec {


    // Decode a saved photo file, downscale it, compress to JPEG, return base64 text.
    fun encodeFileToBase64(path: String): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null

        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > Config.Photo.MAX_SIDE) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(path, opts) ?: return null

        val jpegBytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, Config.Photo.JPEG_QUALITY, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
        // NO_WRAP: no line breaks in the output, so it stays a single clean line for the socket.
        return Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
    }

    // Rebuild a Bitmap from a received base64 string (for display on the loser's screen).
    fun decodeBase64(text: String): Bitmap? =
        try {
            val bytes = Base64.decode(text, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: IllegalArgumentException) {
            null
        }
}
