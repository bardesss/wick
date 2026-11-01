package io.github.bardesss.wick.data

import io.github.bardesss.wick.api.KnownApiPaths

/**
 * The format both stores write next to the server they keep. Absent means v0.1.0, which kept a
 * bare server address instead of the API URL; both stores move to a new format together.
 */
internal const val STORE_FORMAT = 2

/** The API URL v0.1.0 used for the bare [server] address it stored. */
internal fun legacyApiUrl(server: String): String = server.trimEnd('/') + KnownApiPaths.LEGACY
