package io.github.bardesss.wick.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.bardesss.wick.R
import io.github.bardesss.wick.format.Age

/** The short form the rows show, like "12m" or "now". */
@Composable
fun Age.short(): String = when (this) {
    Age.Now -> stringResource(R.string.age_now)
    is Age.Minutes -> stringResource(R.string.age_minutes, n)
    is Age.Hours -> stringResource(R.string.age_hours, n)
    is Age.Days -> stringResource(R.string.age_days, n)
}

/** The long form a screen reader says, like "12 minutes ago". */
@Composable
fun Age.spoken(): String = when (this) {
    Age.Now -> stringResource(R.string.age_now_spoken)
    is Age.Minutes -> pluralStringResource(R.plurals.age_minutes_spoken, n.toQuantity(), n)
    is Age.Hours -> pluralStringResource(R.plurals.age_hours_spoken, n.toQuantity(), n)
    is Age.Days -> pluralStringResource(R.plurals.age_days_spoken, n.toQuantity(), n)
}

private fun Long.toQuantity(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
