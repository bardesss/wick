package io.github.bardesss.wick.ui.login

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import io.github.bardesss.wick.ui.theme.WickTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LoginScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sign_in_is_disabled_while_a_field_is_blank() {
        rule.setContent { WickTheme { LoginContent(LoginState()) { _, _, _ -> } } }
        rule.onNodeWithText("Sign in").assertIsNotEnabled()
        rule.onNodeWithText("Google Reader API URL").performTextInput("example.com")
        rule.onNodeWithText("Username").performTextInput("me")
        rule.onNodeWithText("Sign in").assertIsNotEnabled()
        rule.onNodeWithText("API key").performTextInput("key")
        rule.onNodeWithText("Sign in").assertIsEnabled()
    }
}
