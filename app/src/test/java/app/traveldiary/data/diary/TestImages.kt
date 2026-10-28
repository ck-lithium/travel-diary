package app.traveldiary.data.diary

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.junit.rules.TemporaryFolder

/**
 * Writes a [width] x [height] JPEG, left half red and right half blue, with [orientation] in its EXIF
 * data, and returns its Uri. Needs Robolectric's native graphics.
 */
fun TemporaryFolder.jpeg(width: Int, height: Int, orientation: Int = ExifInterface.ORIENTATION_NORMAL): Uri {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(Color.BLUE)
        drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
    }
    val file = newFile()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    if (orientation != ExifInterface.ORIENTATION_NORMAL) {
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
    }
    return Uri.fromFile(file)
}
