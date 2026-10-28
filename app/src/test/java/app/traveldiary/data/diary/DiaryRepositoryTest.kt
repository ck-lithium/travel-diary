package app.traveldiary.data.diary

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.traveldiary.data.cities.CityEntity
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: DiaryDatabase
    private lateinit var photoDirectory: File
    private lateinit var photos: PlacePhotos
    private lateinit var diary: DiaryRepository

    private val paris = CityEntity(2988507, "Paris", "Île-de-France", "FR", 48.85, 2.35, 2_138_551, "paris")
    private val june = VisitDraft(LocalDate.of(2023, 6, 1), LocalDate.of(2023, 6, 4), "  Louvre and croissants  ")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java).build()
        photoDirectory = folder.newFolder("photos")
        photos = PlacePhotos(photoDirectory, context.contentResolver)
        diary = DiaryRepository(database, photos, clock = { 1_700_000_000_000 })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addingACityTwiceAddsASecondVisit() = runBlocking {
        val first = diary.addVisit(paris, june)
        val second = diary.addVisit(paris, VisitDraft(null, null, ""))
        assertEquals(first, second)

        val place = diary.places.first().single()
        assertEquals("Paris", place.place.name)
        assertEquals(2988507L, place.place.geonameId)
        assertEquals(2, place.visits.size)
        assertEquals("Louvre and croissants", place.sortedVisits.first().notes)
        assertNull(place.sortedVisits.last().startDate)
        assertEquals(setOf(2988507L), diary.visitedCityIds.first())
    }

    @Test
    fun customPlacesHaveNoCity() = runBlocking {
        val id = diary.addCustomPlace(" Plitvice Lakes ", "HR", 44.88, 15.62, june)
        val place = diary.getPlace(id)!!
        assertEquals("Plitvice Lakes", place.name)
        assertNull(place.geonameId)
        assertTrue(diary.visitedCityIds.first().isEmpty())
    }

    @Test
    fun editsAndDeletesVisits() = runBlocking {
        val placeId = diary.addVisit(paris, june)
        val visit = diary.place(placeId).first()!!.visits.single()

        diary.updateVisit(visit.id, VisitDraft(LocalDate.of(2024, 1, 1), null, "New year"))
        val updated = diary.getVisit(visit.id)!!
        assertEquals(LocalDate.of(2024, 1, 1), updated.startDate)
        assertNull(updated.endDate)
        assertEquals("New year", updated.notes)

        diary.deleteVisit(visit.id)
        assertTrue(diary.place(placeId).first()!!.visits.isEmpty())
    }

    @Test
    fun deletingAPlaceDeletesItsVisits() = runBlocking {
        val placeId = diary.addVisit(paris, june)
        val visitId = diary.place(placeId).first()!!.visits.single().id

        diary.deletePlace(placeId)

        assertNull(diary.place(placeId).first())
        assertNull(diary.getVisit(visitId))
        assertTrue(diary.places.first().isEmpty())
    }

    @Test
    fun exportThenImportRestoresTheDiary() = runBlocking {
        diary.addVisit(paris, june)
        diary.addCustomPlace("Plitvice Lakes", "HR", 44.88, 15.62, VisitDraft(null, null, "Waterfalls"))
        val backup = diary.exportBackup()

        diary.deletePlace(diary.places.first().first().place.id)
        assertEquals(2, diary.importBackup(backup))

        val places = diary.places.first()
        assertEquals(listOf("Paris", "Plitvice Lakes"), places.map { it.place.name })
        assertEquals(june.startDate, places[0].visits.single().startDate)
        assertEquals("Waterfalls", places[1].visits.single().notes)
    }

    @Test
    fun invalidBackupLeavesTheDiaryUntouched() = runBlocking {
        diary.addVisit(paris, june)
        assertThrows(InvalidBackupException::class.java) { runBlocking { diary.importBackup("{}") } }
        assertEquals(1, diary.places.first().size)
    }

    @Test
    fun aPhotoCanBeAddedReplacedAndRemoved() = runBlocking {
        val placeId = diary.addVisit(paris, june)

        diary.setPlacePhoto(placeId, folder.jpeg(64, 48))
        val first = diary.getPlace(placeId)!!.photo!!
        assertEquals(setOf(first), storedPhotos())

        diary.setPlacePhoto(placeId, folder.jpeg(64, 48))
        val second = diary.getPlace(placeId)!!.photo!!
        assertNotEquals(first, second)
        assertEquals(setOf(second), storedPhotos())

        diary.removePlacePhoto(placeId)
        assertNull(diary.getPlace(placeId)!!.photo)
        assertTrue(storedPhotos().isEmpty())
    }

    @Test
    fun deletingAPlaceDeletesItsPhoto() = runBlocking {
        val placeId = diary.addVisit(paris, june)
        diary.setPlacePhoto(placeId, folder.jpeg(64, 48))

        diary.deletePlace(placeId)

        assertTrue(storedPhotos().isEmpty())
    }

    @Test
    fun aPhotoForAMissingPlaceIsNotKept() = runBlocking {
        diary.setPlacePhoto(placeId = 42, source = folder.jpeg(64, 48))
        assertTrue(storedPhotos().isEmpty())
    }

    @Test
    fun importingABackupRemovesThePhotos() = runBlocking {
        val placeId = diary.addVisit(paris, june)
        diary.setPlacePhoto(placeId, folder.jpeg(64, 48))

        diary.importBackup(diary.exportBackup())

        assertNull(diary.places.first().single().place.photo)
        assertTrue(storedPhotos().isEmpty())
    }

    private fun storedPhotos(): Set<String> = photoDirectory.list().orEmpty().toSet()
}
