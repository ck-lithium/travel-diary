package app.traveldiary.ui.place

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traveldiary.AppContainer
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.map.WorldMap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaceUiState(
    val loading: Boolean = true,
    /** Null once loaded means the place was deleted. */
    val place: PlaceWithVisits? = null,
    val countryName: String = "",
    /** The place's photo; null when it has none or the file is missing, e.g. after a restore. */
    val photo: File? = null,
    /** A picked photo is being copied into the diary. */
    val savingPhoto: Boolean = false,
)

class PlaceViewModel(private val container: AppContainer, private val placeId: Long) : ViewModel() {

    val map: StateFlow<WorldMap?> = container.worldMap.map

    private val savingPhoto = MutableStateFlow(false)

    private val _photoErrors = Channel<Unit>(Channel.BUFFERED)

    /** Emits when a picked photo could not be added. */
    val photoErrors: Flow<Unit> = _photoErrors.receiveAsFlow()

    val uiState: StateFlow<PlaceUiState> = combine(container.diary.place(placeId), savingPhoto) { place, saving ->
        PlaceUiState(
            loading = false,
            place = place,
            countryName = place?.let { container.countries.name(it.place.countryCode) }.orEmpty(),
            photo = place?.place?.photo?.let(container.photos::file)?.takeIf(File::exists),
            savingPhoto = saving,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceUiState())

    fun deletePlace() {
        viewModelScope.launch { container.diary.deletePlace(placeId) }
    }

    fun setPhoto(source: Uri) {
        savingPhoto.value = true
        // On the app scope, so the photo is still saved if the screen is closed meanwhile.
        container.scope.launch {
            val result = runCatching { container.diary.setPlacePhoto(placeId, source) }
            savingPhoto.value = false
            if (result.isFailure) _photoErrors.send(Unit)
        }
    }

    fun removePhoto() {
        viewModelScope.launch { container.diary.removePlacePhoto(placeId) }
    }
}
