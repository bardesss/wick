package io.github.bardesss.wick.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.bardesss.wick.AppContainer
import io.github.bardesss.wick.R
import io.github.bardesss.wick.ui.login.LoginScreen
import io.github.bardesss.wick.ui.login.LoginViewModel
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickText

@Composable
fun WickNav(container: AppContainer) {
    val auth by container.creds.authFlow.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(auth == null) {
        if (auth == null) settingsOpen = false
    }

    when {
        auth == null -> {
            val vm: LoginViewModel = viewModel(factory = LoginViewModel.factory(container.api, container.creds))
            LoginScreen(vm)
        }
        settingsOpen -> {
            BackHandler { settingsOpen = false }
            SettingsPlaceholder()
        }
        else -> ListPlaceholder(onOpenSettings = { settingsOpen = true })
    }
}

/** Task 6 replaces this with the list. */
@Composable
private fun ListPlaceholder(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(WickColors.Night)) {
        Wordmark(Modifier.align(Alignment.Center))
        Text(
            text = stringResource(R.string.settings),
            style = WickText.Meta,
            color = WickColors.Meta,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .clickable(onClick = onOpenSettings),
        )
    }
}

/** Task 8 replaces this with the settings page. */
@Composable
private fun SettingsPlaceholder() {
    Box(Modifier.fillMaxSize().background(WickColors.Night), contentAlignment = Alignment.Center) {
        Wordmark()
    }
}
