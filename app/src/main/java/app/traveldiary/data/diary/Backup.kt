package app.traveldiary.data.diary

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON file written by "Export diary" and read back by "Import diary". */
@Serializable
data class BackupFile(
    val app: String = DiaryBackup.APP_ID,
    val formatVersion: Int = DiaryBackup.FORMAT_VERSION,
    val exportedAt: String,
    val places: List<BackupPlace>,
)

@Serializable
data class BackupPlace(
    val geonameId: Long? = null,
    val name: String,
    val region: String? = null,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = 0,
    val visits: List<BackupVisit> = emptyList(),
    /** Missing in backups made before homes existed. */
    val homePeriods: List<BackupHomePeriod> = emptyList(),
)

@Serializable
data class BackupHomePeriod(
    /** ISO date, e.g. 2024-05-12. */
    val startDate: String,
    val endDate: String? = null,
    val createdAt: Long = 0,
)

@Serializable
data class BackupVisit(
    /** ISO date, e.g. 2024-05-12. */
    val startDate: String? = null,
    val endDate: String? = null,
    val notes: String = "",
    val createdAt: Long = 0,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DiaryBackup {
    const val APP_ID = "travel-diary"
    const val FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(places: List<PlaceWithVisits>, exportedAt: Long): String {
        val file = BackupFile(
            exportedAt = Instant.ofEpochMilli(exportedAt).toString(),
            places = places.map { it.toBackup() },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** Reads and validates a backup. Throws [InvalidBackupException] before anything is changed. */
    fun decode(text: String): List<BackupPlace> {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidBackupException("Not a Travel Diary backup", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Not a Travel Diary backup", e)
        }
        if (file.app != APP_ID) throw InvalidBackupException("Not a Travel Diary backup")
        if (file.formatVersion > FORMAT_VERSION) {
            throw InvalidBackupException("This backup was made by a newer version of the app")
        }
        file.places.forEach { place ->
            place.visits.forEach { visit ->
                parseDate(visit.startDate)
                parseDate(visit.endDate)
            }
            place.homePeriods.forEach { period ->
                parseDate(period.startDate)
                parseDate(period.endDate)
            }
        }
        return file.places
    }

    private fun PlaceWithVisits.toBackup() = BackupPlace(
        geonameId = place.geonameId,
        name = place.name,
        region = place.region,
        countryCode = place.countryCode,
        latitude = place.latitude,
        longitude = place.longitude,
        createdAt = place.createdAt,
        visits = visits.sortedBy { it.id }.map { visit ->
            BackupVisit(
                startDate = visit.startDate?.toString(),
                endDate = visit.endDate?.toString(),
                notes = visit.notes,
                createdAt = visit.createdAt,
            )
        },
        homePeriods = homePeriods.sortedBy { it.id }.map { period ->
            BackupHomePeriod(
                startDate = period.startDate.toString(),
                endDate = period.endDate?.toString(),
                createdAt = period.createdAt,
            )
        },
    )

    internal fun parseDate(value: String?): LocalDate? = try {
        value?.let(LocalDate::parse)
    } catch (e: DateTimeParseException) {
        throw InvalidBackupException("Invalid date '$value'", e)
    }
}

internal fun BackupPlace.toEntity() = PlaceEntity(
    geonameId = geonameId,
    name = name,
    region = region,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
)

internal fun BackupHomePeriod.toEntity(placeId: Long) = HomePeriodEntity(
    placeId = placeId,
    startDate = checkNotNull(DiaryBackup.parseDate(startDate)),
    endDate = DiaryBackup.parseDate(endDate),
    createdAt = createdAt,
)

internal fun BackupVisit.toEntity(placeId: Long) = VisitEntity(
    placeId = placeId,
    startDate = DiaryBackup.parseDate(startDate),
    endDate = DiaryBackup.parseDate(endDate),
    notes = notes,
    createdAt = createdAt,
)
