package io.github.bardesss.wick.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.bardesss.wick.ui.settings.SettingsScreen
import io.github.bardesss.wick.ui.settings.signOut
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
        else -> {
            val vm: ListViewModel = viewModel(
                viewModelStoreOwner = session,
                key = "list-" + session.generation,
                factory = ListViewModel.factory(container),
                extras = activityExtras,
            )
            // the list stays composed under the settings page, so it keeps its place
            Box(Modifier.fillMaxSize()) {
                ListScreen(vm, onOpenSettings = { settingsOpen = true })
                if (settingsOpen) {
                    BackHandler { settingsOpen = false }
                    SettingsRoute(container, auth?.server.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun SettingsRoute(container: AppContainer, server: String) {
    val oldestFirst by remember { container.settings.oldestFirst.map<Boolean, Boolean?> { it } }
        .collectAsStateWithLifecycle(initialValue = null)
    val haptics by remember { container.settings.haptics.map<Boolean, Boolean?> { it } }
        .collectAsStateWithLifecycle(initialValue = null)
    // the app scope, so sign out finishes even after this page has left the screen
    val scope = container.appScope
    SettingsScreen(
        oldestFirst = oldestFirst,
        haptics = haptics,
        server = server,
        onOldestFirst = { scope.launch { container.settings.setOldestFirst(it) } },
        onHaptics = { scope.launch { container.settings.setHaptics(it) } },
        onSignOut = {
            scope.launch {
                signOut(container.markQueue, container.creds, container.db, clearImages = container::clearImages)
            }
        },
    )
}
