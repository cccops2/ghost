package app.ghostmine.core

import java.util.Locale

fun hashrateParts(h: Double): Pair<String, String> = when {
    h >= 1e9 -> String.format(Locale.US, "%.2f", h / 1e9) to "GH/s"
    h >= 1e6 -> String.format(Locale.US, "%.2f", h / 1e6) to "MH/s"
    h >= 1e3 -> String.format(Locale.US, "%.2f", h / 1e3) to "kH/s"
    else -> String.format(Locale.US, "%.2f", h) to "H/s"
}

fun formatHashrate(h: Double): String = hashrateParts(h).let { "${it.first} ${it.second}" }
fun formatBtcPlain(v: Double): String = String.format(Locale.US, "%.8f", v)
fun formatBtc(v: Double): String = formatBtcPlain(v) + " BTC"

fun formatDuration(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun shortAddress(a: String): String =
    if (a.isBlank()) "Not set" else if (a.length > 14) a.take(6) + "…" + a.takeLast(4) else a

fun formatTemp(t: Float): String = if (t.isNaN()) "-- °C" else String.format(Locale.US, "%.1f °C", t)
