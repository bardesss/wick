package io.github.bardesss.wick.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.UnknownServiceException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Auth(val server: String, val token: String) {
    override fun toString() = "Auth(server=$server, token=***)"
}

class GReaderApi(
    private val client: OkHttpClient,
    private val auth: () -> Auth?,
) : ReaderApi {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // the edit token belongs to the sign in it was fetched for
    @Volatile
    private var editToken: Pair<Auth, String>? = null

    override suspend fun login(server: String, user: String, apiKey: String): String {
        val url = root(server).newBuilder().addPathSegments("accounts/ClientLogin").build()
        val body = FormBody.Builder().add("Email", user).add("Passwd", apiKey).build()
        val request = Request.Builder().url(url).post(body).build()
        val text = execute(request) { code -> if (code == 404) ApiError.NoGReaderApi else null }
        // a 2xx without an Auth line at all is some other web app answering this path
        val line = text.lineSequence().firstOrNull { it.startsWith("Auth=") } ?: throw ApiError.NoGReaderApi
        return line.removePrefix("Auth=").trim().takeIf { it.isNotEmpty() } ?: throw ApiError.BadCredentials
    }

    override suspend fun feeds(): List<Feed> {
        val text = get("subscription/list") { addQueryParameter("output", "json") }
        return decode<SubscriptionListDto>(text).subscriptions.map {
            Feed(
                id = it.id,
                title = decodeEntities(it.title),
                folder = it.categories.firstOrNull()?.label?.takeIf { l -> l.isNotEmpty() },
                iconUrl = it.iconUrl?.takeIf { u -> u.isNotEmpty() },
            )
        }
    }

    override suspend fun unreadCounts(): List<UnreadCount> {
        val text = get("unread-count") { addQueryParameter("output", "json") }
        return decode<UnreadCountsDto>(text).unreadcounts
            .map { UnreadCount(it.id, it.count.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) }
    }

    override suspend fun unread(
        stream: StreamId,
        oldestFirst: Boolean,
        max: Int,
        continuation: String?,
    ): Page {
        val text = get("stream/contents/" + stream.encodedPath, encoded = true) {
            addQueryParameter("output", "json")
            addQueryParameter("xt", "user/-/state/com.google/read")
            addQueryParameter("n", max.toString())
            if (oldestFirst) addQueryParameter("r", "o")
            if (continuation != null) addQueryParameter("c", continuation)
        }
        val dto = decode<StreamContentsDto>(text)
        return Page(dto.items.map { it.toItem() }, dto.continuation?.takeIf { it.isNotEmpty() })
    }

    override suspend fun setRead(itemIds: List<String>, read: Boolean) {
        if (itemIds.isEmpty()) return
        post("edit-tag") { token ->
            val b = FormBody.Builder()
            itemIds.forEach { b.add("i", it) }
            b.add(if (read) "a" else "r", "user/-/state/com.google/read")
            b.add("T", token)
            b.build()
        }
    }

    override suspend fun markAllRead(stream: StreamId, olderThanUsec: Long) {
        post("mark-all-as-read") { token ->
            FormBody.Builder()
                .add("s", stream.raw)
                .add("ts", olderThanUsec.toString())
                .add("T", token)
                .build()
        }
    }

    private inline fun <reified T> decode(text: String): T =
        try {
            json.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            throw ApiError.Malformed
        } catch (e: IllegalArgumentException) {
            throw ApiError.Malformed
        }

    private fun currentAuth(): Auth = auth() ?: throw ApiError.BadCredentials

    private fun root(server: String): HttpUrl =
        (server.trim().trimEnd('/') + "/").toHttpUrlOrNull()
            ?: throw ApiError.Unreachable

    private fun apiUrl(a: Auth, path: String, encoded: Boolean = false): HttpUrl {
        val b = root(a.server).newBuilder()
        val full = "reader/api/0/$path"
        return (if (encoded) b.addEncodedPathSegments(full) else b.addPathSegments(full)).build()
    }

    private suspend fun get(
        path: String,
        encoded: Boolean = false,
        query: HttpUrl.Builder.() -> Unit,
    ): String {
        val a = currentAuth()
        val url = apiUrl(a, path, encoded).newBuilder().apply(query).build()
        val request = Request.Builder().url(url).header("Authorization", "GoogleLogin auth=${a.token}").build()
        return execute(request)
    }

    private suspend fun fetchToken(a: Auth): String {
        val request = Request.Builder()
            .url(apiUrl(a, "token"))
            .header("Authorization", "GoogleLogin auth=${a.token}")
            .build()
        return execute(request).trim()
    }

    private suspend fun post(path: String, form: (String) -> FormBody) {
        val a = currentAuth()
        var retried = false
        while (true) {
            val token = editToken?.takeIf { it.first == a }?.second
                ?: fetchToken(a).also { editToken = a to it }
            val request = Request.Builder()
                .url(apiUrl(a, path))
                .header("Authorization", "GoogleLogin auth=${a.token}")
                .post(form(token))
                .build()
            try {
                execute(request)
                return
            } catch (e: ApiError.BadCredentials) {
                if (!retried) {
                    retried = true
                    editToken = null
                } else {
                    throw e
                }
            }
        }
    }

    /** Runs [request]; cancelling the caller cancels the call at once, not after OkHttp's own timeouts. */
    private suspend fun execute(request: Request, map: (Int) -> ApiError? = { null }): String =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e.toApiError())

                override fun onResponse(call: Call, response: Response) {
                    val text = try {
                        response.use { r ->
                            if (!r.isSuccessful) throw (map(r.code) ?: statusError(r.code))
                            r.body.string()
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(if (e is IOException) e.toApiError() else e)
                        return
                    }
                    cont.resume(text)
                }
            })
        }

    private fun statusError(code: Int): ApiError =
        if (code == 401 || code == 403) ApiError.BadCredentials else ApiError.Http(code)

    // the platform refuses plain http, which is not the same as a server that cannot be reached
    private fun IOException.toApiError(): ApiError =
        if (this is UnknownServiceException) ApiError.Cleartext else ApiError.Unreachable
}
