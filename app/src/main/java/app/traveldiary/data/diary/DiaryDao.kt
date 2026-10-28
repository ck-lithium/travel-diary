package app.traveldiary.data.diary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Transaction
    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    fun observePlaces(): Flow<List<PlaceWithVisits>>

    @Transaction
    @Query("SELECT * FROM places WHERE id = :id")
    fun observePlace(id: Long): Flow<PlaceWithVisits?>

    @Transaction
    @Query("SELECT * FROM places ORDER BY id")
    suspend fun getAllPlaces(): List<PlaceWithVisits>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getPlace(id: Long): PlaceEntity?

    @Query("SELECT * FROM places WHERE geonameId = :cityId LIMIT 1")
    suspend fun getPlaceForCity(cityId: Long): PlaceEntity?

    @Query("SELECT geonameId FROM places WHERE geonameId IS NOT NULL")
    fun observeCityIds(): Flow<List<Long>>

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun getVisit(id: Long): VisitEntity?

    @Insert
    suspend fun insertPlace(place: PlaceEntity): Long

    @Insert
    suspend fun insertVisit(visit: VisitEntity): Long

    @Update
    suspend fun updateVisit(visit: VisitEntity)

    @Query("UPDATE places SET photo = :photo WHERE id = :id")
    suspend fun updatePlacePhoto(id: Long, photo: String?)

    @Query("DELETE FROM visits WHERE id = :id")
    suspend fun deleteVisit(id: Long)

    @Query("DELETE FROM visits WHERE placeId = :placeId")
    suspend fun deleteVisitsOfPlace(placeId: Long)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: Long)

    @Query("DELETE FROM visits")
    suspend fun deleteAllVisits()

    @Query("DELETE FROM places")
    suspend fun deleteAllPlaces()
}
