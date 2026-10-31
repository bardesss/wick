package io.github.bardesss.wick.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.bardesss.wick.AppContainer
import io.github.bardesss.wick.ui.list.ListScreen
import io.github.bardesss.wick.ui.list.ListViewModel
import io.github.bardesss.wick.ui.login.LoginScreen
import io.github.bardesss.wick.ui.login.LoginViewModel
import io.github.bardesss.wick.ui.theme.WickColors

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
        else -> {
            // a new sign in gets a fresh list
            val vm: ListViewModel = viewModel(key = "list-" + auth.hashCode(), factory = ListViewModel.factory(container))
            // Task 8 opens the picker here
            ListScreen(vm, onOpenPicker = {})
        }
    }
}

/** Task 8 replaces this with the settings page. */
@Composable
private fun SettingsPlaceholder() {
    Box(Modifier.fillMaxSize().background(WickColors.Night), contentAlignment = Alignment.Center) {
        Wordmark()
    }
}
