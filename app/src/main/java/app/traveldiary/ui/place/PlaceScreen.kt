package app.traveldiary.ui.place

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traveldiary.R
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.data.diary.VisitEntity
import app.traveldiary.map.MapFocus
import app.traveldiary.map.MapPin
import app.traveldiary.map.MillerProjection
import app.traveldiary.map.WorldMap
import app.traveldiary.map.rememberMapViewportState
import app.traveldiary.ui.common.Flag
import app.traveldiary.ui.common.formatCoordinates
import app.traveldiary.ui.common.formatDateRange
import app.traveldiary.ui.common.stayLength
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps light text readable on the bright parts of a photo. */
private val PhotoTextShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 8f)

@Composable
fun PlaceScreen(
    viewModel: PlaceViewModel,
    onBack: () -> Unit,
    onAddVisit: () -> Unit,
    onEditVisit: (visitId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val map by viewModel.map.collectAsStateWithLifecycle()
    val photo = rememberPhoto(state.photo)
    val snackbarHostState = remember { SnackbarHostState() }
    val photoFailedMessage by rememberUpdatedState(stringResource(R.string.place_photo_failed))
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::setPhoto)
    }

    LaunchedEffect(viewModel) {
        viewModel.photoErrors.collect { snackbarHostState.showSnackbar(photoFailedMessage) }
    }

    // Deleted, or opened for a place that no longer exists.
    LaunchedEffect(state) {
        if (!state.loading && state.place == null) onBack()
    }

    val place = state.place ?: return
    PlaceScreenContent(
        place = place,
        countryName = state.countryName,
        map = map,
        onBack = onBack,
        onAddVisit = onAddVisit,
        onEditVisit = onEditVisit,
        onDelete = viewModel::deletePlace,
        photo = photo,
        savingPhoto = state.savingPhoto,
        onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemovePhoto = viewModel::removePhoto,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreenContent(
    place: PlaceWithVisits,
    countryName: String,
    map: WorldMap?,
    onBack: () -> Unit,
    onAddVisit: () -> Unit,
    onEditVisit: (visitId: Long) -> Unit,
    onDelete: () -> Unit,
    photo: ImageBitmap? = null,
    savingPhoto: Boolean = false,
    onPickPhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmRemovePhoto by rememberSaveable { mutableStateOf(false) }
    val details = place.place
    val onPhoto = photo != null
    // Text drawn straight on the photo is light whatever the theme.
    val textColor = if (onPhoto) Color.White else Color.Unspecified
    val secondaryTextColor = if (onPhoto) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val textShadow = if (onPhoto) PhotoTextShadow else null

    LightSystemBarIcons(enabled = onPhoto)

    Box(Modifier.fillMaxSize()) {
        if (photo != null) PhotoBackground(photo)
        Scaffold(
            containerColor = if (onPhoto) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(details.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        PhotoAction(
                            hasPhoto = onPhoto,
                            saving = savingPhoto,
                            onPick = onPickPhoto,
                            onRemove = { confirmRemovePhoto = true },
                        )
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.place_delete))
                        }
                    },
                    colors = if (onPhoto) {
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            navigationIconContentColor = Color.White,
                            titleContentColor = Color.White,
                            actionIconContentColor = Color.White,
                        )
                    } else {
                        TopAppBarDefaults.topAppBarColors()
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAddVisit,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.place_add_visit)) },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            LazyColumn(
                // Starts below the top bar, which is see-through over a photo.
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "map") {
                    val position = MillerProjection.position(details.latitude, details.longitude)
                    Card(Modifier.fillMaxWidth().height(200.dp)) {
                        WorldMap(
                            map = map,
                            state = rememberMapViewportState(MapFocus.Point(position, zoom = 10f)),
                            visitedCountries = setOf(details.countryCode),
                            pins = listOf(MapPin(details.id, position)),
                            interactive = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Flag(details.countryCode, size = 32.sp)
                        Column {
                            Text(
                                listOfNotNull(details.region, countryName).joinToString(", "),
                                style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                                color = textColor,
                            )
                            Text(
                                formatCoordinates(details.latitude, details.longitude),
                                style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                                color = secondaryTextColor,
                            )
                        }
                    }
                }
                item(key = "visits-title") {
                    Text(
                        pluralStringResource(R.plurals.place_visits, place.visits.size, place.visits.size),
                        style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                        color = textColor,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (place.visits.isEmpty()) {
                    item(key = "no-visits") {
                        Text(
                            stringResource(R.string.place_no_visits),
                            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                            color = secondaryTextColor,
                        )
                    }
                }
                items(place.sortedVisits, key = { it.id }) { visit ->
                    VisitCard(visit, onClick = { onEditVisit(visit.id) })
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.place_delete_title, details.name)) },
            text = { Text(stringResource(R.string.place_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmRemovePhoto) {
        AlertDialog(
            onDismissRequest = { confirmRemovePhoto = false },
            title = { Text(stringResource(R.string.place_remove_photo_title)) },
            text = { Text(stringResource(R.string.place_remove_photo_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemovePhoto = false
                    onRemovePhoto()
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemovePhoto = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Adds a photo, or opens a menu to change or remove it; shows a spinner while one is being saved. */
@Composable
private fun PhotoAction(hasPhoto: Boolean, saving: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    when {
        saving -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
        }
        !hasPhoto -> IconButton(onClick = onPick) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = stringResource(R.string.place_add_photo))
        }
        else -> Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.place_photo))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.place_change_photo)) },
                    leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onPick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.place_remove_photo)) },
                    leadingIcon = { Icon(Icons.Outlined.HideImage, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

/** The place's photo behind the whole screen, darkened so the text and icons on it stay readable. */
@Composable
private fun PhotoBackground(photo: ImageBitmap) {
    Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    0.3f to Color.Black.copy(alpha = 0.35f),
                    1f to Color.Black.copy(alpha = 0.6f),
                ),
            ),
    )
}

/** Decodes the photo off the main thread, showing the previous one until the new one is ready. */
@Composable
private fun rememberPhoto(file: File?): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, file) {
        value = file?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    }
    return photo
}

/** Light status and navigation bar icons while [enabled], to stand out on the dark edges of the photo. */
@Composable
private fun LightSystemBarIcons(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun VisitCard(visit: VisitEntity, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDateRange(visit.startDate, visit.endDate) ?: stringResource(R.string.visit_no_date),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                stayLength(visit.startDate, visit.endDate)?.let { days ->
                    Text(
                        pluralStringResource(R.plurals.visit_days, days, days),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                visit.notes.ifBlank { stringResource(R.string.visit_no_notes) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (visit.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
