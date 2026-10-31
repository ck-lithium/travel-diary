package app.traveldiary.ui.places

import app.traveldiary.data.diary.PlaceEntity
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.data.diary.VisitEntity
import app.traveldiary.data.regions.RegionIndex
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesUiStateTest {

    private val names = mapOf("IT.09" to "Lombardy", "IT.07" to "Lazio", "ES.56" to "Catalonia")
    private val countryNames = mapOf("IT" to "Italy", "ES" to "Spain")

    private fun place(id: Long, name: String, region: String?, country: String, lastVisit: LocalDate? = null) = PlaceWithVisits(
        place = PlaceEntity(id, null, name, region, country, 0.0, 0.0, createdAt = id),
        visits = listOfNotNull(lastVisit?.let { VisitEntity(id, id, it, null, "", 0) }),
    )

    private val places = listOf(
        place(1, "Rome", "Lazio", "IT", LocalDate.of(2024, 5, 1)),
        place(2, "Milan", "Lombardy", "IT", LocalDate.of(2023, 3, 1)),
        place(3, "Bergamo", "Lombardy", "IT", LocalDate.of(2025, 8, 1)),
        place(4, "Somewhere", null, "IT"),
        place(5, "Barcelona", "Catalonia", "ES"),
    )

    @Test
    fun groupsPlacesByRegionWithinTheirCountry() {
        val state = placesUiState(places, countryNames::getValue, RegionIndex(names))
        assertEquals(listOf("Italy", "Spain"), state.regions.map { it.countryName })

        val italy = state.regions.first()
        // Alphabetical, with the places of an unknown region last.
        assertEquals(listOf("Lazio", "Lombardy", null), italy.regions.map { it.name })
        val lombardy = italy.regions[1]
        assertEquals("IT.09", lombardy.code)
        assertEquals(listOf("Bergamo", "Milan"), lombardy.places.map { it.name })
        assertEquals(LocalDate.of(2025, 8, 1), lombardy.lastVisit)
        assertNull(italy.regions.last().code)
        assertEquals(listOf("Somewhere"), italy.regions.last().places.map { it.name })
    }

    @Test
    fun noRegionsBeforeTheIndexIsReady() {
        val state = placesUiState(places, countryNames::getValue)
        assertTrue(state.regions.isEmpty())
        assertEquals(2, state.countries.size)
    }
}
