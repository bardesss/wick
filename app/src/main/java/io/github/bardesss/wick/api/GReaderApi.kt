package io.github.bardesss.wick.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Where v0.1.0 served the API, relative to the bare server address it stored. */
const val LEGACY_API_PATH = "/rest/googlereader"

data class Auth(val server: String, val token: String) {
    override fun toString() = "Auth(server=$server, token=***)"
}

class GReaderApi(
    private val client: OkHttpClient,
    private val auth: () -> Auth?,
) : ReaderApi {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Volatile
    private var editToken: String? = null

    override suspend fun login(server: String, user: String, apiKey: String): String {
        val url = root(server).newBuilder().addPathSegments("accounts/ClientLogin").build()
        val body = FormBody.Builder().add("Email", user).add("Passwd", apiKey).build()
        val request = Request.Builder().url(url).post(body).build()
        val text = execute(request) { code ->
            when (code) {
                401, 403 -> ApiError.BadCredentials
                404 -> ApiError.NoGReaderApi
                else -> null
            }
        }
        return text.lineSequence().firstOrNull { it.startsWith("Auth=") }
            ?.removePrefix("Auth=")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ApiError.BadCredentials
    }

    override suspend fun feeds(): List<Feed> {
        val text = get("subscription/list") { addQueryParameter("output", "json") }
        return decode<SubscriptionListDto>(text).subscriptions.map {
            Feed(
                id = it.id,
                title = decodeEntities(it.title),
                folder = it.categories.firstOrNull()?.label?.takeIf { l -> l.isNotEmpty() },
                htmlUrl = it.htmlUrl?.takeIf { u -> u.isNotEmpty() },
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
            val token = editToken ?: fetchToken(a).also { editToken = it }
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

    private suspend fun execute(request: Request, map: (Int) -> ApiError? = { null }): String =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) {
                        throw (map(r.code) ?: if (r.code == 401) ApiError.BadCredentials else ApiError.Http(r.code))
                    }
                    r.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                throw ApiError.Unreachable
            }
        }
}
