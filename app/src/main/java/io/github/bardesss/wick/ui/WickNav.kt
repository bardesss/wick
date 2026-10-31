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
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
    val session: SessionScope = viewModel { SessionScope(createSavedStateHandle()) }
    // view models for the signed in session are created from the activity's extras, so their
    // saved state lives with the activity while the session store decides how long they live
    val activityExtras = (LocalViewModelStoreOwner.current as? HasDefaultViewModelProviderFactory)
        ?.defaultViewModelCreationExtras ?: CreationExtras.Empty

    LaunchedEffect(auth == null) {
        if (auth == null) {
            settingsOpen = false
            session.end()
        }
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
            val vm: ListViewModel = viewModel(
                viewModelStoreOwner = session,
                key = "list-" + session.generation,
                factory = ListViewModel.factory(container),
                extras = activityExtras,
            )
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
