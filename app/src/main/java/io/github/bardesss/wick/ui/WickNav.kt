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
import io.github.bardesss.wick.data.LinkTarget
import io.github.bardesss.wick.ui.list.ListScreen
import io.github.bardesss.wick.ui.list.ListViewModel
import io.github.bardesss.wick.ui.login.LoginScreen
import io.github.bardesss.wick.ui.login.LoginViewModel
import io.github.bardesss.wick.ui.settings.SettingsScreen
import io.github.bardesss.wick.ui.settings.signOut
import io.github.bardesss.wick.ui.settings.wipeAccount
import io.github.bardesss.wick.ui.theme.WickColors
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun WickNav(container: AppContainer) {
    val auth by container.creds.authFlow.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // the list is gone while the account is wiped, so nothing can write to the database meanwhile
    var signingOut by rememberSaveable { mutableStateOf(false) }
    val session: SessionScope = viewModel { SessionScope(createSavedStateHandle()) }
    // view models for the signed in session are created from the activity's extras, so their
    // saved state lives with the activity while the session store decides how long they live
    val activityExtras = (LocalViewModelStoreOwner.current as? HasDefaultViewModelProviderFactory)
        ?.defaultViewModelCreationExtras ?: CreationExtras.Empty

    LaunchedEffect(auth == null) {
        if (auth == null) {
            settingsOpen = false
            signingOut = false
            session.end()
        }
    }

    when {
        auth == null -> {
            val vm: LoginViewModel = viewModel(factory = LoginViewModel.factory(container.api, container.creds, container.accountSwitch::enter))
            LoginScreen(vm)
        }
        signingOut -> Box(Modifier.fillMaxSize().background(WickColors.Night))
        else -> {
            val vm: ListViewModel = viewModel(
                viewModelStoreOwner = session,
                key = "list-" + session.generation,
                factory = ListViewModel.factory(container),
                extras = activityExtras,
            )
            // the list stays composed under the settings page, so it keeps its place
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                Box(
                    Modifier.fillMaxSize().then(
                        if (settingsOpen) Modifier.semantics { hideFromAccessibility() } else Modifier,
                    ),
                ) {
                    ListScreen(vm, onOpenSettings = { settingsOpen = true })
                }
                if (settingsOpen) {
                    BackHandler { settingsOpen = false }
                    SettingsRoute(container, auth?.server.orEmpty()) {
                        signingOut = true
                        session.end()
                        container.appScope.launch {
                            try {
                                signOut(
                                    container.markQueue,
                                    container.creds,
                                    wipe = {
                                        wipeAccount(
                                            container.markQueue,
                                            container.db,
                                            container.settings,
                                            beforeRelease = { container.creds.clear() },
                                            clearImages = container::clearImages,
                                        )
                                    },
                                    forgetAccount = { container.settings.setAccount(null) },
                                )
                            } finally {
                                signingOut = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRoute(container: AppContainer, server: String, onSignOut: () -> Unit) {
    val oldestFirst by remember { container.settings.oldestFirst.map<Boolean, Boolean?> { it } }
        .collectAsStateWithLifecycle(initialValue = null)
    val haptics by remember { container.settings.haptics.map<Boolean, Boolean?> { it } }
        .collectAsStateWithLifecycle(initialValue = null)
    val linkTarget by remember { container.settings.linkTarget.map<LinkTarget, LinkTarget?> { it } }
        .collectAsStateWithLifecycle(initialValue = null)
    // the app scope, so sign out finishes even after this page has left the screen
    val scope = container.appScope
    SettingsScreen(
        oldestFirst = oldestFirst,
        haptics = haptics,
        linkTarget = linkTarget,
        server = server,
        onOldestFirst = { scope.launch { container.settings.setOldestFirst(it) } },
        onHaptics = { scope.launch { container.settings.setHaptics(it) } },
        onLinkTarget = { scope.launch { container.settings.setLinkTarget(it) } },
        onSignOut = onSignOut,
    )
}
