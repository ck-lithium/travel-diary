package app.traveldiary.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traveldiary.BuildConfig
import app.traveldiary.R
import app.traveldiary.data.cities.CityDataset
import app.traveldiary.data.cities.CityImportState
import app.traveldiary.data.cities.InstalledCities
import app.traveldiary.ui.common.SectionHeader
import app.traveldiary.ui.common.formatDate
import app.traveldiary.ui.common.formatNumber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

const val SOURCE_CODE_URL = "https://github.com/ck-lithium/travel-diary"
private const val NATURAL_EARTH_URL = "https://www.naturalearthdata.com"
private const val GEONAMES_URL = "https://www.geonames.org"

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.text(context)) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportDiary)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    SettingsScreenContent(
        installed = installed,
        importState = importState,
        snackbarHostState = snackbarHostState,
        onInstall = viewModel::install,
        onCancelDownload = viewModel::cancelDownload,
        onDismissError = viewModel::dismissError,
        onExport = { exportLauncher.launch("travel-diary-${LocalDate.now()}.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
    )

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.importDiary(uri)
                }) { Text(stringResource(R.string.settings_import_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun SettingsMessage.text(context: Context): String = when (this) {
    SettingsMessage.ExportDone -> context.getString(R.string.settings_export_done)
    SettingsMessage.ExportFailed -> context.getString(R.string.settings_export_failed)
    is SettingsMessage.ImportDone -> context.resources.getQuantityString(R.plurals.settings_import_done, places, places)
    is SettingsMessage.ImportFailed -> context.getString(
        if (invalidFile) R.string.settings_import_invalid else R.string.settings_import_failed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    installed: InstalledCities?,
    importState: CityImportState,
    snackbarHostState: SnackbarHostState,
    onInstall: (CityDataset) -> Unit,
    onCancelDownload: () -> Unit,
    onDismissError: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_cities_title))
            Text(
                stringResource(R.string.settings_cities_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            installed?.let { current ->
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                R.string.settings_cities_installed,
                                current.dataset?.let { stringResource(it.title) } ?: "?",
                            ),
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.settings_cities_installed_details,
                                formatNumber(current.cityCount),
                                formatImportDate(current.importedAt),
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.LocationCity, contentDescription = null) },
                )
            }
            ImportProgress(importState, onCancelDownload, onInstall, onDismissError)
            val busy = importState is CityImportState.Downloading || importState is CityImportState.Importing
            CityDataset.entries.forEach { dataset ->
                DatasetItem(
                    dataset = dataset,
                    inUse = installed?.dataset == dataset,
                    enabled = !busy,
                    onInstall = { onInstall(dataset) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_backup_title))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                supportingContent = { Text(stringResource(R.string.settings_export_description)) },
                leadingContent = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onExport),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_description)) },
                leadingContent = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onImport),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_about_title))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                supportingContent = { Text(stringResource(R.string.settings_privacy_description)) },
                leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source_code)) },
                supportingContent = { Text(SOURCE_CODE_URL.removePrefix("https://")) },
                leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                modifier = Modifier.clickable { uriHandler.openUri(SOURCE_CODE_URL) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_map_data)) },
                supportingContent = { Text(stringResource(R.string.settings_map_data_description)) },
                leadingContent = { Icon(Icons.Outlined.Public, contentDescription = null) },
                modifier = Modifier.clickable { uriHandler.openUri(NATURAL_EARTH_URL) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_city_data)) },
                supportingContent = { Text(stringResource(R.string.settings_city_data_description)) },
                leadingContent = { Icon(Icons.Outlined.LocationCity, contentDescription = null) },
                modifier = Modifier.clickable { uriHandler.openUri(GEONAMES_URL) },
            )
        }
    }
}

@Composable
private fun DatasetItem(dataset: CityDataset, inUse: Boolean, enabled: Boolean, onInstall: () -> Unit) {
    val size = if (dataset.downloadBytes > 0) formatMegabytes(dataset.downloadBytes) else null
    ListItem(
        headlineContent = { Text(stringResource(dataset.title)) },
        supportingContent = {
            Text(
                listOfNotNull(
                    stringResource(dataset.description),
                    stringResource(R.string.settings_dataset_count, formatNumber(dataset.approximateCities)),
                    size?.let { stringResource(R.string.settings_dataset_download, it) },
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            if (inUse) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        " " + stringResource(R.string.settings_dataset_in_use),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                TextButton(onClick = onInstall, enabled = enabled) {
                    Text(
                        stringResource(
                            if (dataset == CityDataset.BUILT_IN) R.string.settings_dataset_use else R.string.settings_dataset_get,
                        ),
                    )
                }
            }
        },
    )
}

@Composable
private fun ImportProgress(
    state: CityImportState,
    onCancel: () -> Unit,
    onRetry: (CityDataset) -> Unit,
    onDismissError: () -> Unit,
) {
    when (state) {
        CityImportState.Idle -> Unit
        is CityImportState.Downloading -> ProgressCard(
            title = stringResource(R.string.settings_downloading, stringResource(state.dataset.title)),
            detail = stringResource(
                R.string.settings_download_progress,
                formatMegabytes(state.bytesRead),
                formatMegabytes(state.totalBytes),
            ),
            progress = state.progress,
            onCancel = onCancel,
        )
        is CityImportState.Importing -> ProgressCard(
            title = stringResource(R.string.settings_importing, stringResource(state.dataset.title)),
            detail = stringResource(R.string.settings_import_progress, formatNumber(state.citiesRead)),
            progress = null,
            onCancel = onCancel.takeIf { state.dataset != CityDataset.BUILT_IN },
        )
        is CityImportState.Failed -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(
                        if (state.networkError) R.string.settings_download_failed_network else R.string.settings_download_failed,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_dismiss)) }
                    TextButton(onClick = { onRetry(state.dataset) }) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(title: String, detail: String, progress: Float?, onCancel: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onCancel != null) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

private fun formatMegabytes(bytes: Long): String = String.format(Locale.ENGLISH, "%.1f MB", bytes / 1_000_000.0)

private fun formatImportDate(epochMillis: Long): String =
    formatDate(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())
