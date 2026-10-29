package app.traveldiary.map

import androidx.compose.ui.geometry.Rect
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the real map shipped in the app. */
class FlagLayoutTest {

    private val world = WorldGeometry.read(File("src/main/assets/map/world.bin").readBytes())

    private fun parts(code: String) = checkNotNull(world[code]).flagParts()

    private fun List<FlagPart>.indexAt(latitude: Double, longitude: Double): Int {
        val x = MillerProjection.x(longitude)
        val y = MillerProjection.y(latitude)
        return indexOfFirst { CountryGeometry("", it.rings).contains(x, y) }
    }

    @Test
    fun everyRingGetsExactlyOneFlag() {
        world.countries.forEach { country ->
            assertEquals(country.code, country.rings.size, country.flagParts().sumOf { it.rings.size })
        }
    }

    @Test
    fun coastalIslandsShareTheMainlandFlag() {
        val italy = parts("IT")
        assertEquals(1, italy.size)
        assertEquals(checkNotNull(world["IT"]).mainBounds, italy.single().frame)
        assertEquals(1, parts("GR").size)
    }

    @Test
    fun farAwayTerritoriesGetTheirOwnFlag() {
        val us = parts("US")
        val denver = us.indexAt(39.74, -104.99)
        val hawaii = us.indexAt(19.6, -155.5) // Mauna Kea, Big Island
        assertTrue(denver >= 0 && hawaii >= 0)
        assertNotEquals(denver, hawaii)

        val spain = parts("ES")
        assertNotEquals(spain.indexAt(40.42, -3.70), spain.indexAt(28.29, -16.63)) // Madrid, Tenerife
    }

    @Test
    fun framesCoverTheirPart() {
        world.countries.flatMap { it.flagParts() }.forEach { part ->
            val bounds = part.rings.map { CountryGeometry("", listOf(it)).bounds }.maxBy { it.width * it.height }
            assertTrue(part.frame.contains(bounds))
        }
    }

    @Test
    fun narrowCountriesCropTheFlagInsteadOfSqueezingIt() {
        val chile = parts("CL").first { it.frame.width < it.frame.height }
        assertEquals(FLAG_ASPECT / FLAG_MAX_STRETCH, chile.frame.width / chile.frame.height, 0.001f)
    }

    @Test
    fun framesKeepShapesCloseToTheFlag() {
        val square = Rect(0f, 0f, 100f, 100f)
        assertEquals(square, flagFrame(square))

        val wide = Rect(0f, 0f, 400f, 100f)
        val frame = flagFrame(wide)
        assertEquals(400f, frame.width, 0.001f)
        assertEquals(400f / (FLAG_ASPECT * FLAG_MAX_STRETCH), frame.height, 0.001f)
        assertEquals(wide.center, frame.center)
    }

    private fun Rect.contains(other: Rect): Boolean {
        val epsilon = 0.01f
        return left <= other.left + epsilon && top <= other.top + epsilon &&
            right >= other.right - epsilon && bottom >= other.bottom - epsilon
    }
}
