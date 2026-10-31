package io.github.bardesss.wick.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupAndScrollProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        startWithFakeData()

        val list = findList()
        if (list != null) {
            repeat(3) {
                list.scroll(Direction.DOWN, 0.4f, 300)
                device.waitForIdle()
            }
            repeat(3) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
            list.fling(Direction.UP)
            device.waitForIdle()
        }

        // drawer
        device.findObject(By.res("header"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        // a right swipe on a row opens the text sheet
        device.findObject(By.res("row"))?.let { row ->
            row.setGestureMargin(device.displayWidth / 10)
            row.swipe(Direction.RIGHT, 0.9f)
            device.wait(Until.hasObject(By.res("sheet")), 3_000)
            device.pressBack()
            device.waitForIdle()
        }
    }
}
