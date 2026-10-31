package app.traveldiary.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.nio.ByteBuffer
import kotlin.math.hypot

/**
 * Outline of one country in map units: a list of rings (islands and holes) as x,y pairs. Regions use
 * it too, with their GeoNames code ("IT.09") as [code].
 */
class CountryGeometry(val code: String, val rings: List<FloatArray>) {
    /** Bounds of each ring, in the order of [rings]. */
    val ringBounds: List<Rect>

    val bounds: Rect

    /** Bounds of the largest part, to zoom on a country without its far-away islands. */
    val mainBounds: Rect

    init {
        ringBounds = rings.map(::boundsOf)
        bounds = Rect(
            left = ringBounds.minOf { it.left },
            top = ringBounds.minOf { it.top },
            right = ringBounds.maxOf { it.right },
            bottom = ringBounds.maxOf { it.bottom },
        )
        mainBounds = ringBounds.maxBy { it.width * it.height }
    }

    /** Even-odd point-in-polygon test, so holes such as Lesotho inside South Africa work. */
    fun contains(x: Float, y: Float): Boolean {
        if (x < bounds.left || x > bounds.right || y < bounds.top || y > bounds.bottom) return false
        var inside = false
        for (ring in rings) {
            val points = ring.size / 2
            var j = points - 1
            for (i in 0 until points) {
                val xi = ring[2 * i]
                val yi = ring[2 * i + 1]
                val xj = ring[2 * j]
                val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }

    /** Distance from a point to the nearest edge, or [Float.POSITIVE_INFINITY] when it is beyond [limit]. */
    fun distanceTo(x: Float, y: Float, limit: Float): Float {
        var best = Float.POSITIVE_INFINITY
        rings.forEachIndexed { index, ring ->
            if (!ringBounds[index].inflate(limit).contains(Offset(x, y))) return@forEachIndexed
            val points = ring.size / 2
            var j = points - 1
            for (i in 0 until points) {
                best = minOf(best, segmentDistance(x, y, ring[2 * j], ring[2 * j + 1], ring[2 * i], ring[2 * i + 1]))
                j = i
            }
        }
        return if (best <= limit) best else Float.POSITIVE_INFINITY
    }

    private fun segmentDistance(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx * dx + dy * dy
        val t = if (length2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / length2).coerceIn(0f, 1f)
        return hypot(x - (ax + t * dx), y - (ay + t * dy))
    }

    private fun boundsOf(ring: FloatArray): Rect {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (i in ring.indices step 2) {
            left = minOf(left, ring[i])
            right = maxOf(right, ring[i])
            top = minOf(top, ring[i + 1])
            bottom = maxOf(bottom, ring[i + 1])
        }
        return Rect(left, top, right, bottom)
    }
}

/** Every country shape of assets/map/world.bin, projected with [MillerProjection]. */
class WorldGeometry(val countries: List<CountryGeometry>) {
    private val byCode = countries.associateBy { it.code }

    // Small countries first, so an enclave wins over the country around it.
    private val hitTestOrder = countries.sortedBy { it.bounds.width * it.bounds.height }

    operator fun get(code: String): CountryGeometry? = byCode[code]

    /** Country at a map position, or null for the sea. */
    fun countryAt(x: Float, y: Float): String? {
        val wrappedX = MillerProjection.wrapX(x)
        return hitTestOrder.firstOrNull { it.contains(wrappedX, y) }?.code
    }

    fun countryAt(latitude: Double, longitude: Double): String? =
        countryAt(MillerProjection.x(longitude), MillerProjection.y(latitude))

    companion object {
        private const val MAGIC = 0x54444D31 // "TDM1"

        /**
         * Reads the format written by tools/build_assets.py (big-endian): magic, country count, then
         * per country a 2-letter code, ring count and per ring a point count and lon/lat float pairs.
         */
        fun read(bytes: ByteArray): WorldGeometry {
            val buffer = ByteBuffer.wrap(bytes)
            require(buffer.int == MAGIC) { "Not a Travel Diary map file" }
            val countries = List(buffer.int) {
                val code = charArrayOf(buffer.get().toInt().toChar(), buffer.get().toInt().toChar()).concatToString()
                CountryGeometry(code, buffer.readRings())
            }
            return WorldGeometry(countries)
        }
    }
}

/**
 * Every region shape of assets/map/regions.bin: the first-level regions of the countries (states,
 * provinces...) under their GeoNames code, such as "IT.09" for Lombardy. Some regions have no shape,
 * when the map data does not split their country the same way.
 */
class RegionGeometry(val regions: List<CountryGeometry>) {
    private val byCode = regions.associateBy { it.code }

    /** The regions of each country, by country code. */
    val byCountry: Map<String, List<CountryGeometry>> = regions.groupBy { countryOf(it.code) }

    // Regions do not overlap, but the bounding boxes of small ones are quicker to rule out.
    private val hitTestOrder = regions.sortedBy { it.bounds.width * it.bounds.height }

    operator fun get(code: String): CountryGeometry? = byCode[code]

    /** Region at a map position, or null at sea or in a country without region shapes. */
    fun regionAt(x: Float, y: Float): String? {
        val wrappedX = MillerProjection.wrapX(x)
        return hitTestOrder.firstOrNull { it.contains(wrappedX, y) }?.code
    }

    /**
     * Region of [countryCode] at a position: the one containing it or, for a place on the coast just
     * outside the simplified shapes, the nearest one within [maxDistance] map units.
     */
    fun regionOf(countryCode: String, x: Float, y: Float, maxDistance: Float = NEAR_COAST): String? {
        val candidates = byCountry[countryCode] ?: return null
        val wrappedX = MillerProjection.wrapX(x)
        candidates.firstOrNull { it.contains(wrappedX, y) }?.let { return it.code }
        return candidates
            .map { it to it.distanceTo(wrappedX, y, maxDistance) }
            .filter { (_, distance) -> distance.isFinite() }
            .minByOrNull { (_, distance) -> distance }
            ?.first?.code
    }

    fun regionOf(countryCode: String, latitude: Double, longitude: Double): String? =
        regionOf(countryCode, MillerProjection.x(longitude), MillerProjection.y(latitude))

    companion object {
        private const val MAGIC = 0x54445231 // "TDR1"

        /** About 15 km at the equator. */
        private const val NEAR_COAST = 0.4f

        /** "IT.09" -> "IT". */
        fun countryOf(regionCode: String): String = regionCode.substringBefore('.')

        /**
         * Reads the format written by tools/build_regions.py: the one of world.bin with "TDR1" as
         * magic, and codes of any length, each preceded by its length in one byte.
         */
        fun read(bytes: ByteArray): RegionGeometry {
            val buffer = ByteBuffer.wrap(bytes)
            require(buffer.int == MAGIC) { "Not a Travel Diary region file" }
            val regions = List(buffer.int) {
                val code = ByteArray(buffer.get().toInt() and 0xFF).also { buffer.get(it) }.decodeToString()
                CountryGeometry(code, buffer.readRings())
            }
            return RegionGeometry(regions)
        }
    }
}

/** A ring count, then for each ring a point count and lon/lat float pairs, projected to map units. */
private fun ByteBuffer.readRings(): List<FloatArray> = List(int) {
    val ring = FloatArray(int * 2)
    for (i in ring.indices step 2) {
        val longitude = float.toDouble()
        val latitude = float.toDouble()
        ring[i] = MillerProjection.x(longitude)
        ring[i + 1] = MillerProjection.y(latitude)
    }
    ring
}
