package app.traveldiary.data.regions

import app.traveldiary.data.diary.PlaceEntity
import app.traveldiary.map.RegionGeometry

/**
 * Finds the first-level region (state, province...) of places. A city saved from the search keeps
 * the name of its region, which gives the region directly. Places dropped on the map, and cities
 * named like their region (Madrid, Berlin), are saved without one: their position on the region
 * shapes gives it, once [shapes] are loaded.
 *
 * @param names region names by GeoNames code, from assets/data/admin1.tsv: "IT.09" -> "Lombardy".
 */
class RegionIndex(private val names: Map<String, String>, private val shapes: RegionGeometry? = null) {
    private val codesByName: Map<String, String> =
        names.entries.associate { (code, name) -> key(RegionGeometry.countryOf(code), name) to code }

    /** True once the shapes are loaded, so places without a region name can be placed too. */
    val hasShapes: Boolean get() = shapes != null

    fun name(code: String): String = names[code] ?: code

    fun regionOf(place: PlaceEntity): String? =
        place.region?.let { codesByName[key(place.countryCode, it)] }
            ?: shapes?.regionOf(place.countryCode, place.latitude, place.longitude)

    private fun key(countryCode: String, name: String) = "$countryCode\t$name"
}
