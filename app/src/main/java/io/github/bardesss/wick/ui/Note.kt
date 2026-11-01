package io.github.bardesss.wick.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import io.github.bardesss.wick.R
import io.github.bardesss.wick.api.ApiError

/** A message for the user as a string resource; [code] fills in the HTTP status where the text has one. */
@Immutable
data class Note(@StringRes val text: Int, val code: Int? = null)

/** What to tell the user when a call to the server failed this way. */
fun ApiError.note(): Note = when (this) {
    ApiError.Unreachable -> Note(R.string.error_unreachable)
    ApiError.Cleartext -> Note(R.string.error_needs_https)
    ApiError.BadCredentials -> Note(R.string.error_bad_credentials)
    ApiError.NoGReaderApi -> Note(R.string.error_no_api)
    ApiError.Malformed -> Note(R.string.error_malformed)
    is ApiError.Http -> Note(R.string.error_http, code)
}

@Composable
fun Note.resolve(): String = if (code == null) stringResource(text) else stringResource(text, code)
