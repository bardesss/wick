package io.github.bardesss.wick.api

sealed class ApiError : Exception() {
    data object Unreachable : ApiError()
    data object BadCredentials : ApiError()
    data object NoGReaderApi : ApiError()
    data object Malformed : ApiError()
    data class Http(val code: Int) : ApiError()
}
