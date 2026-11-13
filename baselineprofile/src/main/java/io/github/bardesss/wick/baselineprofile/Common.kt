package io.github.bardesss.wick.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "io.github.bardesss.wick.bench"
private const val WAIT_MS = 10_000L

/** Starts the app from a cold process with 500 fake items, no network, and waits for the list. */
fun MacrobenchmarkScope.startWithFakeData() {
    pressHome()
    // without a component, Android shows the app chooser instead of opening the app
    val launch = Intent(Intent.ACTION_MAIN)
        .setClassName(packageName, "io.github.bardesss.wick.MainActivity")
        .putExtra("wick.fake", 500)
    startActivityAndWait(launch)
    device.wait(Until.hasObject(By.res("row")), WAIT_MS)
}

fun MacrobenchmarkScope.findList() = device.findObject(By.res("list"))?.apply {
    // stay clear of the screen edges, which the system takes for gestures
    setGestureMargin(device.displayWidth / 5)
}
