package io.github.bardesss.wick.format

/** How long ago something was published, in the largest whole unit; the screen words it. */
sealed interface Age {
    /** Under a minute, or a time in the future. */
    data object Now : Age
    data class Minutes(val n: Long) : Age
    data class Hours(val n: Long) : Age
    data class Days(val n: Long) : Age
}

fun ageOf(nowSec: Long, thenSec: Long): Age {
    val s = nowSec - thenSec
    return when {
        s < 60 -> Age.Now
        s < 3_600 -> Age.Minutes(s / 60)
        s < 86_400 -> Age.Hours(s / 3_600)
        else -> Age.Days(s / 86_400)
    }
}
