package io.github.bardesss.wick.api

sealed class ApiError : Exception() {
    data object Unreachable : ApiError()

    /** The platform refused a plain http address. */
    data object Cleartext : ApiError()
    data object BadCredentials : ApiError()

    /** A 403: the server knows the caller and still refuses, so the key is not at fault. */
    data object Forbidden : ApiError()
    data object NoGReaderApi : ApiError()
    data object Malformed : ApiError()
    data class Http(val code: Int) : ApiError()
}
