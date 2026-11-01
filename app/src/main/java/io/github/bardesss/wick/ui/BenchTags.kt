package io.github.bardesss.wick.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import io.github.bardesss.wick.BuildConfig

/** Lets the benchmark module find views by test tag. Release builds leave it out. */
fun Modifier.benchTags(): Modifier =
    if (BuildConfig.FAKE_ENABLED) semantics { testTagsAsResourceId = true } else this
