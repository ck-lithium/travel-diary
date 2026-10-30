package app.traveldiary.data.diary

import android.net.Uri
import androidx.room.withTransaction
import app.traveldiary.data.cities.CityEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the user typed in the visit editor. */
data class VisitDraft(val startDate: LocalDate?, val endDate: LocalDate?, val notes: String)

class DiaryRepository(
    private val database: DiaryDatabase,
    private val photos: PlacePhotos,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.diaryDao()

    val places: Flow<List<PlaceWithVisits>> = dao.observePlaces()

    /** GeoNames ids of the cities already in the diary. */
    val visitedCityIds: Flow<Set<Long>> = dao.observeCityIds().map { it.toSet() }

    fun place(id: Long): Flow<PlaceWithVisits?> = dao.observePlace(id)

    suspend fun getPlace(id: Long): PlaceEntity? = dao.getPlace(id)

    suspend fun getPlaceForCity(cityId: Long): PlaceEntity? = dao.getPlaceForCity(cityId)

    suspend fun getVisit(id: Long): VisitEntity? = dao.getVisit(id)

    /** Adds a visit to [city], creating the place the first time. Returns the place id. */
    suspend fun addVisit(city: CityEntity, visit: VisitDraft): Long = database.withTransaction {
        val placeId = placeIdFor(city)
        dao.insertVisit(visit.toEntity(placeId))
        placeId
    }

    suspend fun getHomePeriod(id: Long): HomePeriodEntity? = dao.getHomePeriod(id)

    /** Makes [city] the user's home from [start] to [end] (null: still living there). Returns the place id. */
    suspend fun addHome(city: CityEntity, start: LocalDate, end: LocalDate?): Long = database.withTransaction {
        val placeId = placeIdFor(city)
        dao.insertHomePeriod(HomePeriodEntity(placeId = placeId, startDate = start, endDate = end, createdAt = clock()))
        placeId
    }

    suspend fun updateHomePeriod(periodId: Long, start: LocalDate, end: LocalDate?) {
        val existing = dao.getHomePeriod(periodId) ?: return
        dao.updateHomePeriod(existing.copy(startDate = start, endDate = end))
    }

    /** Removes a home period, and the place too when it was only a home, without visits. */
    suspend fun deleteHomePeriod(periodId: Long) {
        val orphan = database.withTransaction {
            val period = dao.getHomePeriod(periodId) ?: return@withTransaction null
            dao.deleteHomePeriod(periodId)
            val placeId = period.placeId
            placeId.takeIf { dao.countVisits(it) == 0 && dao.countHomePeriods(it) == 0 }
        }
        orphan?.let { deletePlace(it) }
    }

    private suspend fun placeIdFor(city: CityEntity): Long = dao.getPlaceForCity(city.id)?.id ?: dao.insertPlace(
        PlaceEntity(
            geonameId = city.id,
            name = city.name,
            region = city.region,
            countryCode = city.countryCode,
            latitude = city.latitude,
            longitude = city.longitude,
            createdAt = clock(),
        ),
    )

    /** Adds a place dropped on the map, which is not in the city database. Returns the place id. */
    suspend fun addCustomPlace(
        name: String,
        countryCode: String,
        latitude: Double,
        longitude: Double,
        visit: VisitDraft,
    ): Long = database.withTransaction {
        val placeId = dao.insertPlace(
            PlaceEntity(
                geonameId = null,
                name = name.trim(),
                region = null,
                countryCode = countryCode,
                latitude = latitude,
                longitude = longitude,
                createdAt = clock(),
            ),
        )
        dao.insertVisit(visit.toEntity(placeId))
        placeId
    }

    suspend fun addVisit(placeId: Long, visit: VisitDraft) {
        dao.insertVisit(visit.toEntity(placeId))
    }

    suspend fun updateVisit(visitId: Long, visit: VisitDraft) {
        val existing = dao.getVisit(visitId) ?: return
        dao.updateVisit(existing.copy(startDate = visit.startDate, endDate = visit.endDate, notes = visit.notes.trim()))
    }

    suspend fun deleteVisit(visitId: Long) = dao.deleteVisit(visitId)

    suspend fun deletePlace(placeId: Long) {
        val photo = database.withTransaction {
            val photo = dao.getPlace(placeId)?.photo
            dao.deleteVisitsOfPlace(placeId)
            dao.deleteHomePeriodsOfPlace(placeId)
            dao.deletePlace(placeId)
            photo
        }
        photos.delete(photo)
    }

    /** Gives the place a copy of the image at [source] as its photo, replacing the previous one. */
    suspend fun setPlacePhoto(placeId: Long, source: Uri) = replacePlacePhoto(placeId, photos.save(source))

    suspend fun removePlacePhoto(placeId: Long) = replacePlacePhoto(placeId, null)

    private suspend fun replacePlacePhoto(placeId: Long, photo: String?) {
        val previous = database.withTransaction {
            dao.getPlace(placeId)?.also { dao.updatePlacePhoto(placeId, photo) }
        }
        // Without a place (deleted while the photo was being copied) the new photo is not kept.
        photos.delete(if (previous == null) photo else previous.photo)
    }

    suspend fun exportBackup(): String = DiaryBackup.encode(dao.getAllPlaces(), clock())

    /** Replaces the whole diary with a backup. Returns the number of places imported. */
    suspend fun importBackup(json: String): Int {
        val places = DiaryBackup.decode(json)
        database.withTransaction {
            dao.deleteAllVisits()
            dao.deleteAllHomePeriods()
            dao.deleteAllPlaces()
            places.forEach { place ->
                val placeId = dao.insertPlace(place.toEntity())
                place.visits.forEach { dao.insertVisit(it.toEntity(placeId)) }
                place.homePeriods.forEach { dao.insertHomePeriod(it.toEntity(placeId)) }
            }
        }
        // Backups have no photos, and the places the current ones belonged to are gone.
        photos.deleteAll()
        return places.size
    }

    private fun VisitDraft.toEntity(placeId: Long) = VisitEntity(
        placeId = placeId,
        startDate = startDate,
        endDate = endDate,
        notes = notes.trim(),
        createdAt = clock(),
    )
}
