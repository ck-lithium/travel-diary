package app.traveldiary.data.countries

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.traveldiary.map.RegionGeometry
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Who made the flag of a region, from assets/flags/regions/credits.tsv. */
data class FlagCredit(val regionCode: String, val file: String, val author: String, val licence: String, val source: String)

/**
 * Flag images shipped in assets/flags: countries rendered by tools/build_flags.sh, regions by
 * tools/build_region_flags.py.
 */
class FlagRepository(private val context: Context) {

    // Only the flags of the last call stay in memory, so asking for none frees them all.
    @Volatile
    private var loaded: Map<String, ImageBitmap> = emptyMap()

    @Volatile
    private var loadedRegions: Map<String, ImageBitmap> = emptyMap()

    /** Authors and licences of the region flags, which also tells which regions have one. */
    val regionCredits: List<FlagCredit> by lazy {
        try {
            context.assets.open("$REGION_FLAGS/credits.tsv").bufferedReader().useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val cols = line.split('\t')
                    if (cols.size >= 5) FlagCredit(cols[0], cols[1], cols[2], cols[3], cols[4]) else null
                }.toList()
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    private val regionsWithFlag: Set<String> by lazy { regionCredits.mapTo(HashSet()) { it.regionCode } }

    /** Flags of the given countries; countries without a flag image are left out. */
    suspend fun load(codes: Set<String>): Map<String, ImageBitmap> = withContext(Dispatchers.IO) {
        val previous = loaded
        codes.mapNotNull { code -> (previous[code] ?: decode("flags/${code.lowercase()}.webp"))?.let { code to it } }
            .toMap()
            .also { loaded = it }
    }

    /**
     * Flags of the given regions: their own when they have one, otherwise the flag of their country,
     * so every visited region gets painted.
     */
    suspend fun loadRegions(codes: Set<String>): Map<String, ImageBitmap> = withContext(Dispatchers.IO) {
        val previous = loadedRegions
        val own = codes.filter { it in regionsWithFlag }
            .mapNotNull { code -> (previous[code] ?: decode("$REGION_FLAGS/${code.lowercase()}.webp"))?.let { code to it } }
            .toMap()
            .also { loadedRegions = it }
        val countryFlags = load((codes - own.keys).mapTo(HashSet(), RegionGeometry::countryOf))
        own + (codes - own.keys).mapNotNull { code -> countryFlags[RegionGeometry.countryOf(code)]?.let { code to it } }
    }

    /** Frees the region flags while the map shows countries. */
    fun releaseRegions() {
        loadedRegions = emptyMap()
    }

    private fun decode(asset: String): ImageBitmap? {
        // Flags have no transparency: 16-bit colour halves the memory without visible loss.
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return try {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val REGION_FLAGS = "flags/regions"
    }
}
