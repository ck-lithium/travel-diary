package app.traveldiary.ui.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traveldiary.AppContainer
import app.traveldiary.data.countries.Country
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.map.GravityPeriod
import app.traveldiary.map.GravityTimeline
import app.traveldiary.map.MapPin
import app.traveldiary.map.MillerProjection
import app.traveldiary.map.WorldMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MapUiState(
    val loaded: Boolean = false,
    val places: List<PlaceWithVisits> = emptyList(),
    val pins: List<MapPin> = emptyList(),
    val visitedCountries: Set<String> = emptySet(),
    /** Share of the 195 countries of the world visited, 0 to 1. */
    val worldShare: Float = 0f,
    val gravityTimeline: GravityTimeline = GravityTimeline.Empty,
    val gravityPeriod: GravityPeriod = GravityPeriod.QUARTER,
)

class MapViewModel(private val container: AppContainer) : ViewModel() {

    val map: StateFlow<WorldMap?> = container.worldMap.map

    private val _gravityPeriod = MutableStateFlow(GravityPeriod.QUARTER)
    val gravityPeriod: StateFlow<GravityPeriod> = _gravityPeriod.asStateFlow()

    val uiState: StateFlow<MapUiState> = combine(container.diary.places, _gravityPeriod) { places, period ->
        mapUiState(places, container.countries.all, period)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    /** Flags of the visited countries when the flag map is on, otherwise none. */
    val flags: StateFlow<Map<String, ImageBitmap>> = combine(
        container.diary.places.map { places -> places.mapTo(HashSet()) { it.place.countryCode } }.distinctUntilChanged(),
        container.settings.flagMap,
    ) { visited, enabled -> container.flags.load(if (enabled) visited else emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setGravityPeriod(period: GravityPeriod) {
        _gravityPeriod.value = period
    }

    fun country(code: String): Country? = container.countries[code]
}

fun mapUiState(
    places: List<PlaceWithVisits>,
    countries: List<Country>,
    period: GravityPeriod = GravityPeriod.QUARTER,
): MapUiState {
    val visited = places.map { it.place.countryCode }.toSet()
    val unMembers = countries.filter { it.isUnMember }
    return MapUiState(
        loaded = true,
        places = places,
        pins = places.map {
            MapPin(it.place.id, MillerProjection.position(it.place.latitude, it.place.longitude), isHome = it.isHome)
        },
        visitedCountries = visited,
        worldShare = if (unMembers.isEmpty()) 0f else unMembers.count { it.code in visited }.toFloat() / unMembers.size,
        gravityTimeline = GravityTimeline.compute(places, period),
        gravityPeriod = period,
    )
}
