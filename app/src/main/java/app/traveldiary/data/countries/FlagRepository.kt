package app.traveldiary.data.countries

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Flag images shipped in assets/flags, rendered by tools/build_flags.sh. */
class FlagRepository(private val context: Context) {

    // Only the flags of the last call stay in memory, so asking for none frees them all.
    @Volatile
    private var loaded: Map<String, ImageBitmap> = emptyMap()

    /** Flags of the given countries; countries without a flag image are left out. */
    suspend fun load(codes: Set<String>): Map<String, ImageBitmap> = withContext(Dispatchers.IO) {
        val previous = loaded
        codes.mapNotNull { code -> (previous[code] ?: decode(code))?.let { code to it } }
            .toMap()
            .also { loaded = it }
    }

    private fun decode(code: String): ImageBitmap? {
        // Flags have no transparency: 16-bit colour halves the memory without visible loss.
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return try {
            context.assets.open("flags/${code.lowercase()}.webp").use { BitmapFactory.decodeStream(it, null, options) }
                ?.asImageBitmap()
        } catch (_: IOException) {
            null
        }
    }
}
