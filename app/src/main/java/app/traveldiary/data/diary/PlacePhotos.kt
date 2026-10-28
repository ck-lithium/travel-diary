package app.traveldiary.data.diary

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The places' photos. Each one is a JPEG copy of the picked image, scaled down and turned upright,
 * kept in the app's own storage so it works offline and survives the original being deleted.
 */
class PlacePhotos(private val directory: File, private val contentResolver: ContentResolver) {

    fun file(name: String): File = File(directory, name)

    /** Copies the image at [source] into the diary and returns the new photo's file name. */
    suspend fun save(source: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decode(source)
        val name = "${UUID.randomUUID()}.jpg"
        val partial = File(directory, "$name.part")
        try {
            directory.mkdirs()
            partial.outputStream().use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    throw IOException("Cannot encode $source")
                }
            }
            if (!partial.renameTo(file(name))) throw IOException("Cannot store $name")
        } finally {
            partial.delete()
            bitmap.recycle()
        }
        name
    }

    suspend fun delete(name: String?) {
        if (name != null) withContext(Dispatchers.IO) { file(name).delete() }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun decode(source: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(source).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $source")

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight)) }
        val bitmap = open(source).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot decode $source")
        return bitmap.uprightAndScaled(open(source).use(::orientation))
    }

    private fun open(source: Uri): InputStream =
        contentResolver.openInputStream(source) ?: throw IOException("Cannot open $source")

    companion object {
        /** Longest side of a stored photo, in pixels: sharp enough for a full-screen background. */
        const val MAX_SIZE = 2048
        private const val JPEG_QUALITY = 85

        /** The largest power of two that still decodes [longestSide] to at least [MAX_SIZE] pixels. */
        internal fun sampleSize(longestSide: Int): Int {
            var sample = 1
            while (longestSide / (sample * 2) >= MAX_SIZE) sample *= 2
            return sample
        }
    }
}

/** EXIF orientation of a photo: how the camera says it must be turned to be upright. */
private fun orientation(stream: InputStream): Int = try {
    ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
} catch (e: Exception) {
    // Unreadable metadata must not block the photo: keep it as it is.
    ExifInterface.ORIENTATION_NORMAL
}

/** Turns the bitmap upright and scales it down to [PlacePhotos.MAX_SIZE] in a single pass. */
private fun Bitmap.uprightAndScaled(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
    }
    val scale = PlacePhotos.MAX_SIZE.toFloat() / maxOf(width, height)
    if (scale < 1f) matrix.postScale(scale, scale)
    if (matrix.isIdentity) return this
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { if (it !== this) recycle() }
}
