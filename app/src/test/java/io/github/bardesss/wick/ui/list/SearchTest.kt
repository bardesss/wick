package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.bardesss.wick.ui.theme.WickTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SearchTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val rows = listOf(
        RowModel("1", "The night train is back", null, "Rail News", null, 0),
        RowModel("2", "Kotlin 2.4 ships with faster builds", null, "Android Developers", null, 0),
        RowModel("3", "Espresso machines, ranked", null, "Coffee Review", null, 0),
    )

    private fun ids(query: String) = searchRows(rows, query).map { it.id }

    @Test
    fun matches_title_or_feed_ignoring_case() {
        assertEquals(listOf("1"), ids("TRAIN"))
        assertEquals(listOf("2"), ids("android"))
        assertEquals(listOf("1", "2", "3"), ids("  "))
        assertEquals(emptyList<String>(), ids("tram"))
    }

    @Test
    fun every_word_must_match_somewhere() {
        assertEquals(listOf("1"), ids("rail night"))
        assertEquals(emptyList<String>(), ids("rail kotlin"))
    }

    private fun show(history: Boolean) {
        rule.setContent {
            WickTheme {
                ListContent(
                    viewName = "Recently read",
                    history = history,
                    count = 0,
                    message = null,
                    rows = rows,
                    read = remember { mutableStateOf(rows.map { it.id }.toSet()) },
                    nowSec = 0,
                    refreshing = false,
                    listState = rememberLazyListState(),
                    onRefresh = {},
                    onOpenPicker = {},
                    onOpen = {},
                    onFrame = { false },
                )
            }
        }
    }

    @Test
    fun search_narrows_the_list_and_closing_brings_it_back() {
        show(history = true)

        rule.onNodeWithTag("search").performClick()
        rule.onNodeWithTag("search_field").performTextInput("coffee")
        rule.onAllNodesWithTag("row").assertCountEquals(1)

        rule.onNodeWithTag("search_field").performTextInput("x")
        rule.onNode(hasText("Nothing you read matches")).assertExists()

        rule.onNodeWithTag("search_close").performClick()
        rule.onNodeWithTag("search_field").assertDoesNotExist()
        rule.onAllNodesWithTag("row").assertCountEquals(3)
    }

    @Test
    fun only_the_recently_read_view_searches() {
        show(history = false)
        rule.onNodeWithTag("search").assertDoesNotExist()
    }
}
