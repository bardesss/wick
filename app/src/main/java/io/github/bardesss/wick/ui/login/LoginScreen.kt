package io.github.bardesss.wick.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bardesss.wick.R
import io.github.bardesss.wick.ui.Wordmark
import io.github.bardesss.wick.ui.resolve
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickText

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(state, viewModel::signIn)
}

@Composable
fun LoginContent(state: LoginState, onSignIn: (String, String, String) -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    // the key stays out of saved state, which the system keeps on disk
    var key by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = { onSignIn(server, user, key) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WickColors.Night)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(72.dp))
        Wordmark()
        Spacer(Modifier.height(56.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LoginField(
                    value = server,
                    onValueChange = { server = it },
                    label = stringResource(R.string.login_server),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                Text(
                    text = stringResource(R.string.login_server_hint),
                    style = WickText.Meta,
                    color = WickColors.Meta,
                )
            }
            LoginField(
                value = user,
                onValueChange = { user = it },
                label = stringResource(R.string.login_user),
                contentType = ContentType.Username,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            )
            LoginField(
                value = key,
                onValueChange = { key = it },
                label = stringResource(R.string.login_key),
                contentType = ContentType.Password,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    submit()
                }),
            )
        }

        Text(
            text = stringResource(R.string.login_hint),
            style = WickText.Meta,
            color = WickColors.Meta,
            modifier = Modifier.padding(top = 10.dp),
        )

        Button(
            onClick = submit,
            enabled = !state.busy,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WickColors.Amber,
                contentColor = WickColors.Night,
                disabledContainerColor = WickColors.Amber,
                disabledContentColor = WickColors.Night,
            ),
            modifier = Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = WickColors.Night,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.login_button),
                    style = WickText.Meta.copy(fontFamily = InstrumentSans, fontSize = 16.sp),
                )
            }
        }

        state.error?.let {
            Text(
                text = it.resolve(),
                style = WickText.Meta.copy(fontSize = 14.sp),
                color = WickColors.Amber,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .align(Alignment.Start)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    contentType: ContentType? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = WickText.Meta) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = WickText.Meta.copy(fontSize = 16.sp, color = WickColors.Paper),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = WickColors.Paper,
            unfocusedTextColor = WickColors.Paper,
            focusedContainerColor = WickColors.Raised,
            unfocusedContainerColor = WickColors.Raised,
            focusedBorderColor = WickColors.Meta,
            unfocusedBorderColor = WickColors.Line,
            focusedLabelColor = WickColors.Meta,
            unfocusedLabelColor = WickColors.Meta,
            cursorColor = WickColors.Amber,
        ),
        modifier = Modifier.fillMaxWidth().then(
            if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier,
        ),
    )
}
