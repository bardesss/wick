package io.github.bardesss.wick.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.BuildConfig
import io.github.bardesss.wick.R
import io.github.bardesss.wick.data.LinkTarget
import io.github.bardesss.wick.ui.Wordmark
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickText

private val LabelStyle = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.4.sp)
private val ChoiceStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 16.sp)
private val NoteStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.sp)
private val IdleChoice = Color(0xFFB7BEC6)

/** Null for a setting that has not been read yet; no choice is highlighted until it is. */
@Composable
fun SettingsScreen(
    oldestFirst: Boolean?,
    hideEmpty: Boolean?,
    leadImages: Boolean?,
    haptics: Boolean?,
    linkTarget: LinkTarget?,
    server: String,
    onOldestFirst: (Boolean) -> Unit,
    onHideEmpty: (Boolean) -> Unit,
    onLeadImages: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onLinkTarget: (LinkTarget) -> Unit,
    onSignOut: () -> Unit,
    version: String = BuildConfig.VERSION_NAME,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(WickColors.Night)
            // this page sits over the list, so it keeps touches from reaching it
            .pointerInput(Unit) { detectTapGestures() }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Text(
                stringResource(R.string.settings),
                style = WickText.Header,
                color = WickColors.Paper,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )

            Section(stringResource(R.string.settings_sort)) {
                Choice(stringResource(R.string.settings_newest_first), oldestFirst == false) { onOldestFirst(false) }
                Choice(stringResource(R.string.settings_oldest_first), oldestFirst == true) { onOldestFirst(true) }
            }
            Section(stringResource(R.string.settings_empty_feeds)) {
                Choice(stringResource(R.string.settings_show), hideEmpty == false) { onHideEmpty(false) }
                Choice(stringResource(R.string.settings_hide), hideEmpty == true) { onHideEmpty(true) }
            }
            Section(stringResource(R.string.settings_images)) {
                Choice(stringResource(R.string.settings_on), leadImages == true) { onLeadImages(true) }
                Choice(stringResource(R.string.settings_off), leadImages == false) { onLeadImages(false) }
            }
            Section(stringResource(R.string.settings_haptics)) {
                Choice(stringResource(R.string.settings_on), haptics == true) { onHaptics(true) }
                Choice(stringResource(R.string.settings_off), haptics == false) { onHaptics(false) }
            }
            Section(stringResource(R.string.settings_links)) {
                Choice(stringResource(R.string.settings_links_in_app), linkTarget == LinkTarget.InApp) {
                    onLinkTarget(LinkTarget.InApp)
                }
                Choice(stringResource(R.string.settings_links_default), linkTarget == LinkTarget.DefaultApp) {
                    onLinkTarget(LinkTarget.DefaultApp)
                }
            }
            Section(stringResource(R.string.settings_account)) {
                Text(server, style = NoteStyle, color = WickColors.Meta, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.settings_sign_out),
                    style = ChoiceStyle,
                    color = WickColors.Amber,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onSignOut)
                        .padding(vertical = 12.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Wordmark(size = 28.sp)
            Text(version, style = NoteStyle, color = WickColors.Meta)
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Text(label, style = LabelStyle, color = WickColors.Meta, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    content()
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = ChoiceStyle,
        color = if (selected) WickColors.Amber else IdleChoice,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
