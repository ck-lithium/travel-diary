package app.traveldiary.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** The flag images in assets/flags are 4:3. */
const val FLAG_ASPECT = 4f / 3f

/** How much a flag may be squeezed or widened to follow the shape of a country; beyond that it is cropped. */
const val FLAG_MAX_STRETCH = 2f

/** Rings within this share of the largest part's size count as close to each other. */
private const val NEARBY = 0.1f

/** Some rings of a country painted with one copy of its flag, stretched over [frame]. */
class FlagPart(val rings: List<FloatArray>, val frame: Rect)

/**
 * Splits a country into the parts that get a flag each. Rings close to each other share one flag,
 * fitted to the biggest of them, so coastal islands continue the colours of the mainland while
 * far-away territories such as Hawaii or the Canary Islands get a flag of their own.
 */
fun CountryGeometry.flagParts(): List<FlagPart> {
    val margin = NEARBY * maxOf(mainBounds.width, mainBounds.height)
    // Union-find over the rings: every group of nearby rings ends up under one root.
    val parent = IntArray(rings.size) { it }
    fun root(ring: Int): Int {
        var r = ring
        while (parent[r] != r) r = parent[r]
        return r
    }
    for (i in rings.indices) {
        val near = ringBounds[i].inflate(margin)
        for (j in 0 until i) {
            if (near.overlaps(ringBounds[j])) parent[root(i)] = root(j)
        }
    }
    return rings.indices.groupBy(::root).values.map { group ->
        val biggest = group.maxBy { ringBounds[it].width * ringBounds[it].height }
        FlagPart(group.map { rings[it] }, flagFrame(ringBounds[biggest]))
    }
}

/**
 * Where to draw a flag so that it covers [bounds]: stretched to the same shape when that is close
 * to 4:3, otherwise as stretched as allowed and cropped around the centre (Chile, Norway).
 */
fun flagFrame(bounds: Rect): Rect {
    val aspect = (bounds.width / bounds.height).coerceIn(FLAG_ASPECT / FLAG_MAX_STRETCH, FLAG_ASPECT * FLAG_MAX_STRETCH)
    val width = maxOf(bounds.width, bounds.height * aspect)
    val height = width / aspect
    return Rect(Offset(bounds.center.x - width / 2, bounds.center.y - height / 2), Size(width, height))
}
