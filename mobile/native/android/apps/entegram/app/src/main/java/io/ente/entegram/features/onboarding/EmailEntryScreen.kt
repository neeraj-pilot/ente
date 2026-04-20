package io.ente.entegram.features.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@Composable
fun EmailEntryScreen(
    email: String,
    isLoading: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
    isEmailValid: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200) // wait for OnboardingScaffold's AnimatedVisibility to compose the field
        focusRequester.requestFocus()
    }

    OnboardingScaffold(
        currentStep = 0,
        modifier = modifier,
    ) {
        OnboardingHeadline(
            title = stringResource(R.string.onboarding_email_title),
            subtitle = stringResource(R.string.onboarding_email_subtitle),
        )

        Spacer(modifier = Modifier.height(Space.xl))

        OnboardingTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = stringResource(R.string.onboarding_email_placeholder),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(
                onGo = { if (isEmailValid && !isLoading) onContinue() },
            ),
            enabled = !isLoading,
            modifier = Modifier.focusRequester(focusRequester),
        )

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(Motion.quickFade()),
            exit = fadeOut(Motion.quickFade()),
        ) {
            Text(
                text = error.orEmpty(),
                style = Typo.caption,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = Space.sm),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        OnboardingContinueButton(
            enabled = isEmailValid,
            isLoading = isLoading,
            onClick = onContinue,
        )

        Spacer(modifier = Modifier.height(Space.lg))
    }
}

@Preview(showBackground = true)
@Composable
private fun EmailEntryEmptyPreview() {
    EnteGramTheme {
        EmailEntryScreen(
            email = "",
            isLoading = false,
            error = null,
            onEmailChange = {},
            onContinue = {},
            isEmailValid = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmailEntryFilledPreview() {
    EnteGramTheme {
        EmailEntryScreen(
            email = "user@example.com",
            isLoading = false,
            error = null,
            onEmailChange = {},
            onContinue = {},
            isEmailValid = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmailEntryLoadingPreview() {
    EnteGramTheme {
        EmailEntryScreen(
            email = "user@example.com",
            isLoading = true,
            error = null,
            onEmailChange = {},
            onContinue = {},
            isEmailValid = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmailEntryErrorPreview() {
    EnteGramTheme {
        EmailEntryScreen(
            email = "bad-email",
            isLoading = false,
            error = "Failed to send verification code. Please try again.",
            onEmailChange = {},
            onContinue = {},
            isEmailValid = false,
        )
    }
}
