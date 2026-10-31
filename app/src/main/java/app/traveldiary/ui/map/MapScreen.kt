package app.traveldiary.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traveldiary.R
import app.traveldiary.data.countries.Country
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.map.GravityPeriod
import app.traveldiary.map.GravityTimeline
import app.traveldiary.map.MapFocus
import app.traveldiary.map.MapViewportState
import app.traveldiary.map.MillerProjection
import app.traveldiary.map.RegionGeometry
import app.traveldiary.map.RegionMap
import app.traveldiary.map.WorldMap
import app.traveldiary.map.rememberMapViewportState
import app.traveldiary.ui.common.Flag
import app.traveldiary.ui.common.formatCoordinates
import app.traveldiary.ui.common.formatDate
import app.traveldiary.ui.common.formatDateRange
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    focusPlaceId: Long?,
    onFocusHandled: () -> Unit,
    onSearch: (countryCode: String?) -> Unit,
    onOpenPlace: (Long) -> Unit,
    onAddCustomPlace: (latitude: Double, longitude: Double) -> Unit,
) {
    val map by viewModel.map.collectAsStateWithLifecycle()
    val regions by viewModel.regionMap.collectAsStateWithLifecycle()
    val showRegions by viewModel.showRegions.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val flags by viewModel.flags.collectAsStateWithLifecycle()
    MapScreenContent(
        map = map,
        state = state,
        flags = flags,
        regions = regions,
        showRegions = showRegions,
        onShowRegionsChange = viewModel::setShowRegions,
        regionName = viewModel::regionName,
        countryOf = viewModel::country,
        onSearch = onSearch,
        onOpenPlace = onOpenPlace,
        onAddCustomPlace = onAddCustomPlace,
        onPeriodChange = viewModel::setGravityPeriod,
        focusPlaceId = focusPlaceId,
        onFocusHandled = onFocusHandled,
    )
}

