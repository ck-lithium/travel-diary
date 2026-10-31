package app.traveldiary.ui.places

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traveldiary.R
import app.traveldiary.ui.common.EmptyState
import app.traveldiary.ui.common.Flag
import app.traveldiary.ui.common.RegionFlag
import app.traveldiary.ui.common.formatDate
import app.traveldiary.ui.common.formatDateRange

@Composable
fun PlacesScreen(viewModel: PlacesViewModel, onOpenPlace: (Long) -> Unit, onAddPlace: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlacesScreenContent(state, onOpenPlace, onAddPlace)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreenContent(
    state: PlacesUiState,
    onOpenPlace: (Long) -> Unit,
    onAddPlace: () -> Unit,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.places_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.loaded && state.placeCount == 0) {
                EmptyState(
                    icon = Icons.Outlined.TravelExplore,
                    title = stringResource(R.string.places_empty_title),
                    message = stringResource(R.string.places_empty_message),
                    action = {
                        Button(onClick = onAddPlace) { Text(stringResource(R.string.places_empty_action)) }
                    },
                )
                return@Column
            }
            val tabs = listOf(R.string.places_tab_countries, R.string.places_tab_regions, R.string.places_tab_timeline)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                    ) { Text(stringResource(label)) }
                }
            }
            when (tab) {
                0 -> CountryList(state.countries, onOpenPlace)
                1 -> RegionList(state.regions, onOpenPlace)
                else -> Timeline(state.timeline, onOpenPlace)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CountryList(countries: List<CountryGroup>, onOpenPlace: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        countries.forEach { group ->
            stickyHeader(key = "country-${group.code}") {
                CountryHeader(group.code, group.name, pluralStringResource(R.plurals.places_count, group.places.size, group.places.size))
            }
            items(group.places, key = { "place-${it.id}" }) { row -> PlaceItem(row, onOpenPlace) }
        }
    }
}

/** The visited regions of each country; a region opens to show its places. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RegionList(countries: List<RegionGroup>, onOpenPlace: (Long) -> Unit) {
    var opened by rememberSaveable { mutableStateOf(listOf<String>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        countries.forEach { group ->
            stickyHeader(key = "regions-${group.countryCode}") {
                val known = group.regions.count { it.code != null }
                CountryHeader(group.countryCode, group.countryName, pluralStringResource(R.plurals.places_regions_count, known, known))
            }
            group.regions.forEach { region ->
                val key = "${group.countryCode}/${region.code}"
                val open = key in opened
                item(key = "region-$key") {
                    ListItem(
                        headlineContent = { Text(region.name ?: stringResource(R.string.places_region_unknown)) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    pluralStringResource(R.plurals.places_count, region.places.size, region.places.size),
                                    region.lastVisit?.let(::formatDate),
                                ).joinToString(" · "),
                            )
                        },
                        leadingContent = { RegionFlag(region.code) },
                        trailingContent = {
                            Icon(
                                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = stringResource(if (open) R.string.places_region_hide else R.string.places_region_show),
                            )
                        },
                        modifier = Modifier.clickable { opened = if (open) opened - key else opened + key },
                    )
                }
                if (open) {
                    items(region.places, key = { "region-$key-place-${it.id}" }) { row ->
                        PlaceItem(row, onOpenPlace, Modifier.padding(start = 24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryHeader(code: String, name: String, count: String) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Flag(code, size = 22.sp)
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaceItem(row: PlaceRow, onOpenPlace: (Long) -> Unit, modifier: Modifier = Modifier) {
    val visits = pluralStringResource(R.plurals.place_visits, row.visits, row.visits)
    ListItem(
        headlineContent = { Text(row.name) },
        supportingContent = {
            Text(listOfNotNull(row.region, visits, row.lastVisit?.let(::formatDate)).joinToString(" · "))
        },
        leadingContent = {
            Icon(
                if (row.isHome) Icons.Filled.Home else Icons.Filled.Place,
                contentDescription = if (row.isHome) stringResource(R.string.home_icon_description) else null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable { onOpenPlace(row.id) }.then(modifier),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Timeline(years: List<TimelineYear>, onOpenPlace: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        years.forEach { year ->
            stickyHeader(key = "year-${year.year}") {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        year.year?.toString() ?: stringResource(R.string.places_timeline_undated),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(year.entries, key = { "visit-${it.visitId}" }) { entry ->
                OutlinedCard(onClick = { onOpenPlace(entry.placeId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Flag(entry.countryCode, size = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(entry.placeName, style = MaterialTheme.typography.titleMedium)
                        }
                        // Undated visits are already grouped under a "No date" header.
                        formatDateRange(entry.startDate, entry.endDate)?.let { dates ->
                            Text(
                                dates,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (entry.notes.isNotBlank()) {
                            Text(
                                entry.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
