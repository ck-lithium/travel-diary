package app.traveldiary.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * Read-only text field that opens a date picker; the trailing button clears the date.
 * Whether the picker is open is up to the caller, so it can also open it without a tap.
 * Confirming a date closes the picker before calling [onDateChange], which may open another one.
 */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    pickerOpen: Boolean,
    onPickerOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    suggestedDate: LocalDate? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnPickerOpenChange by rememberUpdatedState(onPickerOpenChange)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnPickerOpenChange(true) }
    }
    OutlinedTextField(
        value = date?.let(::formatDate) ?: "",
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        trailingIcon = {
            if (date != null) {
                IconButton(onClick = { onDateChange(null) }) {
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
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (date ?: suggestedDate)?.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { onPickerOpenChange(false) },
            confirmButton = {
                TextButton(onClick = {
                    onPickerOpenChange(false)
                    pickerState.selectedDateMillis?.let { onDateChange(pickerMillisToDate(it)) }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { onPickerOpenChange(false) }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
