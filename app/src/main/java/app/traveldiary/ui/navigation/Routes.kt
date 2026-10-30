package app.traveldiary.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MapRoute

@Serializable
data object PlacesRoute

@Serializable
data object StatsRoute

@Serializable
data object SettingsRoute

/** Shown once, on the first launch, to ask where the user lives. */
@Serializable
data object WelcomeRoute

/** City search, optionally limited to one country; with [forHome] the city picked becomes the user's home. */
@Serializable
data class SearchRoute(val countryCode: String? = null, val forHome: Boolean = false)

/**
 * Set or change the user's home. Exactly one way in:
 * - [periodId]: edit that home period,
 * - [cityId]: make a city picked in the search the home.
 */
@Serializable
data class HomeEditorRoute(val periodId: Long? = null, val cityId: Long? = null)

@Serializable
data class PlaceRoute(val placeId: Long)

/**
 * Add or edit a visit. Exactly one way in:
 * - [visitId]: edit that visit,
 * - [placeId]: new visit to a place already in the diary,
 * - [cityId]: new visit to a city picked in the search,
 * - [latitude] and [longitude]: new place dropped on the map.
 */
@Serializable
data class VisitEditorRoute(
    val visitId: Long? = null,
    val placeId: Long? = null,
    val cityId: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
