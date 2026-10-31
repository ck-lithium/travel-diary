package app.traveldiary.ui.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traveldiary.AppContainer
import app.traveldiary.data.countries.Country
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.data.regions.RegionIndex
import app.traveldiary.map.GravityPeriod
import app.traveldiary.map.GravityTimeline
import app.traveldiary.map.MapPin
import app.traveldiary.map.MillerProjection
import app.traveldiary.map.RegionMap
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
    /** Region code of each place whose region is known, by place id. */
    val placeRegions: Map<Long, String> = emptyMap(),
    val visitedRegions: Set<String> = emptySet(),
    /** Share of the 195 countries of the world visited, 0 to 1. */
    val worldShare: Float = 0f,
    val gravityTimeline: GravityTimeline = GravityTimeline.Empty,
    val gravityPeriod: GravityPeriod = GravityPeriod.QUARTER,
)

class MapViewModel(private val container: AppContainer) : ViewModel() {

    val map: StateFlow<WorldMap?> = container.worldMap.map
    val regionMap: StateFlow<RegionMap?> = container.worldMap.regions

    /** Regions on the map instead of countries. */
    val showRegions: StateFlow<Boolean> = container.settings.regionMap

    private val _gravityPeriod = MutableStateFlow(GravityPeriod.QUARTER)
    val gravityPeriod: StateFlow<GravityPeriod> = _gravityPeriod.asStateFlow()

    val uiState: StateFlow<MapUiState> = combine(container.diary.places, _gravityPeriod, container.regions.index) { places, period, regions ->
        mapUiState(places, container.countries.all, period, regions)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    /**
     * Flags of what the map shows when the flag map is on: the visited countries, or the visited
     * regions (with their country's flag when they have none of their own). Otherwise none.
     */
    val flags: StateFlow<Map<String, ImageBitmap>> = combine(
        uiState.map { it.visitedCountries }.distinctUntilChanged(),
        uiState.map { it.visitedRegions }.distinctUntilChanged(),
        container.settings.flagMap,
        container.settings.regionMap,
    ) { countries, regions, enabled, showRegions ->
        val flags = container.flags
        when {
            enabled && showRegions -> flags.loadRegions(regions)
            else -> {
                flags.releaseRegions()
                flags.load(if (enabled) countries else emptySet())
            }
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setShowRegions(show: Boolean) = container.settings.setRegionMap(show)

    fun setGravityPeriod(period: GravityPeriod) {
        _gravityPeriod.value = period
    }

    fun country(code: String): Country? = container.countries[code]

    fun regionName(code: String): String = container.regions.name(code)
}

fun mapUiState(
    places: List<PlaceWithVisits>,
    countries: List<Country>,
    period: GravityPeriod = GravityPeriod.QUARTER,
    regions: RegionIndex? = null,
): MapUiState {
    val visited = places.map { it.place.countryCode }.toSet()
    val unMembers = countries.filter { it.isUnMember }
    val placeRegions = regions?.let { index ->
        places.mapNotNull { item -> index.regionOf(item.place)?.let { item.place.id to it } }.toMap()
    }.orEmpty()
    return MapUiState(
        loaded = true,
        places = places,
        pins = places.map {
            MapPin(it.place.id, MillerProjection.position(it.place.latitude, it.place.longitude), isHome = it.isHome)
        },
        visitedCountries = visited,
        placeRegions = placeRegions,
        visitedRegions = placeRegions.values.toSet(),
        worldShare = if (unMembers.isEmpty()) 0f else unMembers.count { it.code in visited }.toFloat() / unMembers.size,
        gravityTimeline = GravityTimeline.compute(places, period),
        gravityPeriod = period,
    )
}
