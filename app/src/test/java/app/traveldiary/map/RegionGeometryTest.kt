package app.traveldiary.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the real region shapes shipped in the app. */
class RegionGeometryTest {

    private val regions = RegionGeometry.read(File("src/main/assets/map/regions.bin").readBytes())

    private fun regionAt(latitude: Double, longitude: Double) =
        regions.regionAt(MillerProjection.x(longitude), MillerProjection.y(latitude))

    @Test
    fun loadsTheRegionsOfEveryContinent() {
        assertTrue(regions.regions.size > 3000)
        assertTrue(regions.regions.all { it.rings.isNotEmpty() && it.rings.all { ring -> ring.size >= 6 } })
        listOf("IT", "US", "BR", "AU", "JP", "ZA").forEach { country ->
            assertTrue(country, regions.byCountry.getValue(country).size > 5)
        }
    }

    @Test
    fun findsRegionsAtCoordinates() {
        assertEquals("IT.09", regionAt(45.46, 9.19)) // Milan, Lombardy
        assertEquals("IT.07", regionAt(41.90, 12.50)) // Rome, Lazio
        assertEquals("ES.56", regionAt(41.39, 2.17)) // Barcelona, Catalonia
        assertEquals("DE.02", regionAt(48.14, 11.58)) // Munich, Bavaria
        assertEquals("US.TX", regionAt(30.27, -97.74)) // Austin, Texas
        assertEquals("GB.SCT", regionAt(55.95, -3.19)) // Edinburgh, Scotland
        assertEquals("BR.27", regionAt(-23.55, -46.63)) // São Paulo
        assertNull(regionAt(43.0, 5.0)) // Gulf of Lion
    }

    @Test
    fun mergesSmallerUnitsIntoTheirRegion() {
        // Natural Earth splits Italy into provinces and France into departments: one shape each here.
        assertTrue(regions.byCountry.getValue("IT").size == 20)
        assertTrue(regions.byCountry.getValue("FR").size == 13)
        assertEquals("FR.11", regionAt(48.86, 2.35)) // Paris, Île-de-France
        assertEquals("FR.11", regionAt(48.80, 2.13)) // Versailles, another department of Île-de-France
    }

    @Test
    fun placesOnTheCoastGetTheNearestRegionOfTheirCountry() {
        // Venice and Honolulu are just off the simplified coasts.
        assertEquals("IT.20", regions.regionOf("IT", 45.4371, 12.3326))
        assertEquals("US.HI", regions.regionOf("US", 21.3069, -157.8583))
        // Never a region of another country, nor one far away.
        assertNull(regions.regionOf("FR", 45.46, 9.19))
        assertNull(regions.regionOf("IT", 43.0, 5.0))
    }

    @Test
    fun splitsCodesIntoCountries() {
        assertEquals("IT", RegionGeometry.countryOf("IT.09"))
        assertEquals("GB", RegionGeometry.countryOf("GB.SCT"))
    }
}
