package app.traveldiary.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traveldiary.AppContainer
import app.traveldiary.data.cities.CityDataset
import app.traveldiary.data.cities.CityImportState
import app.traveldiary.data.cities.InstalledCities
import app.traveldiary.data.diary.InvalidBackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsMessage {
    data object ExportDone : SettingsMessage

    data object ExportFailed : SettingsMessage

    data class ImportDone(val places: Int) : SettingsMessage

    data class ImportFailed(val invalidFile: Boolean) : SettingsMessage
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val installed: StateFlow<InstalledCities?> = container.cities.installed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val importState: StateFlow<CityImportState> = container.cities.importState

    val flagMap: StateFlow<Boolean> = container.settings.flagMap

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    fun setFlagMap(enabled: Boolean) = container.settings.setFlagMap(enabled)

    fun install(dataset: CityDataset) = container.cities.install(dataset)

    fun cancelDownload() = container.cities.cancel()

    fun dismissError() = container.cities.dismissError()

    fun exportDiary(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = container.diary.exportBackup()
                withContext(Dispatchers.IO) {
                    container.context.contentResolver.openOutputStream(uri, "wt").use { stream ->
                        checkNotNull(stream) { "Cannot write $uri" }.write(json.toByteArray())
                    }
                }
            }
            _messages.send(if (result.isSuccess) SettingsMessage.ExportDone else SettingsMessage.ExportFailed)
        }
    }

    fun importDiary(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    container.context.contentResolver.openInputStream(uri).use { stream ->
                        checkNotNull(stream) { "Cannot read $uri" }.readBytes().decodeToString()
                    }
                }
                container.diary.importBackup(json)
            }
            _messages.send(
                result.fold(
                    onSuccess = { SettingsMessage.ImportDone(it) },
                    onFailure = { SettingsMessage.ImportFailed(invalidFile = it is InvalidBackupException) },
                ),
            )
        }
    }
}
