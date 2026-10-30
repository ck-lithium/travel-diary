package app.traveldiary.map

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import app.traveldiary.R
import app.traveldiary.data.diary.PlaceWithVisits
import app.traveldiary.map.MillerProjection.WORLD_WIDTH
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Pre-set smoothing periods for the mobile average of travel positions. */
enum class GravityPeriod(val days: Int, @StringRes val labelRes: Int) {
    MONTH(30, R.string.gravity_period_30d),
    QUARTER(90, R.string.gravity_period_90d),
    HALF_YEAR(180, R.string.gravity_period_180d),
    YEAR(365, R.string.gravity_period_1y),
    ALL(Int.MAX_VALUE, R.string.gravity_period_all),
}

/** One point along the center-of-gravity trajectory. */
data class GravityPoint(
    val date: LocalDate,
    val latitude: Double,
    val longitude: Double,
    val position: Offset,
    /** Position in the timeline, 0 (first visit) to 1 (latest visit). */
    val progress: Float,
)

/** A continuous straight segment of the trajectory in map units, safe to draw without antimeridian jumps. */
data class GravitySegment(
    val start: Offset,
    val end: Offset,
    /** Progress at the end of this segment, for partial-path drawing. */
    val progress: Float,
)

/** The full computed trajectory of the user's center of gravity across time. */
data class GravityTrajectory(
    val points: List<GravityPoint> = emptyList(),
    val segments: List<GravitySegment> = emptyList(),
) {
    fun positionAt(progress: Float): Offset? {
        if (points.isEmpty()) return null
        if (points.size == 1 || progress <= 0f) return points.first().position
        if (progress >= 1f) return points.last().position

        var i = 0
        while (i < points.size - 1 && points[i + 1].progress < progress) i++
        val p1 = points[i]
        val p2 = points[(i + 1).coerceAtMost(points.lastIndex)]
        val range = p2.progress - p1.progress
        if (range <= 1e-6f) return p1.position

        val u = ((progress - p1.progress) / range).coerceIn(0f, 1f)
        var targetX = p2.position.x
        if (targetX - p1.position.x > WORLD_WIDTH / 2) targetX -= WORLD_WIDTH
        if (p1.position.x - targetX > WORLD_WIDTH / 2) targetX += WORLD_WIDTH

        val lerpedX = MillerProjection.wrapX(p1.position.x + u * (targetX - p1.position.x))
        val lerpedY = p1.position.y + u * (p2.position.y - p1.position.y)
        return Offset(lerpedX, lerpedY)
    }

    fun coordinatesAt(progress: Float): Pair<Double, Double>? {
        val pos = positionAt(progress) ?: return null
        return MillerProjection.latitude(pos.y) to MillerProjection.longitude(pos.x)
    }

    fun dateAt(progress: Float, startDate: LocalDate, totalDays: Int): LocalDate {
        if (totalDays <= 0) return startDate
        return startDate.plusDays((progress.coerceIn(0f, 1f) * totalDays).roundToLong())
    }
}

