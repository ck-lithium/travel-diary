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
    fun flagFills(flags: Map<String, ImageBitmap>): List<FlagFill> = flags.flatMap { (code, flag) ->
        geometry[code]?.flagParts().orEmpty().map { part -> FlagFill(part.rings.toPath(), flagBrush(flag, part.frame)) }
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
}

/** Loads the bundled world map once, in the background. */
class WorldMapRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _map = MutableStateFlow<WorldMap?>(null)
    val map: StateFlow<WorldMap?> = _map.asStateFlow()

    fun load() {
        if (_map.value != null) return
        scope.launch(Dispatchers.Default) {
            val bytes = context.assets.open("map/world.bin").use { it.readBytes() }
            _map.value = WorldMap(WorldGeometry.read(bytes))
        }
    }

    /** Country at a position, or null when the map is not loaded yet or the position is at sea. */
    fun countryAt(latitude: Double, longitude: Double): String? =
        _map.value?.geometry?.countryAt(latitude, longitude)
}
