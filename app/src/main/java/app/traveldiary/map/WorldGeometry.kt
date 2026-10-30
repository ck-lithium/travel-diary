package app.traveldiary.map

import androidx.compose.ui.geometry.Rect
import java.nio.ByteBuffer

/** Outline of one country in map units: a list of rings (islands and holes) as x,y pairs. */
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
                val rings = List(buffer.int) {
                    val ring = FloatArray(buffer.int * 2)
                    for (i in ring.indices step 2) {
                        val longitude = buffer.float.toDouble()
                        val latitude = buffer.float.toDouble()
                        ring[i] = MillerProjection.x(longitude)
                        ring[i + 1] = MillerProjection.y(latitude)
                    }
                    ring
                }
                CountryGeometry(code, rings)
            }
            return WorldGeometry(countries)
        }
    }
}
