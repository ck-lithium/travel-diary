package app.traveldiary.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traveldiary.AppContainer
import app.traveldiary.data.diary.PlaceWithVisits
import java.text.Collator
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    val timeline: List<TimelineYear> = emptyList(),
)

class PlacesViewModel(container: AppContainer) : ViewModel() {
    val uiState: StateFlow<PlacesUiState> = container.diary.places
        .map { places -> placesUiState(places, container.countries::name) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())
}

fun placesUiState(places: List<PlaceWithVisits>, countryName: (String) -> String): PlacesUiState {
    val collator = Collator.getInstance(Locale.ENGLISH)
    val countries = places
        .groupBy { it.place.countryCode }
        .map { (code, countryPlaces) ->
            CountryGroup(
                code = code,
                name = countryName(code),
                places = countryPlaces
                    .map { PlaceRow(it.place.id, it.place.name, it.place.region, it.visits.size, it.lastVisitDate, it.isHome) }
                    .sortedWith(compareBy(collator) { it.name }),
            )
        }
        .sortedWith(compareBy(collator) { it.name })

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

    return PlacesUiState(loaded = true, placeCount = places.size, countries = countries, timeline = timeline)
}
