package app.traveldiary.ui.editor

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.traveldiary.R
import app.traveldiary.ui.common.CountryPickerDialog
import app.traveldiary.ui.common.DateRangeField
import app.traveldiary.ui.common.Flag
import app.traveldiary.ui.common.formatCoordinates
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitEditorScreen(viewModel: VisitEditorViewModel, onClose: () -> Unit, onSaved: (placeId: Long) -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickCountry by rememberSaveable { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::pickPhoto)
    }

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

            if (viewModel.canAddPhoto) {
                NewPlacePhoto(
                    photo = viewModel.photo?.let(viewModel::photoFile),
                    saving = viewModel.savingPhoto,
                    failed = viewModel.photoFailed,
                    onPick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = viewModel::removePhoto,
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

/** Optional photo of a place being created: the same one its page can get later, picked right away. */
@Composable
private fun NewPlacePhoto(photo: File?, saving: Boolean, failed: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    val preview by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { file ->
            withContext(Dispatchers.IO) {
                // A preview does not need the full-size copy.
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.place_photo), style = MaterialTheme.typography.titleSmall)
        val image = preview
        if (photo != null && image != null) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPick, enabled = !saving) { Text(stringResource(R.string.place_change_photo)) }
                TextButton(onClick = onRemove, enabled = !saving) { Text(stringResource(R.string.place_remove_photo)) }
            }
        } else {
            OutlinedCard(onClick = onPick, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.place_add_photo)) },
                    supportingContent = { Text(stringResource(R.string.editor_photo_hint)) },
                    leadingContent = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
                    trailingContent = {
                        if (saving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        if (failed) {
            Text(
                stringResource(R.string.place_photo_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
