package app.traveldiary.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traveldiary.AppContainer
import app.traveldiary.R
import app.traveldiary.data.cities.CityEntity
import app.traveldiary.ui.editor.PlaceHeader
import app.traveldiary.ui.navigation.HomeEditorRoute
import java.time.LocalDate
import kotlinx.coroutines.launch

enum class HomeEditorError(@StringRes val message: Int) {
    START_MISSING(R.string.home_editor_error_start_missing),
    NOT_FOUND(R.string.editor_error_not_found),
}

class HomeEditorViewModel(private val container: AppContainer, private val route: HomeEditorRoute) : ViewModel() {

    /** False when editing a home period that already exists. */
    val isNew: Boolean = route.periodId == null

    var loading by mutableStateOf(true)
        private set
    var header by mutableStateOf<PlaceHeader?>(null)
        private set
    var validationError by mutableStateOf<HomeEditorError?>(null)
        private set

    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)

    private var city: CityEntity? = null
    private var saving = false

    init {
        viewModelScope.launch {
            load()
            loading = false
        }
    }

    private suspend fun load() {
        val periodId = route.periodId
        if (periodId != null) {
            val period = container.diary.getHomePeriod(periodId)
            val place = period?.let { container.diary.getPlace(it.placeId) }
            if (period == null || place == null) {
                validationError = HomeEditorError.NOT_FOUND
                return
            }
            startDate = period.startDate
            endDate = period.endDate
            header = PlaceHeader(place.name, place.region, place.countryCode, container.countries.name(place.countryCode))
        } else {
            val found = container.cities.getCity(checkNotNull(route.cityId))
            if (found == null) {
                validationError = HomeEditorError.NOT_FOUND
                return
            }
            city = found
            header = PlaceHeader(found.name, found.region, found.countryCode, container.countries.name(found.countryCode))
        }
    }

    fun save(onSaved: () -> Unit) {
        if (loading || saving || validationError == HomeEditorError.NOT_FOUND) return
        val start = startDate
        validationError = if (start == null) HomeEditorError.START_MISSING else null
        if (start == null) return

        saving = true
        viewModelScope.launch {
            val periodId = route.periodId
            if (periodId != null) {
                container.diary.updateHomePeriod(periodId, start, endDate)
            } else {
                container.diary.addHome(checkNotNull(city), start, endDate)
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val periodId = route.periodId ?: return
        viewModelScope.launch {
            container.diary.deleteHomePeriod(periodId)
            onDeleted()
        }
    }
}
