package app.traveldiary.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.traveldiary.map.MillerProjection.WORLD_WIDTH
import app.traveldiary.ui.theme.LocalMapColors

/** A place on the map, in map units. */
@Immutable
data class MapPin(val id: Long, val position: Offset, val isHome: Boolean = false)

/**
 * The world map: visited countries filled, with their flag when it is in [flags], places shown as
 * pins. Drag to pan, pinch or double-tap to zoom, tap a country or a pin, long-press to pick a
 * position.
 */
@Composable
fun WorldMap(
    map: WorldMap?,
    state: MapViewportState,
    visitedCountries: Set<String>,
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    flags: Map<String, ImageBitmap> = emptyMap(),
    selectedCountry: String? = null,
    interactive: Boolean = true,
    gravityTrajectory: GravityTrajectory? = null,
    gravityCurrentPosition: Offset? = null,
    gravityProgress: Float = 1f,
    onCountryClick: (String?) -> Unit = {},
    onPinClick: (Long) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
) {
    val colors = LocalMapColors.current
    val scope = rememberCoroutineScope()
    val pinTouchRadius = with(LocalDensity.current) { 24.dp.toPx() }
    val visitedPath = remember(map, visitedCountries) { map?.pathOf(visitedCountries) }
    val flagFills = remember(map, visitedCountries, flags) {
        map?.flagFills(flags.filterKeys { it in visitedCountries }).orEmpty()
    }
    val selectedPath = remember(map, selectedCountry) { selectedCountry?.let { map?.path(it) } }
    val sortedPins = remember(pins) { pins.sortedBy { it.isHome } }
    val homeIcon = rememberVectorPainter(Icons.Filled.Home)
    val homeTint = remember(colors.pinBorder) { ColorFilter.tint(colors.pinBorder) }

    // The gesture detectors are installed once; let them see the latest values.
    val currentMap by rememberUpdatedState(map)
    val currentPins by rememberUpdatedState(pins)
    val currentOnCountryClick by rememberUpdatedState(onCountryClick)
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    val gestures = if (interactive) {
        Modifier
            .pointerInput(state) {
                detectTransformGestures { centroid, pan, zoom, _ -> state.onGesture(centroid, pan, zoom) }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onDoubleTap = { state.zoomIn(scope, it) },
                    onLongPress = {
                        state.stopAnimation()
                        val point = if (state.isReady) state.toMap(it) else null
                        // Ignore presses above or below the map.
                        if (point != null && point.y in 0f..MillerProjection.WORLD_HEIGHT) currentOnLongPress(point)
                    },
                    onTap = { position ->
                        val pin = state.pinAt(position, currentPins, pinTouchRadius)
                        if (pin != null) {
                            currentOnPinClick(pin.id)
                        } else if (state.isReady) {
                            val point = state.toMap(position)
                            currentOnCountryClick(currentMap?.geometry?.countryAt(point.x, point.y))
                        }
                    },
                )
            }
    } else {
        Modifier
    }

    Canvas(modifier.clipToBounds().onSizeChanged(state::onSizeChanged).then(gestures)) {
        drawRect(colors.ocean)
        if (map == null || !state.isReady) return@Canvas
        val scale = state.scale
        val offset = state.offset
        val worldWidthPx = WORLD_WIDTH * scale

        // The map repeats east-west: draw as many copies as needed to fill the view.
        var left = offset.x
        while (left < size.width) {
            withTransform({
                translate(left, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawPath(map.land, colors.land)
                visitedPath?.let { drawPath(it, colors.visited) }
                flagFills.forEach { drawPath(it.path, it.brush) }
                selectedPath?.let { drawPath(it, colors.selection) }
                drawPath(map.land, colors.border, style = Stroke(width = Stroke.HairlineWidth))
                selectedPath?.let { drawPath(it, colors.selectionBorder, style = Stroke(width = 2.dp.toPx() / scale)) }
                gravityTrajectory?.let { trajectory ->
                    // Faint line for remaining trajectory ahead of the current date
                    for (segment in trajectory.segments) {
                        if (segment.progress > gravityProgress) {
                            drawLine(
                                color = colors.gravityLine,
                                start = segment.start,
                                end = segment.end,
                                strokeWidth = 1.5.dp.toPx() / scale,
                            )
                        }
                    }
                    // Solid line for past trajectory up to current date
                    for (segment in trajectory.segments) {
                        if (segment.progress <= gravityProgress) {
                            drawLine(
                                color = colors.gravityLinePast,
                                start = segment.start,
                                end = segment.end,
                                strokeWidth = 2.5.dp.toPx() / scale,
                            )
                        }
                    }
                }
            }
            left += worldWidthPx
        }

        // Center-of-gravity marker at the current date
        gravityCurrentPosition?.let { center ->
            val y = offset.y + center.y * scale
            val haloRadius = 14.dp.toPx()
            val outerRadius = 8.dp.toPx()
            val innerRadius = 5.dp.toPx()
            val coreRadius = 2.dp.toPx()
            if (y >= -haloRadius && y <= size.height + haloRadius) {
                var x = offset.x + center.x * scale - worldWidthPx
                while (x < size.width + haloRadius) {
                    if (x > -haloRadius) {
                        val centerOffset = Offset(x, y)
                        drawCircle(colors.gravityPinHalo, haloRadius, centerOffset)
                        drawCircle(colors.pinBorder, outerRadius, centerOffset)
                        drawCircle(colors.gravityPin, innerRadius, centerOffset)
                        drawCircle(colors.pinBorder, coreRadius, centerOffset)
                    }
                    x += worldWidthPx
                }
            }
        }

        // Pins keep the same size at every zoom level. Homes are bigger, with a house inside,
        // and drawn last so other pins never hide them.
        val ring = 1.5.dp.toPx()
        val homeIconSize = 12.dp.toPx()
        for (pin in sortedPins) {
            val radius = (if (pin.isHome) 9.dp else 5.dp).toPx()
            val y = offset.y + pin.position.y * scale
            if (y < -radius || y > size.height + radius) continue
            var x = offset.x + pin.position.x * scale - worldWidthPx
            while (x < size.width + radius) {
                if (x > -radius) {
                    drawCircle(colors.pinBorder, radius + ring, Offset(x, y))
                    drawCircle(colors.pin, radius, Offset(x, y))
                    if (pin.isHome) {
                        translate(x - homeIconSize / 2, y - homeIconSize / 2) {
                            with(homeIcon) { draw(Size(homeIconSize, homeIconSize), colorFilter = homeTint) }
                        }
                    }
                }
                x += worldWidthPx
            }
        }
    }
}
