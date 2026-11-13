package io.github.bardesss.wick.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.bardesss.wick.ui.theme.TapTarget
import io.github.bardesss.wick.ui.theme.WickColors

/** A plain icon with a 48dp tap target, in the colour of the mark all check. */
@Composable
internal fun HeaderIcon(icon: Int, label: String, onClick: () -> Unit, tag: String? = null) {
    Box(
        Modifier
            .heightIn(min = TapTarget)
            .widthIn(min = TapTarget)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = WickColors.Amber, modifier = Modifier.size(22.dp))
    }
}
