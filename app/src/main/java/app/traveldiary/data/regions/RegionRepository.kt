package app.traveldiary.data.regions

import android.content.Context
import app.traveldiary.data.cities.GeoNamesParser
import app.traveldiary.map.WorldMapRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Region names, and the [RegionIndex] that tells which region each place is in. */
class RegionRepository(private val context: Context, worldMap: WorldMapRepository) {

    // The same names the city import gives to the places it adds.
    private val names: Map<String, String> by lazy {
        context.assets.open("data/admin1.tsv").bufferedReader().useLines(GeoNamesParser::parseRegionNames)
    }

    /** A new index once the region shapes are loaded. */
    val index: Flow<RegionIndex> = worldMap.regions.map { RegionIndex(names, it?.geometry) }

    fun name(code: String): String = names[code] ?: code
}
