package io.github.bardesss.wick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import io.github.bardesss.wick.fake.FakeServerMode
import io.github.bardesss.wick.ui.WickNav
import io.github.bardesss.wick.ui.theme.WickTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // seed once per launch, not again after rotation or a restore
        if (savedInstanceState == null && BuildConfig.FAKE_ENABLED && packageName.endsWith(FakeServerMode.PACKAGE_SUFFIX)) {
            val count = intent.getIntExtra(FakeServerMode.EXTRA, 0)
            if (count > 0) {
                // the list must find the seeded data when it first reads the database
                runBlocking(Dispatchers.IO) { FakeServerMode.enable((application as WickApp).container, count) }
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            WickTheme {
                WickNav((application as WickApp).container)
            }
        }
    }
}

