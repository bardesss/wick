package io.github.bardesss.wick.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun fling_500_items() = fling(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun fling_500_items_no_compilation() = fling(CompilationMode.None())

    private fun fling(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { startWithFakeData() },
    ) {
        val list = findList() ?: return@measureRepeated
        repeat(5) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }
}
