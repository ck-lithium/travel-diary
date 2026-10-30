package app.traveldiary.data.diary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {

    private val lisbon = PlaceWithVisits(
        place = PlaceEntity(1, 2267057, "Lisbon", "Lisbon", "PT", 38.72, -9.13, createdAt = 1_000),
        visits = listOf(
            VisitEntity(1, 1, LocalDate.of(2024, 5, 12), LocalDate.of(2024, 5, 18), "Pastéis de nata \"every\" day", 2_000),
            VisitEntity(2, 1, null, null, "", 3_000),
        ),
    )
    private val customPlace = PlaceWithVisits(
        place = PlaceEntity(2, null, "Plitvice Lakes", null, "HR", 44.88, 15.62, createdAt = 4_000),
        visits = emptyList(),
    )

    @Test
    fun roundTrip() {
        val json = DiaryBackup.encode(listOf(lisbon, customPlace), exportedAt = 0)
        val places = DiaryBackup.decode(json)

        assertEquals(2, places.size)
        assertEquals(lisbon.place.copy(id = 0), places[0].toEntity())
        assertEquals(customPlace.place.copy(id = 0), places[1].toEntity())
        assertEquals(
            lisbon.visits.map { it.copy(id = 0, placeId = 7) },
            places[0].visits.map { it.toEntity(placeId = 7) },
        )
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(InvalidBackupException::class.java) { DiaryBackup.decode("not json") }
        assertThrows(InvalidBackupException::class.java) { DiaryBackup.decode("""{"places": []}""") }
        assertThrows(InvalidBackupException::class.java) {
            DiaryBackup.decode("""{"app": "other-app", "exportedAt": "x", "places": []}""")
        }
    }

    @Test
    fun rejectsNewerFormats() {
        assertThrows(InvalidBackupException::class.java) {
            DiaryBackup.decode("""{"app": "travel-diary", "formatVersion": 99, "exportedAt": "x", "places": []}""")
        }
    }

    @Test
    fun rejectsInvalidDates() {
        val json = """
            {"app": "travel-diary", "formatVersion": 1, "exportedAt": "x", "places": [
              {"name": "Rome", "countryCode": "IT", "latitude": 41.9, "longitude": 12.5,
               "visits": [{"startDate": "2024-13-45"}]}
            ]}
        """.trimIndent()
        assertThrows(InvalidBackupException::class.java) { DiaryBackup.decode(json) }
    }

    @Test
    fun ignoresUnknownFields() {
        val json = """
            {"app": "travel-diary", "formatVersion": 1, "exportedAt": "x", "future": true, "places": [
              {"name": "Rome", "countryCode": "IT", "latitude": 41.9, "longitude": 12.5, "rating": 5}
            ]}
        """.trimIndent()
        assertEquals("Rome", DiaryBackup.decode(json).single().name)
    }

    @Test
    fun keepsHomePeriods() {
        val home = lisbon.copy(
            homePeriods = listOf(
                HomePeriodEntity(1, 1, LocalDate.of(2020, 9, 1), LocalDate.of(2023, 7, 31), 5_000),
                HomePeriodEntity(2, 1, LocalDate.of(2025, 1, 1), null, 6_000),
            ),
        )

        val place = DiaryBackup.decode(DiaryBackup.encode(listOf(home), exportedAt = 0)).single()

        assertEquals(
            home.homePeriods.map { it.copy(id = 0, placeId = 7) },
            place.homePeriods.map { it.toEntity(placeId = 7) },
        )
    }

    @Test
    fun readsBackupsMadeBeforeHomes() {
        val json = """
            {"app": "travel-diary", "formatVersion": 1, "exportedAt": "x", "places": [
              {"name": "Rome", "countryCode": "IT", "latitude": 41.9, "longitude": 12.5}
            ]}
        """.trimIndent()
        assertEquals(emptyList<BackupHomePeriod>(), DiaryBackup.decode(json).single().homePeriods)
    }

    @Test
    fun rejectsHomePeriodsWithInvalidDates() {
        val json = """
            {"app": "travel-diary", "formatVersion": 1, "exportedAt": "x", "places": [
              {"name": "Rome", "countryCode": "IT", "latitude": 41.9, "longitude": 12.5,
               "homePeriods": [{"startDate": "last summer"}]}
            ]}
        """.trimIndent()
        assertThrows(InvalidBackupException::class.java) { DiaryBackup.decode(json) }
    }
}
