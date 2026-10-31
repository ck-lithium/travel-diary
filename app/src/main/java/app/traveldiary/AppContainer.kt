package app.traveldiary

import android.content.Context
import app.traveldiary.data.cities.CityDatabase
import app.traveldiary.data.cities.CityRepository
import app.traveldiary.data.countries.CountryRepository
import app.traveldiary.data.countries.FlagRepository
import app.traveldiary.data.diary.DiaryDatabase
import app.traveldiary.data.diary.DiaryRepository
import app.traveldiary.data.diary.PlacePhotos
import app.traveldiary.data.regions.RegionRepository
import app.traveldiary.data.settings.SettingsRepository
import app.traveldiary.map.WorldMapRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Creates and holds the app-wide objects; screens get what they need from here. */
class AppContainer(context: Context) {
    val context: Context = context.applicationContext

    /** For work that must outlive a screen, like importing a city database. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val countries = CountryRepository(this.context)
    val flags = FlagRepository(this.context)
    val settings = SettingsRepository(this.context)
    val photos = PlacePhotos(File(this.context.filesDir, "photos"), this.context.contentResolver)
    val diary = DiaryRepository(DiaryDatabase.create(this.context), photos)
    val cities = CityRepository(this.context, CityDatabase.create(this.context), scope)
    val worldMap = WorldMapRepository(this.context, scope)
    val regions = RegionRepository(this.context, worldMap)

    fun start() {
        worldMap.load()
        cities.ensureBuiltInInstalled()
    }
}
