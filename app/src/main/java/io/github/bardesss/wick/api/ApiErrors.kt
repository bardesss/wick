package io.github.bardesss.wick.api

sealed class ApiError : Exception() {
    object Unreachable : ApiError()
    object BadCredentials : ApiError()
    object NoGReaderApi : ApiError()
    data class Http(val code: Int) : ApiError()
}
