package app.traveldiary.ui.common

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayOnly = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

fun formatDate(date: LocalDate): String = date.format(dayMonthYear)

/** "November 2026". */
fun formatMonthYear(date: LocalDate): String = date.format(monthYear)

/** "12 May 2024", "12 – 18 May 2024", "28 Apr – 3 May 2024" or "28 Dec 2023 – 3 Jan 2024". */
fun formatDateRange(start: LocalDate?, end: LocalDate?): String? {
    if (start == null) return null
    if (end == null || !end.isAfter(start)) return start.format(dayMonthYear)
    return when {
        start.year != end.year -> "${start.format(dayMonthYear)} – ${end.format(dayMonthYear)}"
        start.month != end.month -> "${start.format(dayMonth)} – ${end.format(dayMonthYear)}"
        else -> "${start.format(dayOnly)} – ${end.format(dayMonthYear)}"
    }
}

/** Number of calendar days of a stay, both ends included; null without a start date. */
fun stayLength(start: LocalDate?, end: LocalDate?): Int? {
    if (start == null) return null
    if (end == null || !end.isAfter(start)) return 1
    return ChronoUnit.DAYS.between(start, end).toInt() + 1
}

/** 2_148_000 -> "2.1M", 48_500 -> "48K", 1_250 -> "1.3K", 800 -> "800". */
fun formatPopulation(population: Long): String = when {
    population >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1fM", population / 1_000_000.0).replace(".0M", "M")
    population >= 10_000 -> "${population / 1_000}K"
    population >= 1_000 -> String.format(Locale.ENGLISH, "%.1fK", population / 1_000.0).replace(".0K", "K")
    else -> population.toString()
}

fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value)

fun formatCoordinates(latitude: Double, longitude: Double): String {
    val lat = String.format(Locale.ENGLISH, "%.3f° %s", kotlin.math.abs(latitude), if (latitude >= 0) "N" else "S")
    val lon = String.format(Locale.ENGLISH, "%.3f° %s", kotlin.math.abs(longitude), if (longitude >= 0) "E" else "W")
    return "$lat, $lon"
}

/** The Material date picker works with UTC midnight timestamps. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
