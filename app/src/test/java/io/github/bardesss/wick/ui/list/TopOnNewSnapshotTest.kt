package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TopOnNewSnapshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var ids by mutableStateOf<List<String>?>(null)
    private val listState = LazyListState()

    private fun show() {
        rule.setContent {
            TopOnNewSnapshot(ids, listState)
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(List(100) { "row$it" }) { Text(it, Modifier.height(80.dp)) }
            }
        }
    }

    private fun scrollTo(index: Int) {
        rule.runOnUiThread { runBlocking { listState.scrollToItem(index) } }
        rule.waitForIdle()
    }

    @Test
    fun the_first_snapshot_keeps_the_position() {
        show()
        scrollTo(40)

        ids = listOf("a", "b")
        rule.waitForIdle()

        assertEquals(40, listState.firstVisibleItemIndex)
    }

    @Test
    fun a_changed_snapshot_starts_at_the_top() {
        ids = listOf("a")
        show()
        scrollTo(40)

        ids = listOf("a", "b")
        rule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
    }

    @Test
    fun a_view_that_opens_empty_then_fills_starts_at_the_top() {
        ids = listOf("old")
        show()
        rule.waitForIdle()
        ids = emptyList()
        rule.waitForIdle()
        scrollTo(40)

        ids = listOf("new")
        rule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
    }
}
