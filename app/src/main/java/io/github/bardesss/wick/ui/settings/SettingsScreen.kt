package io.github.bardesss.wick.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.BuildConfig
import io.github.bardesss.wick.R
import io.github.bardesss.wick.data.LinkTarget
import io.github.bardesss.wick.ui.Wordmark
import io.github.bardesss.wick.ui.openLink
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.TapTarget
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickText

private val LabelStyle = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.4.sp)
private val RowStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 16.sp)
private val SegmentStyle = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
private val SegmentPadding = PaddingValues(horizontal = 12.dp)

// every setting row has the same height, whatever control it holds
private val RowHeight = 60.dp
private const val PROJECT_URL = "https://github.com/bardesss/wick"

/** Null for a setting that has not been read yet; its control shows no choice and waits until it is. */
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
    /** Only the snapshot tests set it, so the picture does not change with every release. */
    version: String = BuildConfig.VERSION_NAME,
) {
    val context = LocalContext.current
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
                // same box as the list header, so the title lands where the list's does
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 14.dp)
                    .heightIn(min = TapTarget)
                    .wrapContentHeight(Alignment.CenterVertically),
            )

            TwoChoiceRow(
                label = stringResource(R.string.settings_sort),
                first = stringResource(R.string.settings_newest),
                second = stringResource(R.string.settings_oldest),
                // true picks the second choice, oldest first
                value = oldestFirst,
                onChange = onOldestFirst,
            )
            SwitchRow(stringResource(R.string.settings_hide_empty), hideEmpty, onHideEmpty)
            SwitchRow(stringResource(R.string.settings_images), leadImages, onLeadImages)
            SwitchRow(stringResource(R.string.settings_haptics), haptics, onHaptics)
            TwoChoiceRow(
                label = stringResource(R.string.settings_links),
                first = stringResource(R.string.settings_links_in_app),
                second = stringResource(R.string.settings_links_default),
                value = linkTarget?.let { it == LinkTarget.DefaultApp },
                onChange = { onLinkTarget(if (it) LinkTarget.DefaultApp else LinkTarget.InApp) },
            )

            Section(stringResource(R.string.settings_account)) {
                Text(server, style = WickText.Note, color = WickColors.Meta, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.settings_sign_out),
                    style = RowStyle,
                    color = WickColors.Amber,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TapTarget)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_source),
                    style = WickText.Note,
                    color = WickColors.Meta,
                    modifier = Modifier
                        .heightIn(min = TapTarget)
                        .clickable(role = Role.Button) { openLink(context, PROJECT_URL, linkTarget ?: LinkTarget.InApp) }
                        .padding(horizontal = 12.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
                Text(version, style = WickText.Note, color = WickColors.Meta)
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Text(
        label,
        style = LabelStyle,
        color = WickColors.Meta,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() },
    )
    content()
}

/** An on/off setting: the whole row toggles, and the switch only shows the state. */
@Composable
private fun SwitchRow(label: String, checked: Boolean?, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .toggleable(value = checked == true, enabled = checked != null, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = RowStyle, color = WickColors.Paper, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked == true,
            onCheckedChange = null,
            enabled = checked != null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WickColors.Night,
                checkedTrackColor = WickColors.Amber,
                checkedBorderColor = WickColors.Amber,
                uncheckedThumbColor = WickColors.Meta,
                uncheckedTrackColor = WickColors.Night,
                uncheckedBorderColor = WickColors.Handle,
                disabledUncheckedThumbColor = WickColors.Handle,
                disabledUncheckedTrackColor = WickColors.Night,
                disabledUncheckedBorderColor = WickColors.Line,
            ),
        )
    }
}

/** A setting with two choices, [first] for false and [second] for true, as a pair of segments. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoChoiceRow(label: String, first: String, second: String, value: Boolean?, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = RowStyle, color = WickColors.Paper, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        SingleChoiceSegmentedButtonRow {
            listOf(first to false, second to true).forEachIndexed { index, (text, choice) ->
                SegmentedButton(
                    selected = value == choice,
                    onClick = { onChange(choice) },
                    enabled = value != null,
                    shape = SegmentedButtonDefaults.itemShape(index, count = 2),
                    colors = SegmentColors,
                    contentPadding = SegmentPadding,
                    // the colour says which is chosen; a check mark would only make it wider
                    icon = {},
                ) {
                    Text(text, style = SegmentStyle, maxLines = 1)
                }
            }
        }
    }
}

private val SegmentColors
    @Composable get() = SegmentedButtonDefaults.colors(
        activeContainerColor = WickColors.Amber,
        activeContentColor = WickColors.Night,
        activeBorderColor = WickColors.Amber,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = WickColors.Muted,
        inactiveBorderColor = WickColors.Handle,
        disabledInactiveContainerColor = Color.Transparent,
        disabledInactiveContentColor = WickColors.Faded,
        disabledInactiveBorderColor = WickColors.Line,
    )
