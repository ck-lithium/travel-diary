package app.traveldiary.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traveldiary.AppContainer
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.data.regions.RegionIndex
import java.text.Collator
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class PlaceRow(
    val id: Long,
    val name: String,
    val region: String?,
    val visits: Int,
    val lastVisit: LocalDate?,
    val isHome: Boolean = false,
)

data class CountryGroup(val code: String, val name: String, val places: List<PlaceRow>)

/**
 * A region and the places visited in it. [code] and [name] are null for the places of a country
 * whose region is not known: a pin dropped at sea, or a region the map has no shape for.
 */
data class RegionRow(val code: String?, val name: String?, val places: List<PlaceRow>, val lastVisit: LocalDate?)

data class RegionGroup(val countryCode: String, val countryName: String, val regions: List<RegionRow>)

data class TimelineEntry(
    val visitId: Long,
    val placeId: Long,
    val placeName: String,
    val countryCode: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val notes: String,
)

/** Visits of one year, newest first; [year] is null for visits without a date. */
data class TimelineYear(val year: Int?, val entries: List<TimelineEntry>)

data class PlacesUiState(
    val loaded: Boolean = false,
    val placeCount: Int = 0,
    val countries: List<CountryGroup> = emptyList(),
    val regions: List<RegionGroup> = emptyList(),
    val timeline: List<TimelineYear> = emptyList(),
)

class PlacesViewModel(container: AppContainer) : ViewModel() {
    val uiState: StateFlow<PlacesUiState> = combine(container.diary.places, container.regions.index) { places, regions ->
        placesUiState(places, container.countries::name, regions)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())
}

fun placesUiState(
    places: List<PlaceWithVisits>,
    countryName: (String) -> String,
    regions: RegionIndex? = null,
): PlacesUiState {
    val collator = Collator.getInstance(Locale.ENGLISH)
    fun rowsOf(places: List<PlaceWithVisits>) = places
        .map { PlaceRow(it.place.id, it.place.name, it.place.region, it.visits.size, it.lastVisitDate, it.isHome) }
        .sortedWith(compareBy(collator) { it.name })

    val byCountry = places.groupBy { it.place.countryCode }
    val countries = byCountry
        .map { (code, countryPlaces) -> CountryGroup(code = code, name = countryName(code), places = rowsOf(countryPlaces)) }
        .sortedWith(compareBy(collator) { it.name })

    val regionGroups = if (regions == null) {
        emptyList()
    } else {
        byCountry
            .map { (code, countryPlaces) ->
                val rows = countryPlaces
                    .groupBy { regions.regionOf(it.place) }
                    .map { (region, regionPlaces) ->
                        RegionRow(
                            code = region,
                            name = region?.let(regions::name),
                            places = rowsOf(regionPlaces),
                            lastVisit = regionPlaces.mapNotNull { it.lastVisitDate }.maxOrNull(),
                        )
                    }
                    // The places without a known region come last.
                    .sortedWith(compareBy<RegionRow> { it.code == null }.thenBy(collator) { it.name.orEmpty() })
                RegionGroup(countryCode = code, countryName = countryName(code), regions = rows)
            }
            .sortedWith(compareBy(collator) { it.countryName })
    }

    val timeline = places
        .flatMap { place ->
            place.visits.map { visit ->
                TimelineEntry(
                    visitId = visit.id,
                    placeId = place.place.id,
                    placeName = place.place.name,
                    countryCode = place.place.countryCode,
                    startDate = visit.startDate,
                    endDate = visit.endDate,
                    notes = visit.notes,
                )
            }
        }
        .sortedWith(
            compareByDescending<TimelineEntry> { it.startDate != null }
                .thenByDescending { it.startDate }
                .thenByDescending { it.visitId },
        )
        .groupBy { it.startDate?.year }
        .map { (year, entries) -> TimelineYear(year, entries) }

    return PlacesUiState(
        loaded = true,
        placeCount = places.size,
        countries = countries,
        regions = regionGroups,
        timeline = timeline,
    )
}