/** Complete time series state for the center of gravity view. */
data class GravityTimeline(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val totalDays: Int,
    val trajectory: GravityTrajectory,
    val activePlacesCount: Int,
) {
    val isEmpty: Boolean get() = startDate == null || trajectory.points.isEmpty()

    companion object {
        val Empty = GravityTimeline(null, null, 0, GravityTrajectory(), 0)

        /**
         * Calculates the mobile average center of gravity across time from dated visits and, for
         * the days no visit covers, the user's home. Home days stop at [today].
         * Positions are averaged on the 3D unit sphere to preserve true spherical geometry and
         * avoid distortion near the poles or across the 180° antimeridian.
         */
        fun compute(
            places: List<PlaceWithVisits>,
            period: GravityPeriod,
            today: LocalDate = LocalDate.now(),
        ): GravityTimeline {
            // Collect all dated daily samples: each day a user was at a place contributes one sample.
            val visitSamples = places.flatMap { placeWithVisits ->
                placeWithVisits.visits.flatMap { visit ->
                    val start = visit.startDate ?: return@flatMap emptyList()
                    val end = maxOf(visit.endDate ?: start, start)
                    val days = ChronoUnit.DAYS.between(start, end).toInt()
                    (0..days).map { dayOffset -> DatedSample.at(placeWithVisits, start.plusDays(dayOffset.toLong())) }
                }
            }
            // Days at home count only when no trip took the user somewhere else.
            val travelDays = visitSamples.mapTo(HashSet()) { it.date }
            val homeSamples = places.flatMap { placeWithVisits ->
                placeWithVisits.homePeriods.flatMap { home ->
                    home.daysUntil(today).filter { it !in travelDays }.map { DatedSample.at(placeWithVisits, it) }.toList()
                }
            }
            val samples = (visitSamples + homeSamples).sortedBy { it.date }

            if (samples.isEmpty()) return Empty

            val startDate = samples.first().date
            val endDate = samples.last().date
            val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt()

            if (totalDays == 0) {
                val (lat, lon) = sphericalAverage(samples.map { doubleArrayOf(it.x, it.y, it.z) })
                val point = GravityPoint(
                    date = startDate,
                    latitude = lat,
                    longitude = lon,
                    position = MillerProjection.position(lat, lon),
                    progress = 1f,
                )
                return GravityTimeline(
                    startDate = startDate,
                    endDate = endDate,
                    totalDays = 0,
                    trajectory = GravityTrajectory(points = listOf(point), segments = emptyList()),
                    activePlacesCount = places.count { it.isHome || it.visits.any { v -> v.startDate != null } },
                )
            }

            // Generate dense sample dates along the span, plus explicit visit dates to capture turning points.
            // Home days are not added one by one: years at home would make the timeline very slow.
            val denseStepCount = maxOf(60, minOf(365, totalDays))
            val dateSet = sortedSetOf<LocalDate>()
            for (i in 0 until denseStepCount) {
                val offset = (i.toDouble() / (denseStepCount - 1) * totalDays).roundToLong()
                dateSet.add(startDate.plusDays(offset))
            }
            visitSamples.forEach { dateSet.add(it.date) }
            val dates = dateSet.toList()

            // Calculate center of gravity for each sample date.
            val points = ArrayList<GravityPoint>(dates.size)
            for (date in dates) {
                val progress = ChronoUnit.DAYS.between(startDate, date).toFloat() / totalDays
                val (lat, lon) = computeCentroidAt(date, samples, period)
                points.add(
                    GravityPoint(
                        date = date,
                        latitude = lat,
                        longitude = lon,
                        position = MillerProjection.position(lat, lon),
                        progress = progress.coerceIn(0f, 1f),
                    ),
                )
            }

            // Break trajectory into clean segments that split at the antimeridian.
            val segments = ArrayList<GravitySegment>()
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                segments.addAll(splitAtAntimeridian(p1.position, p2.position, p2.progress))
            }

            return GravityTimeline(
                startDate = startDate,
                endDate = endDate,
                totalDays = totalDays,
                trajectory = GravityTrajectory(points, segments),
                activePlacesCount = places.count { it.isHome || it.visits.any { v -> v.startDate != null } },
            )
        }

        private fun computeCentroidAt(date: LocalDate, samples: List<DatedSample>, period: GravityPeriod): Pair<Double, Double> {
            val isAll = period == GravityPeriod.ALL
            val sigma = maxOf(1.0, period.days / 2.0)
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            var sw = 0.0

            for (sample in samples) {
                val w = if (isAll) {
                    if (sample.date <= date) 1.0 else 0.0
                } else {
                    val dt = ChronoUnit.DAYS.between(sample.date, date).toDouble()
                    exp(-0.5 * (dt / sigma) * (dt / sigma))
                }
                if (w > 0.0) {
                    sx += sample.x * w
                    sy += sample.y * w
                    sz += sample.z * w
                    sw += w
                }
            }

            // In case of large gaps between visits when finite period weights are near zero,
            // interpolate between the last visited place before this date and the first visited place after.
            if (sw < 1e-6) {
                val past = samples.lastOrNull { it.date <= date }
                val future = samples.firstOrNull { it.date > date }
                if (past != null && future != null) {
                    val gapDays = ChronoUnit.DAYS.between(past.date, future.date).toDouble()
                    val frac = if (gapDays > 0) ChronoUnit.DAYS.between(past.date, date) / gapDays else 0.5
                    sx = past.x * (1.0 - frac) + future.x * frac
                    sy = past.y * (1.0 - frac) + future.y * frac
                    sz = past.z * (1.0 - frac) + future.z * frac
                    sw = 1.0
                } else if (past != null) {
                    return past.latitude to past.longitude
                } else if (future != null) {
                    return future.latitude to future.longitude
                }
            }

            val r = sqrt(sx * sx + sy * sy + sz * sz)
            if (r < 1e-9) return 0.0 to 0.0
            val lat = Math.toDegrees(asin((sz / r).coerceIn(-1.0, 1.0)))
            val lon = Math.toDegrees(atan2(sy, sx))
            return lat to lon
        }

        private fun sphericalAverage(vectors: List<DoubleArray>): Pair<Double, Double> {
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            for (v in vectors) {
                sx += v[0]
                sy += v[1]
                sz += v[2]
            }
            val r = sqrt(sx * sx + sy * sy + sz * sz)
            if (r < 1e-9) return 0.0 to 0.0
            val lat = Math.toDegrees(asin((sz / r).coerceIn(-1.0, 1.0)))
            val lon = Math.toDegrees(atan2(sy, sx))
            return lat to lon
        }

        private fun splitAtAntimeridian(p1: Offset, p2: Offset, progress: Float): List<GravitySegment> {
            val dx = p2.x - p1.x
            if (kotlin.math.abs(dx) <= WORLD_WIDTH / 2f) {
                return listOf(GravitySegment(p1, p2, progress))
            }
            // Antimeridian crossing: split into two subsegments hitting the boundary
            return if (dx < 0f) {
                // Moving east from near WORLD_WIDTH to near 0
                val trueX2 = p2.x + WORLD_WIDTH
                val t = (WORLD_WIDTH - p1.x) / (trueX2 - p1.x)
                val yEdge = p1.y + t * (p2.y - p1.y)
                listOf(
                    GravitySegment(p1, Offset(WORLD_WIDTH, yEdge), progress),
                    GravitySegment(Offset(0f, yEdge), p2, progress),
                )
            } else {
                // Moving west from near 0 to near WORLD_WIDTH
                val trueX2 = p2.x - WORLD_WIDTH
                val t = (0f - p1.x) / (trueX2 - p1.x)
                val yEdge = p1.y + t * (p2.y - p1.y)
                listOf(
                    GravitySegment(p1, Offset(0f, yEdge), progress),
                    GravitySegment(Offset(WORLD_WIDTH, yEdge), p2, progress),
                )
            }
        }
    }
}

private data class DatedSample(
    val date: LocalDate,
    val latitude: Double,
    val longitude: Double,
    val x: Double,
    val y: Double,
    val z: Double,
) {
    companion object {
        /** The user at [place] on [date], with its position on the unit sphere. */
        fun at(place: PlaceWithVisits, date: LocalDate): DatedSample {
            val latitude = place.place.latitude
            val longitude = place.place.longitude
            val latRad = Math.toRadians(latitude)
            val lonRad = Math.toRadians(longitude)
            return DatedSample(
                date = date,
                latitude = latitude,
                longitude = longitude,
                x = cos(latRad) * cos(lonRad),
                y = cos(latRad) * sin(lonRad),
                z = sin(latRad),
            )
        }
    }
}

