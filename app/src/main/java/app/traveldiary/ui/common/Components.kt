package app.traveldiary.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.traveldiary.R
import app.traveldiary.data.cities.SearchNormalizer
import app.traveldiary.data.countries.Country
import app.traveldiary.data.countries.flagEmoji
import app.traveldiary.ui.theme.LocalRangePalette
import app.traveldiary.ui.theme.RangePalette
import java.time.LocalDate

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun Flag(countryCode: String, modifier: Modifier = Modifier, size: TextUnit = 24.sp) {
    Text(flagEmoji(countryCode), fontSize = size, modifier = modifier)
}

/** Searchable list of every country and territory. */
@Composable
fun CountryPickerDialog(countries: List<Country>, onDismiss: () -> Unit, onSelect: (Country) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(filter, countries) {
        val query = SearchNormalizer.normalize(filter)
        if (query.isEmpty()) countries else countries.filter { SearchNormalizer.normalize(it.name).contains(query) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.country_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text(stringResource(R.string.country_picker_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.code }) { country ->
                        ListItem(
                            headlineContent = { Text(country.name) },
                            leadingContent = { Flag(country.code) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onSelect(country) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Read-only text field with a range of dates. A tap opens a calendar to pick both dates in one go:
 * the first day tapped is the start, the second the end, and the days in between are highlighted.
 * The end is optional; the trailing button clears both dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeField(
    label: String,
    start: LocalDate?,
    end: LocalDate?,
    onRangeChange: (start: LocalDate?, end: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    pickerTitle: String = label,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) pickerOpen = true }
    }
    OutlinedTextField(
        value = formatDateRange(start, end) ?: "",
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        trailingIcon = {
            if (start != null) {
                IconButton(onClick = { onRangeChange(null, null) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_date))
                }
            } else {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            }
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
    if (pickerOpen) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start?.toPickerMillis(),
            // The picker refuses an end without a start.
            initialSelectedEndDateMillis = if (start != null) end?.toPickerMillis() else null,
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerOpen = false
                        onRangeChange(
                            pickerState.selectedStartDateMillis?.let(::pickerMillisToDate),
                            pickerState.selectedEndDateMillis?.let(::pickerMillisToDate),
                        )
                    },
                    enabled = pickerState.selectedStartDateMillis != null,
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            RangePickerContent(pickerState, pickerTitle)
        }
    }
}

/**
 * The calendar of [DateRangeField], in the tint picked in Settings. Tapping the dates on top or the
 * month above the calendar switches to a list of years, to jump far without scrolling month by month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.RangePickerContent(state: DateRangePickerState, title: String) {
    var choosingYear by rememberSaveable { mutableStateOf(false) }
    val palette = LocalRangePalette.current
    val colors = DatePickerDefaults.colors(
        selectedDayContainerColor = palette.strong,
        selectedDayContentColor = palette.onStrong,
        dayInSelectionRangeContainerColor = palette.soft,
        dayInSelectionRangeContentColor = palette.onSoft,
        todayDateBorderColor = palette.strong,
    )
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    val displayedMonth = pickerMillisToDate(state.displayedMonthMillis)

    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = colors.titleContentColor,
        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
    )
    DateRangePickerDefaults.DateRangePickerHeadline(
        selectedStartDateMillis = state.selectedStartDateMillis,
        selectedEndDateMillis = state.selectedEndDateMillis,
        displayMode = state.displayMode,
        dateFormatter = dateFormatter,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { choosingYear = !choosingYear }
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        contentColor = colors.headlineContentColor,
    )
    HorizontalDivider(color = colors.dividerColor)
    TextButton(
        onClick = { choosingYear = !choosingYear },
        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
    ) {
        Text(formatMonthYear(displayedMonth))
        Icon(
            if (choosingYear) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
            contentDescription = stringResource(R.string.date_picker_choose_year),
        )
    }

    // Both take the room the dialog has left: the calendar scrolls through the months.
    if (choosingYear) {
        YearGrid(
            selectedYear = displayedMonth.year,
            palette = palette,
            onSelect = { year ->
                // The calendar scrolls to the displayed month by itself when it comes back.
                state.displayedMonthMillis = displayedMonth.withYear(year).withDayOfMonth(1).toPickerMillis()
                choosingYear = false
            },
            modifier = Modifier.weight(1f),
        )
    } else {
        DateRangePicker(
            state = state,
            dateFormatter = dateFormatter,
            colors = colors,
            title = null,
            headline = null,
            showModeToggle = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearGrid(selectedYear: Int, palette: RangePalette, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val years = DatePickerDefaults.YearRange
    // Start with the selected year on the third row, so the years around it are visible too.
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (selectedYear - years.first - 6).coerceAtLeast(0))
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(years.count()) { index ->
            val year = years.first + index
            val selected = year == selectedYear
            Surface(
                onClick = { onSelect(year) },
                shape = CircleShape,
                color = if (selected) palette.strong else Color.Transparent,
                contentColor = if (selected) palette.onStrong else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(year.toString(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
