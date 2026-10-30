package app.traveldiary.map

import app.traveldiary.data.diary.HomePeriodEntity
import app.traveldiary.data.diary.PlaceEntity
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.data.diary.VisitEntity
import app.traveldiary.map.MillerProjection.WORLD_WIDTH
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CenterOfGravityTest {

    private var nextId = 1L

    private fun place(
        name: String,
        latitude: Double,
        longitude: Double,
        vararg visits: Pair<LocalDate?, LocalDate?>,
    ): PlaceWithVisits {
        val id = nextId++
        return PlaceWithVisits(
            place = PlaceEntity(
                id = id,
                geonameId = id,
                name = name,
                region = null,
                countryCode = "XX",
                latitude = latitude,
                longitude = longitude,
                createdAt = 0,
            ),
            visits = visits.map { (start, end) ->
                VisitEntity(
                    id = nextId++,
                    placeId = id,
                    startDate = start,
                    endDate = end,
                    notes = "",
                    createdAt = 0,
                )
            },
        )
    }

    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    @Test
    fun emptyPlacesReturnsEmptyTimeline() {
        val timeline = GravityTimeline.compute(emptyList(), GravityPeriod.QUARTER)
        assertTrue(timeline.isEmpty)
        assertNull(timeline.startDate)
        assertNull(timeline.endDate)
        assertEquals(0, timeline.totalDays)
        assertTrue(timeline.trajectory.points.isEmpty())
        assertNull(timeline.trajectory.positionAt(0.5f))
    }

    @Test
    fun placesWithoutDatesReturnEmptyTimeline() {
        val places = listOf(
            place("Undated Place 1", 48.85, 2.35, null to null),
            place("Undated Place 2", 40.71, -74.0, null to null),
        )
        val timeline = GravityTimeline.compute(places, GravityPeriod.QUARTER)
        assertTrue(timeline.isEmpty)
    }

    @Test
    fun singlePlaceWithDateReturnsConstantPosition() {
        val place = place("Rome", 41.9, 12.5, date(2024, 6, 1) to date(2024, 6, 5))
        val timeline = GravityTimeline.compute(listOf(place), GravityPeriod.QUARTER)

        assertFalse(timeline.isEmpty)
        assertEquals(date(2024, 6, 1), timeline.startDate)
        assertEquals(date(2024, 6, 5), timeline.endDate)
        assertEquals(4, timeline.totalDays)
        assertEquals(1, timeline.activePlacesCount)

        val posStart = timeline.trajectory.positionAt(0f)
        val posEnd = timeline.trajectory.positionAt(1f)
        assertNotNull(posStart)
        assertNotNull(posEnd)
        assertEquals(MillerProjection.x(12.5), posStart!!.x, 1e-2f)
        assertEquals(MillerProjection.y(41.9), posStart.y, 1e-2f)
        assertEquals(posStart.x, posEnd!!.x, 1e-2f)
        assertEquals(posStart.y, posEnd.y, 1e-2f)
    }

    @Test
    fun calculatesSphericalCenterOfGravityBetweenPlaces() {
        val places = listOf(
            place("Madrid", 40.41, -3.70, date(2023, 1, 10) to date(2023, 1, 15)),
            place("Rome", 41.90, 12.50, date(2023, 7, 10) to date(2023, 7, 15)),
        )
        val timeline = GravityTimeline.compute(places, GravityPeriod.MONTH)

        assertFalse(timeline.isEmpty)
        assertEquals(date(2023, 1, 10), timeline.startDate)
        assertEquals(date(2023, 7, 15), timeline.endDate)
        assertEquals(2, timeline.activePlacesCount)

        // At progress 0 (in Jan 2023): center of gravity is at Madrid
        val startCoords = timeline.trajectory.coordinatesAt(0f)
        assertNotNull(startCoords)
        assertEquals(40.41, startCoords!!.first, 0.5)
        assertEquals(-3.70, startCoords.second, 0.5)

        // At progress 1 (in July 2023): center of gravity is at Rome
        val endCoords = timeline.trajectory.coordinatesAt(1f)
        assertNotNull(endCoords)
        assertEquals(41.90, endCoords!!.first, 0.5)
        assertEquals(12.50, endCoords.second, 0.5)

        // Halfway in between (approx April 2023): center of gravity is between Spain and Italy (western Mediterranean)
        val midCoords = timeline.trajectory.coordinatesAt(0.5f)
        assertNotNull(midCoords)
        assertTrue(midCoords!!.first in 40.0..42.5)
        assertTrue(midCoords.second in -3.70..12.50)
    }

    @Test
    fun handlesAntimeridianCrossingWithoutCrossWorldJumps() {
        // Fiji (~178° E) to Samoa (~-171° W / 188° E) across the 180° meridian
        val places = listOf(
            place("Fiji", -18.0, 178.0, date(2023, 3, 1) to date(2023, 3, 5)),
            place("Samoa", -13.8, -171.7, date(2023, 9, 1) to date(2023, 9, 5)),
        )
        val timeline = GravityTimeline.compute(places, GravityPeriod.HALF_YEAR)

        assertFalse(timeline.isEmpty)

        // The center of gravity in the Pacific should be near longitude 180° / -180°, NEVER near 0°
        val midCoords = timeline.trajectory.coordinatesAt(0.5f)
        assertNotNull(midCoords)
        val lon = midCoords!!.second
        assertTrue(
            "Longitude should be near antimeridian (close to 180 or -180), got $lon",
            lon > 170.0 || lon < -165.0,
        )

        // Segments should NEVER cross more than half the map width in a single line
        for (segment in timeline.trajectory.segments) {
            val dx = kotlin.math.abs(segment.end.x - segment.start.x)
            assertTrue(
                "Segment $segment should not cross antimeridian with dx = $dx",
                dx <= WORLD_WIDTH / 2f + 1e-2f,
            )
        }
    }

    @Test
    fun allTimePeriodAccumulatesCenterOfGravity() {
        val places = listOf(
            place("Paris", 48.85, 2.35, date(2020, 1, 1) to date(2020, 1, 5)),
            place("Tokyo", 35.68, 139.69, date(2023, 1, 1) to date(2023, 1, 5)),
        )
        val timeline = GravityTimeline.compute(places, GravityPeriod.ALL)

        // Start at Paris
        val startCoords = timeline.trajectory.coordinatesAt(0f)
        assertEquals(48.85, startCoords!!.first, 0.5)
        assertEquals(2.35, startCoords.second, 0.5)

        // End incorporates both Paris and Tokyo equally.
        // On a 3D sphere, the center of gravity between Paris (~49°N, 2°E) and Tokyo (~36°N, 140°E)
        // follows the great circle path curving up over Siberia (~67.5°N, 86.1°E).
        val endCoords = timeline.trajectory.coordinatesAt(1f)
        assertNotNull(endCoords)
        assertEquals(67.5, endCoords!!.first, 1.0)
        assertEquals(86.1, endCoords.second, 1.0)
    }

    @Test
    fun dateAtMapsProgressToCalendarDate() {
        val start = date(2024, 1, 1)
        val end = date(2024, 1, 11) // 10 days
        val trajectory = GravityTrajectory(points = listOf(), segments = listOf())

        assertEquals(date(2024, 1, 1), trajectory.dateAt(0f, start, 10))
        assertEquals(date(2024, 1, 6), trajectory.dateAt(0.5f, start, 10))
        assertEquals(date(2024, 1, 11), trajectory.dateAt(1f, start, 10))
    }

    private fun PlaceWithVisits.livedIn(start: LocalDate, end: LocalDate?) =
        copy(homePeriods = listOf(HomePeriodEntity(nextId++, place.id, start, end, createdAt = 0)))

    @Test
    fun homeAloneMakesATimelineUntilToday() {
        val rome = place("Rome", 41.9, 12.5).livedIn(date(2025, 1, 1), end = null)

        val timeline = GravityTimeline.compute(listOf(rome), GravityPeriod.QUARTER, today = date(2025, 1, 31))

        assertEquals(date(2025, 1, 1), timeline.startDate)
        assertEquals(date(2025, 1, 31), timeline.endDate)
        val last = timeline.trajectory.points.last()
        assertEquals(41.9, last.latitude, 1e-6)
        assertEquals(12.5, last.longitude, 1e-6)
    }

    @Test
    fun homeDaysInTheFutureDoNotCount() {
        val rome = place("Rome", 41.9, 12.5).livedIn(date(2025, 1, 1), date(2025, 12, 31))

        val timeline = GravityTimeline.compute(listOf(rome), GravityPeriod.QUARTER, today = date(2025, 1, 10))

        assertEquals(date(2025, 1, 10), timeline.endDate)
    }

    @Test
    fun tripDaysAreNotAlsoSpentAtHome() {
        // Home west of Greenwich, a trip as far east of it on the second day: one day each, so the
        // average sits on the meridian. Counting the trip day at home too would pull it west.
        val home = place("Home", 0.0, -10.0).livedIn(date(2025, 1, 1), date(2025, 1, 2))
        val trip = place("Trip", 0.0, 10.0, date(2025, 1, 2) to date(2025, 1, 2))

        val timeline = GravityTimeline.compute(listOf(home, trip), GravityPeriod.ALL, today = date(2025, 6, 1))

        assertEquals(date(2025, 1, 2), timeline.endDate)
        assertEquals(0.0, timeline.trajectory.points.last().longitude, 1e-6)
    }
}

