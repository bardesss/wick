package io.github.bardesss.wick.format

/** Short age like "12m", "3h" or "2d"; "now" under a minute and for times in the future. */
fun formatAge(nowSec: Long, thenSec: Long): String {
    val s = nowSec - thenSec
    return when {
        s < 60 -> "now"
        s < 3_600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3_600}h"
        else -> "${s / 86_400}d"
    }
}
