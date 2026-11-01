package io.github.bardesss.wick.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

private const val FAVICON_PATH = "/rest/feed/favicon/"

/**
 * The server's favicon endpoint needs a signed in user and only takes the API key as a query
 * parameter, so add it to favicon requests for the signed in server and to nothing else.
 * It runs inside the image loader's HTTP client, below its caches, which keep the plain URL.
 */
class FaviconAuth(private val auth: () -> Auth?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!url.encodedPath.contains(FAVICON_PATH)) return chain.proceed(request)
        val signedIn = auth() ?: return chain.proceed(request)
        val server = signedIn.server.toHttpUrlOrNull() ?: return chain.proceed(request)
        val key = signedIn.token.substringAfter('/', "")
        if (key.isEmpty() || url.scheme != server.scheme || url.host != server.host || url.port != server.port) {
            return chain.proceed(request)
        }
        val withKey = url.newBuilder().setQueryParameter("apiKey", key).build()
        return chain.proceed(request.newBuilder().url(withKey).build())
    }
}
