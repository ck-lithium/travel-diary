package app.traveldiary.data.regions

import app.traveldiary.data.diary.PlaceEntity
import app.traveldiary.map.RegionGeometry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionIndexTest {

    private val names = mapOf("IT.09" to "Lombardy", "IT.20" to "Veneto", "ES.29" to "Madrid", "US.GA" to "Georgia", "GE.51" to "Tbilisi")
    private val shapes = RegionGeometry.read(File("src/main/assets/map/regions.bin").readBytes())

    private fun place(name: String, region: String?, country: String, latitude: Double, longitude: Double) =
        PlaceEntity(0, null, name, region, country, latitude, longitude, createdAt = 0)

    @Test
    fun usesTheRegionNameOfTheCity() {
        val index = RegionIndex(names)
        assertEquals("IT.09", index.regionOf(place("Bergamo", "Lombardy", "IT", 45.70, 9.67)))
        // The same name in another country is another region.
        assertEquals("US.GA", index.regionOf(place("Atlanta", "Georgia", "US", 33.75, -84.39)))
        assertNull(index.regionOf(place("Nowhere", "Atlantis", "IT", 45.70, 9.67)))
    }

    @Test
    fun withoutShapesPlacesWithoutRegionNameStayUnknown() {
        assertNull(RegionIndex(names).regionOf(place("Madrid", null, "ES", 40.42, -3.70)))
    }

    @Test
    fun placesWithoutRegionNameAreFoundOnTheShapes() {
        val index = RegionIndex(names, shapes)
        // GeoNames saves no region for a city named like it.
        assertEquals("ES.29", index.regionOf(place("Madrid", null, "ES", 40.42, -3.70)))
        // A pin dropped on the map.
        assertEquals("IT.20", index.regionOf(place("Country house", null, "IT", 45.55, 11.55)))
        // A pin at sea.
        assertNull(index.regionOf(place("Boat", null, "IT", 39.0, 15.0)))
    }

    @Test
    fun namesRegions() {
        val index = RegionIndex(names)
        assertEquals("Lombardy", index.name("IT.09"))
        assertEquals("XX.01", index.name("XX.01"))
    }
}
