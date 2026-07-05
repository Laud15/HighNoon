package it.diunipi.sam.highnoon.game

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Saves a photo into the shared gallery via MediaStore. On Android 10+ (we're minSdk 33)
// this needs NO storage permission: the system indexes the image so the gallery shows it
// (Lez. 20: MediaStore is the system's media index). Blocking I/O -> off the UI thread.
object GallerySaver {

    // Returns true on success. Copies the app-private file into Pictures/HighNoon.
    suspend fun saveToGallery(context: Context, sourceFile: File, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!sourceFile.exists()) return@withContext false
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return@withContext false

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HighNoon")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } ?: return@withContext false
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)   // clean up a half-written entry
                false
            } finally {
                bitmap.recycle()
            }
        }

    // Overload for the loser: the received photos are Bitmaps in memory, not files.
    suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HighNoon")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } ?: return@withContext false
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                false
            }
        }
}