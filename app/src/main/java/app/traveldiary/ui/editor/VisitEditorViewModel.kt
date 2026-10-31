package app.traveldiary.ui.editor

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traveldiary.AppContainer
import app.traveldiary.R
import app.traveldiary.data.cities.CityEntity
import app.traveldiary.data.countries.Country
import app.traveldiary.data.diary.PlaceEntity
import app.traveldiary.data.diary.VisitDraft
import app.traveldiary.ui.navigation.VisitEditorRoute
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.launch

enum class EditorMode(@StringRes val title: Int) {
    /** A city picked in the search. */
    NEW_PLACE(R.string.editor_title_new_place),

    /** A pin dropped on the map. */
    CUSTOM_PLACE(R.string.editor_title_custom_place),
    NEW_VISIT(R.string.editor_title_new_visit),
    EDIT_VISIT(R.string.editor_title_edit_visit),
}

data class PlaceHeader(val name: String, val region: String?, val countryCode: String, val countryName: String)

enum class EditorError(@StringRes val message: Int) {
    END_BEFORE_START(R.string.editor_error_end_before_start),
    NAME_MISSING(R.string.editor_error_name_missing),
    COUNTRY_MISSING(R.string.editor_error_country_missing),
    NOT_FOUND(R.string.editor_error_not_found),
}

class VisitEditorViewModel(private val container: AppContainer, private val route: VisitEditorRoute) : ViewModel() {

    val mode: EditorMode = when {
        route.visitId != null -> EditorMode.EDIT_VISIT
        route.placeId != null -> EditorMode.NEW_VISIT
        route.cityId != null -> EditorMode.NEW_PLACE
        else -> EditorMode.CUSTOM_PLACE
    }

    var loading by mutableStateOf(true)
        private set
    var header by mutableStateOf<PlaceHeader?>(null)
        private set
    var alreadyVisited by mutableStateOf(false)
        private set
    var validationError by mutableStateOf<EditorError?>(null)
        private set

    var customName by mutableStateOf("")
    var customCountry by mutableStateOf<Country?>(null)
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)
    var notes by mutableStateOf("")

    val latitude: Double = route.latitude ?: 0.0
    val longitude: Double = route.longitude ?: 0.0
    val countries: List<Country> get() = container.countries.all

    /** File name of the photo picked for a new place, already copied into the diary; linked when saving. */
    var photo by mutableStateOf<String?>(null)
        private set
    var savingPhoto by mutableStateOf(false)
        private set
    var photoFailed by mutableStateOf(false)
        private set

    /** Only a place created here can get its photo here: an existing one keeps its own. */
    val canAddPhoto: Boolean
        get() = mode == EditorMode.CUSTOM_PLACE || (mode == EditorMode.NEW_PLACE && !alreadyVisited)

    private var city: CityEntity? = null
    private var saving = false

    init {
        viewModelScope.launch {
            load()
            loading = false
        }
    }

    private suspend fun load() {
        val diary = container.diary
        when (mode) {
            EditorMode.EDIT_VISIT -> {
                val visit = diary.getVisit(checkNotNull(route.visitId))
                if (visit == null) {
                    validationError = EditorError.NOT_FOUND
                    return
                }
                startDate = visit.startDate
                endDate = visit.endDate
                notes = visit.notes
                header = diary.getPlace(visit.placeId)?.let(::headerOf)
            }
            EditorMode.NEW_VISIT -> {
                header = diary.getPlace(checkNotNull(route.placeId))?.let(::headerOf)
                if (header == null) validationError = EditorError.NOT_FOUND
            }
            EditorMode.NEW_PLACE -> {
                val found = container.cities.getCity(checkNotNull(route.cityId))
                if (found == null) {
                    validationError = EditorError.NOT_FOUND
                    return
                }
                city = found
                header = PlaceHeader(found.name, found.region, found.countryCode, container.countries.name(found.countryCode))
                alreadyVisited = diary.getPlaceForCity(found.id) != null
            }
            EditorMode.CUSTOM_PLACE -> {
                customCountry = container.worldMap.countryAt(latitude, longitude)?.let { container.countries[it] }
            }
        }
    }

    fun photoFile(name: String): File = container.photos.file(name)

    fun pickPhoto(source: Uri) {
        viewModelScope.launch {
            savingPhoto = true
            photoFailed = false
            val saved = runCatching { container.photos.save(source) }.getOrNull()
            savingPhoto = false
            if (saved == null) {
                photoFailed = true
            } else {
                // A second pick replaces the first one.
                container.photos.delete(photo)
                photo = saved
            }
        }
    }

    fun removePhoto() {
        val removed = photo ?: return
        photo = null
        viewModelScope.launch { container.photos.delete(removed) }
    }

    override fun onCleared() {
        // Closed without saving: the copy of the picked photo belongs to no place.
        photo?.let { unused -> container.scope.launch { container.photos.delete(unused) } }
    }

    fun save(onSaved: (placeId: Long) -> Unit) {
        if (loading || saving || savingPhoto || validationError == EditorError.NOT_FOUND) return
        // A departure date alone is simply the date of the visit.
        val start = startDate ?: endDate
        val end = if (startDate == null) null else endDate
        validationError = when {
            start != null && end != null && end.isBefore(start) -> EditorError.END_BEFORE_START
            mode == EditorMode.CUSTOM_PLACE && customName.isBlank() -> EditorError.NAME_MISSING
            mode == EditorMode.CUSTOM_PLACE && customCountry == null -> EditorError.COUNTRY_MISSING
            else -> null
        }
        if (validationError != null) return

        saving = true
        val draft = VisitDraft(start, end, notes)
        viewModelScope.launch {
            val diary = container.diary
            val placeId = when (mode) {
                EditorMode.EDIT_VISIT -> {
                    val visitId = checkNotNull(route.visitId)
                    diary.updateVisit(visitId, draft)
                    diary.getVisit(visitId)?.placeId ?: 0L
                }
                EditorMode.NEW_VISIT -> {
                    val placeId = checkNotNull(route.placeId)
                    diary.addVisit(placeId, draft)
                    placeId
                }
                EditorMode.NEW_PLACE -> diary.addVisit(checkNotNull(city), draft)
                EditorMode.CUSTOM_PLACE ->
                    diary.addCustomPlace(customName, checkNotNull(customCountry).code, latitude, longitude, draft)
            }
            photo?.let { picked ->
                diary.attachPlacePhoto(placeId, picked)
                // It belongs to the place now: closing the editor must not delete it.
                photo = null
            }
            onSaved(placeId)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val visitId = route.visitId ?: return
        viewModelScope.launch {
            container.diary.deleteVisit(visitId)
            onDeleted()
        }
    }

    private fun headerOf(place: PlaceEntity) =
        PlaceHeader(place.name, place.region, place.countryCode, container.countries.name(place.countryCode))
}
