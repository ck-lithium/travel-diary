package app.traveldiary.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.traveldiary.R
import app.traveldiary.ui.common.CountryPickerDialog
import app.traveldiary.ui.common.DateRangeField
import app.traveldiary.ui.common.Flag
import app.traveldiary.ui.common.formatCoordinates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitEditorScreen(viewModel: VisitEditorViewModel, onClose: () -> Unit, onSaved: (placeId: Long) -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickCountry by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(viewModel.mode.title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    if (viewModel.mode == EditorMode.EDIT_VISIT) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.editor_delete_visit))
                        }
                    }
                    TextButton(onClick = { viewModel.save(onSaved) }, enabled = !viewModel.loading) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (viewModel.mode == EditorMode.CUSTOM_PLACE) {
                OutlinedTextField(
                    value = viewModel.customName,
                    onValueChange = { viewModel.customName = it },
                    label = { Text(stringResource(R.string.editor_place_name)) },
                    placeholder = { Text(stringResource(R.string.editor_place_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                val country = viewModel.customCountry
                OutlinedCard(onClick = { pickCountry = true }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(country?.name ?: stringResource(R.string.editor_choose_country)) },
                        supportingContent = { Text(formatCoordinates(viewModel.latitude, viewModel.longitude)) },
                        leadingContent = {
                            if (country != null) Flag(country.code, size = 28.sp) else Icon(Icons.Outlined.Public, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.editor_choose_country))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            } else {
                viewModel.header?.let { header ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Flag(header.countryCode, size = 40.sp)
                        Column {
                            Text(header.name, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                listOfNotNull(header.region, header.countryName).joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (viewModel.alreadyVisited) {
                Text(
                    stringResource(R.string.editor_already_visited),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(stringResource(R.string.editor_when), style = MaterialTheme.typography.titleSmall)
            DateRangeField(
                label = stringResource(R.string.editor_dates),
                start = viewModel.startDate,
                end = viewModel.endDate,
                onRangeChange = { start, end ->
                    viewModel.startDate = start
                    viewModel.endDate = end
                },
                pickerTitle = stringResource(R.string.editor_when),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.editor_dates_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text(stringResource(R.string.editor_notes)) },
                placeholder = { Text(stringResource(R.string.editor_notes_hint)) },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            viewModel.validationError?.let { error ->
                Text(stringResource(error.message), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_visit_title)) },
            text = { Text(stringResource(R.string.editor_delete_visit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onClose)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (pickCountry) {
        CountryPickerDialog(
            countries = viewModel.countries,
            onDismiss = { pickCountry = false },
            onSelect = {
                viewModel.customCountry = it
                pickCountry = false
            },
        )
    }
}
