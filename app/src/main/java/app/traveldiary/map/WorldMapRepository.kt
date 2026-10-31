package app.traveldiary.map

import android.content.Context
import android.graphics.Matrix
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A part of a country to fill with [brush], which paints its flag. */
class FlagFill(val path: Path, val brush: Brush)

/** The world map, ready to draw: one [Path] per country in map units. */
class WorldMap(val geometry: WorldGeometry) {
    private val countryPaths: Map<String, Path> = geometry.countries.associate { it.code to it.rings.toPath() }

    /** All countries in one path: filled for the land, stroked for the borders. */
    val land: Path = Path().apply {
        fillType = PathFillType.EvenOdd
        countryPaths.values.forEach { addPath(it) }
    }

    fun path(code: String): Path? = countryPaths[code]

    fun pathOf(codes: Collection<String>): Path = Path().apply {
        fillType = PathFillType.EvenOdd
        codes.forEach { code -> countryPaths[code]?.let { addPath(it) } }
    }

    /** The countries in [flags] painted with their flag, see [flagParts]. */
    fun flagFills(flags: Map<String, ImageBitmap>): List<FlagFill> = flagFills(flags) { geometry[it] }
}

/** The borders between the regions of one country, with its bounds to skip it when out of view. */
class RegionBorders(val bounds: Rect, val path: Path)

/** The regions, ready to draw: one [Path] per region in map units. */
class RegionMap(val geometry: RegionGeometry) {
    private val regionPaths: Map<String, Path> = geometry.regions.associate { it.code to it.rings.toPath() }

    val borders: List<RegionBorders> = geometry.byCountry.values.map { regions ->
        RegionBorders(
            bounds = regions.map { it.bounds }.reduce { all, bounds ->
                Rect(minOf(all.left, bounds.left), minOf(all.top, bounds.top), maxOf(all.right, bounds.right), maxOf(all.bottom, bounds.bottom))
            },
            path = Path().apply { regions.forEach { region -> addPath(regionPaths.getValue(region.code)) } },
        )
    }

    fun path(code: String): Path? = regionPaths[code]

    fun pathOf(codes: Collection<String>): Path = Path().apply {
        fillType = PathFillType.EvenOdd
        codes.forEach { code -> regionPaths[code]?.let { addPath(it) } }
    }

    /** The regions in [flags] painted with the flag given for them. */
    fun flagFills(flags: Map<String, ImageBitmap>): List<FlagFill> = flagFills(flags) { geometry[it] }
}

/** Each shape of [flags] split into [flagParts], fitted to the proportions of its flag image. */
private fun flagFills(flags: Map<String, ImageBitmap>, shapeOf: (String) -> CountryGeometry?): List<FlagFill> =
    flags.flatMap { (code, flag) ->
        shapeOf(code)?.flagParts(aspect = flag.width.toFloat() / flag.height).orEmpty()
            .map { part -> FlagFill(part.rings.toPath(), flagBrush(flag, part.frame)) }
    }

private fun flagBrush(flag: ImageBitmap, frame: Rect): Brush {
    // The shader works in map units, so it follows the map when it is panned or zoomed. Islands
    // outside the frame take the colour of the nearest edge of the flag.
    val shader = ImageShader(flag, TileMode.Clamp, TileMode.Clamp)
    shader.setLocalMatrix(
        Matrix().apply {
            setScale(frame.width / flag.width, frame.height / flag.height)
            postTranslate(frame.left, frame.top)
        },
    )
    return ShaderBrush(shader)
}

private fun List<FloatArray>.toPath() = Path().apply {
    fillType = PathFillType.EvenOdd
    this@toPath.forEach { ring ->
        moveTo(ring[0], ring[1])
        for (i in 2 until ring.size step 2) lineTo(ring[i], ring[i + 1])
        close()
    }
}

/** Loads the bundled world map once, in the background, then the regions. */
class WorldMapRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _map = MutableStateFlow<WorldMap?>(null)
    val map: StateFlow<WorldMap?> = _map.asStateFlow()

    private val _regions = MutableStateFlow<RegionMap?>(null)

    /** The region shapes, ready a moment after [map]. */
    val regions: StateFlow<RegionMap?> = _regions.asStateFlow()

    fun load() {
        if (_map.value != null) return
        scope.launch(Dispatchers.Default) {
            _map.value = WorldMap(WorldGeometry.read(readAsset("map/world.bin")))
            _regions.value = RegionMap(RegionGeometry.read(readAsset("map/regions.bin")))
        }
    }

    private fun readAsset(name: String): ByteArray = context.assets.open(name).use { it.readBytes() }

    /** Country at a position, or null when the map is not loaded yet or the position is at sea. */
    fun countryAt(latitude: Double, longitude: Double): String? =
        _map.value?.geometry?.countryAt(latitude, longitude)
}
