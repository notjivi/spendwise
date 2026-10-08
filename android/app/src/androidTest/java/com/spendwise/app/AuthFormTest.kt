package com.spendwise.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spendwise.app.ui.auth.AuthForm
import com.spendwise.app.ui.auth.AuthUiState
import com.spendwise.app.ui.theme.SpendWiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs on a device/emulator: ./gradlew connectedDebugAndroidTest. The form is stateless, so no Hilt/Firebase needed. */
class AuthFormTest {
    @get:Rule val rule = createComposeRule()

    @Test fun typingAndSubmittingWiresCallbacks() {
        var state by mutableStateOf(AuthUiState())
        var submits = 0
        rule.setContent {
            SpendWiseTheme {
                AuthForm(false, state, { state = state.copy(email = it) }, { state = state.copy(password = it) }, { submits++ }, {}, {})
            }
        }
        rule.onNodeWithTag("email").performTextInput("a@b.co")
        rule.onNodeWithTag("submit").performClick()
        assertEquals("a@b.co", state.email)
        assertEquals(1, submits)
    }

    @Test fun showsErrorAndDisablesSubmitWhileLoading() {
        rule.setContent {
            SpendWiseTheme { AuthForm(false, AuthUiState(loading = true, error = "Incorrect email or password"), {}, {}, {}, {}, {}) }
        }
        rule.onNodeWithText("Incorrect email or password").assertExists()
        rule.onNodeWithTag("submit").assertIsNotEnabled()
    }

    @Test fun registerModeShowsCreateAccountButton() {
        rule.setContent { SpendWiseTheme { AuthForm(true, AuthUiState(), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Create account").assertIsEnabled()
    }
}
