package app.traveldiary.data.diary

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * A place in the diary. It keeps its own copy of the city data, so the diary does not
 * depend on which city database is installed.
 */
@Entity(tableName = "places", indices = [Index("countryCode"), Index("geonameId")])
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** GeoNames id of the city, or null for a place dropped on the map. */
    val geonameId: Long?,
    val name: String,
    /** State, region or province, when known. */
    val region: String?,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long,
    /** File name of the place's photo in [PlacePhotos], or null when it has none. */
    val photo: String? = null,
)

/** One stay at a place, with optional dates and free-text notes. */
@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("placeId")],
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val notes: String,
    val createdAt: Long,
)

/**
 * A time when a place was the user's home. Days at home that no visit covers count as spent there,
 * so the diary only needs the trips away from it.
 */
@Entity(
    tableName = "home_periods",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("placeId")],
)
data class HomePeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val startDate: LocalDate,
    /** Last day living there, or null while the user still lives there. */
    val endDate: LocalDate?,
    val createdAt: Long,
) {
    /** Days at home from [startDate] to [endDate], never after [today]. */
    fun daysUntil(today: LocalDate): Sequence<LocalDate> {
        val last = minOf(endDate ?: today, today)
        return generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }
    }
}

data class PlaceWithVisits(
    @Embedded val place: PlaceEntity,
    @Relation(parentColumn = "id", entityColumn = "placeId")
    val visits: List<VisitEntity>,
    @Relation(parentColumn = "id", entityColumn = "placeId")
    val homePeriods: List<HomePeriodEntity> = emptyList(),
) {
    /** True for a place where the user lives or has lived. */
    val isHome: Boolean get() = homePeriods.isNotEmpty()

    /** Visits from the most recent to the oldest; visits without a date come last. */
    val sortedVisits: List<VisitEntity>
        get() = visits.sortedWith(
            compareByDescending<VisitEntity> { it.startDate != null }
                .thenByDescending { it.startDate }
                .thenByDescending { it.createdAt },
        )

    val lastVisitDate: LocalDate? get() = visits.mapNotNull { it.endDate ?: it.startDate }.maxOrNull()
}

class Converters {
    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()
}
