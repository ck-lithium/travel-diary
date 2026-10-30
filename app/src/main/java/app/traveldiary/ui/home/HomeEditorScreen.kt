package app.traveldiary.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.traveldiary.R
import app.traveldiary.ui.common.DateRangeField
import app.traveldiary.ui.common.Flag

/** Where the user lives and since when; [onDone] runs after saving or removing the home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeEditorScreen(viewModel: HomeEditorViewModel, onClose: () -> Unit, onDone: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) R.string.home_editor_title_new else R.string.home_editor_title_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.home_editor_delete))
                        }
                    }
                    TextButton(onClick = { viewModel.save(onDone) }, enabled = !viewModel.loading) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

            Text(stringResource(R.string.home_editor_when), style = MaterialTheme.typography.titleSmall)
            DateRangeField(
                label = stringResource(R.string.home_editor_dates),
                start = viewModel.startDate,
                end = viewModel.endDate,
                onRangeChange = { start, end ->
                    viewModel.startDate = start
                    viewModel.endDate = end
                },
                pickerTitle = stringResource(R.string.home_editor_when),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.home_editor_dates_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.home_editor_explanation), style = MaterialTheme.typography.bodyMedium)

            viewModel.validationError?.let { error ->
                Text(stringResource(error.message), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.home_editor_delete_title)) },
            text = { Text(stringResource(R.string.home_editor_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
