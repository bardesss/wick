package io.github.bardesss.wick.api

/** Where servers are known to serve the Google Reader API, relative to the site. */
object KnownApiPaths {
    /** Also where v0.1.0 assumed the API was, relative to the bare server address it stored. */
    const val LEGACY = "/rest/googlereader"

    val all = listOf(LEGACY, "/api/greader.php")
}