@Composable
fun MapScreenContent(
    map: WorldMap?,
    state: MapUiState,
    countryOf: (String) -> Country?,
    onSearch: (countryCode: String?) -> Unit,
    onOpenPlace: (Long) -> Unit,
    onAddCustomPlace: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
    flags: Map<String, ImageBitmap> = emptyMap(),
    regions: RegionMap? = null,
    showRegions: Boolean = false,
    onShowRegionsChange: (Boolean) -> Unit = {},
    regionName: (String) -> String = { it },
    onPeriodChange: (GravityPeriod) -> Unit = {},
    focusPlaceId: Long? = null,
    onFocusHandled: () -> Unit = {},
    viewport: MapViewportState = rememberMapViewportState(),
    initialSelectedCountry: String? = null,
) {
    val scope = rememberCoroutineScope()
    var selectedCountry by rememberSaveable { mutableStateOf(initialSelectedCountry) }
    var selectedRegion by rememberSaveable { mutableStateOf<String?>(null) }
    var gravityMode by rememberSaveable { mutableStateOf(false) }
    var playbackProgress by rememberSaveable { mutableFloatStateOf(1f) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }

    // After adding a place, fly to it.
    LaunchedEffect(focusPlaceId, state.places) {
        val place = state.places.firstOrNull { it.place.id == focusPlaceId }?.place ?: return@LaunchedEffect
        snapshotFlow { viewport.isReady }.first { it }
        viewport.animateTo(scope, MapFocus.Point(MillerProjection.position(place.latitude, place.longitude), zoom = 6f))
        onFocusHandled()
    }

    // Smoothly animate time from start to finish when playback is active
    LaunchedEffect(isPlaying, state.gravityTimeline) {
        if (!isPlaying || state.gravityTimeline.isEmpty) return@LaunchedEffect
        val startProgress = if (playbackProgress >= 1f) 0f else playbackProgress
        playbackProgress = startProgress
        val remainingFraction = 1f - startProgress
        val durationMs = (15_000 * remainingFraction).roundToInt().coerceAtLeast(1_000)
        animate(
            initialValue = startProgress,
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
        ) { value, _ ->
            playbackProgress = value
        }
        isPlaying = false
    }

    Box(modifier.fillMaxSize()) {
        WorldMap(
            map = map,
            state = viewport,
            visitedCountries = state.visitedCountries,
            pins = state.pins,
            flags = flags,
            selectedCountry = selectedCountry,
            regions = regions,
            showRegions = showRegions,
            visitedRegions = state.visitedRegions,
            selectedRegion = selectedRegion,
            gravityTrajectory = if (gravityMode) state.gravityTimeline.trajectory else null,
            gravityCurrentPosition = if (gravityMode) state.gravityTimeline.trajectory.positionAt(playbackProgress) else null,
            gravityProgress = if (gravityMode) playbackProgress else 1f,
            onCountryClick = { selectedCountry = it },
            onRegionClick = { selectedRegion = it },
            onPinClick = onOpenPlace,
            onLongPress = { position ->
                onAddCustomPlace(MillerProjection.latitude(position.y), MillerProjection.longitude(position.x))
            },
            modifier = Modifier.fillMaxSize(),
        )

        SearchPill(
            onClick = { onSearch(null) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RegionToggle(
                checked = showRegions,
                onCheckedChange = {
                    selectedCountry = null
                    selectedRegion = null
                    onShowRegionsChange(it)
                },
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (gravityMode) {
                    GravityTimelineCard(
                        timeline = state.gravityTimeline,
                        period = state.gravityPeriod,
                        progress = playbackProgress,
                        isPlaying = isPlaying,
                        onProgressChange = {
                            isPlaying = false
                            playbackProgress = it
                        },
                        onPlayToggle = { isPlaying = !isPlaying },
                        onPeriodSelect = onPeriodChange,
                        onClose = {
                            isPlaying = false
                            gravityMode = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SummaryCard(state, showRegions, Modifier.weight(1f))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(
                        onClick = {
                            selectedCountry = null
                            selectedRegion = null
                            if (gravityMode) {
                                isPlaying = false
                                gravityMode = false
                            } else {
                                gravityMode = true
                                if (playbackProgress == 0f) playbackProgress = 1f
                            }
                        },
                        containerColor = if (gravityMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (gravityMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            imageVector = if (gravityMode) Icons.Filled.Place else Icons.Filled.Timeline,
                            contentDescription = stringResource(if (gravityMode) R.string.map_mode_places else R.string.map_mode_gravity),
                        )
                    }

                    SmallFloatingActionButton(onClick = {
                        selectedCountry = null
                        selectedRegion = null
                        viewport.animateTo(scope, MapFocus.World)
                    }) {
                        Icon(Icons.Filled.Public, contentDescription = stringResource(R.string.map_show_world))
                    }
                }
            }
        }

        if (map == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }

    selectedRegion?.let { code ->
        val countryCode = RegionGeometry.countryOf(code)
        RegionSheet(
            name = regionName(code),
            countryCode = countryCode,
            country = countryOf(countryCode),
            places = state.places.filter { state.placeRegions[it.place.id] == code },
            onDismiss = { selectedRegion = null },
            onAddCity = {
                selectedRegion = null
                onSearch(countryCode)
            },
            onOpenPlace = { placeId ->
                selectedRegion = null
                onOpenPlace(placeId)
            },
        )
    }

    selectedCountry?.let { code ->
        CountrySheet(
            code = code,
            country = countryOf(code),
            places = state.places.filter { it.place.countryCode == code },
            onDismiss = { selectedCountry = null },
            onAddCity = {
                selectedCountry = null
                onSearch(code)
            },
            onOpenPlace = { placeId ->
                selectedCountry = null
                onOpenPlace(placeId)
            },
        )
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(R.string.map_search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Small switch at the bottom left: regions on the map when on, countries when off. */
@Composable
private fun RegionToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        // Inside the Surface, so the ripple follows its rounded shape.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(stringResource(R.string.map_regions), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
            // A Switch has a fixed size: shrink it on screen, and keep its layout box just as small.
            Box(Modifier.size(width = 40.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                Switch(checked = checked, onCheckedChange = null, modifier = Modifier.scale(0.75f))
            }
        }
    }
}

@Composable
private fun GravityTimelineCard(
    timeline: GravityTimeline,
    period: GravityPeriod,
    progress: Float,
    isPlaying: Boolean,
    onProgressChange: (Float) -> Unit,
    onPlayToggle: () -> Unit,
    onPeriodSelect: (GravityPeriod) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        if (timeline.isEmpty) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.gravity_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.gravity_no_dates_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val startDate = timeline.startDate!!
            val totalDays = timeline.totalDays
            val currentDate = timeline.trajectory.dateAt(progress, startDate, totalDays)
            val coords = timeline.trajectory.coordinatesAt(progress)

            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatDate(currentDate),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        coords?.let { (lat, lon) ->
                            Text(
                                formatCoordinates(lat, lon),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayToggle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.gravity_pause else R.string.gravity_play),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = progress,
                        onValueChange = onProgressChange,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GravityPeriod.entries.forEach { p ->
                        val selected = p == period
                        FilterChip(
                            selected = selected,
                            onClick = { onPeriodSelect(p) },
                            label = { Text(stringResource(p.labelRes), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: MapUiState, showRegions: Boolean, modifier: Modifier = Modifier) {
    if (!state.loaded) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        if (state.places.isEmpty()) {
            Text(
                stringResource(R.string.map_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                if (showRegions) {
                    val regions = state.visitedRegions.size
                    SummaryValue(
                        value = regions.toString(),
                        label = pluralStringResource(R.plurals.summary_regions, regions),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val countries = state.visitedCountries.size
                    SummaryValue(
                        value = countries.toString(),
                        label = pluralStringResource(R.plurals.summary_countries, countries),
                        modifier = Modifier.weight(1f),
                    )
                }
                SummaryValue(
                    value = state.places.size.toString(),
                    label = pluralStringResource(R.plurals.summary_places, state.places.size),
                    modifier = Modifier.weight(1f),
                )
                SummaryValue(
                    value = formatShare(state.worldShare),
                    label = stringResource(R.string.summary_world),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 0.004 -> "<1%", 0.123 -> "12%". */
fun formatShare(share: Float): String {
    val percent = share * 100
    return if (percent > 0f && percent < 1f) "<1%" else "${percent.roundToInt()}%"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionSheet(
    name: String,
    countryCode: String,
    country: Country?,
    places: List<PlaceWithVisits>,
    onDismiss: () -> Unit,
    onAddCity: () -> Unit,
    onOpenPlace: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PlacesSheetContent(
            flagCode = countryCode,
            title = name,
            subtitle = country?.name ?: countryCode,
            places = places,
            addLabel = stringResource(R.string.country_add_city, country?.name ?: countryCode),
            onAddCity = onAddCity,
            onOpenPlace = onOpenPlace,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(
    code: String,
    country: Country?,
    places: List<PlaceWithVisits>,
    onDismiss: () -> Unit,
    onAddCity: () -> Unit,
    onOpenPlace: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CountrySheetContent(code, country, places, onAddCity, onOpenPlace)
    }
}

@Composable
fun CountrySheetContent(
    code: String,
    country: Country?,
    places: List<PlaceWithVisits>,
    onAddCity: () -> Unit,
    onOpenPlace: (Long) -> Unit,
) {
    val name = country?.name ?: code
    PlacesSheetContent(
        flagCode = code,
        title = name,
        subtitle = null,
        places = places,
        addLabel = stringResource(R.string.country_add_city, name),
        onAddCity = onAddCity,
        onOpenPlace = onOpenPlace,
    )
}

/** A country or a region: its flag, name, places visited there and a button to add one. */
@Composable
private fun PlacesSheetContent(
    flagCode: String,
    title: String,
    subtitle: String?,
    places: List<PlaceWithVisits>,
    addLabel: String,
    onAddCity: () -> Unit,
    onOpenPlace: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Flag(flagCode, size = 44.sp)
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                val visited = if (places.isEmpty()) {
                    stringResource(R.string.country_not_visited)
                } else {
                    pluralStringResource(R.plurals.country_places_visited, places.size, places.size)
                }
                Text(
                    text = listOfNotNull(subtitle, visited).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (places.isNotEmpty()) {
            Column {
                places.forEach { item ->
                    val visit = item.sortedVisits.firstOrNull()
                    ListItem(
                        headlineContent = { Text(item.place.name) },
                        supportingContent = {
                            val subtitle = formatDateRange(visit?.startDate, visit?.endDate) ?: item.place.region
                            subtitle?.let { Text(it) }
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpenPlace(item.place.id) },
                    )
                }
            }
        }
        Button(onClick = onAddCity, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(addLabel)
        }
    }
}
