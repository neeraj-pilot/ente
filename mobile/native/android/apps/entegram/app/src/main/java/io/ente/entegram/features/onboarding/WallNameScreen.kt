package io.ente.entegram.features.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo
import kotlinx.coroutines.delay

@Composable
fun WallNameScreen(
    wallName: String,
    availability: WallNameAvailability,
    isLoading: Boolean,
    error: String?,
    isValid: Boolean,
    onWallNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200) // wait for OnboardingScaffold's AnimatedVisibility to compose the field
        focusRequester.requestFocus()
    }

    OnboardingScaffold(
        currentStep = 3,
        onBack = onBack,
        modifier = modifier,
    ) {
        OnboardingHeadline(
            title = stringResource(R.string.onboarding_wallname_title),
            subtitle = stringResource(R.string.onboarding_wallname_subtitle),
        )

        Spacer(modifier = Modifier.height(Space.xl))

        OnboardingTextField(
            value = wallName,
            onValueChange = onWallNameChange,
            placeholder = stringResource(R.string.onboarding_wallname_placeholder),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (isValid && !isLoading) onContinue() },
            ),
            leadingIcon = {
                Text(
                    text = "@",
                    style = Typo.body,
                    color = Color.White.copy(alpha = 0.5f),
                )
            },
            trailingIcon = {
                when (availability) {
                    WallNameAvailability.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White.copy(alpha = 0.5f),
                            strokeWidth = 2.dp,
                        )
                    }

                    WallNameAvailability.Available -> {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.onboarding_wallname_available_icon),
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    WallNameAvailability.Taken -> {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.onboarding_wallname_taken_icon),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    else -> {}
                }
            },
            isError = availability == WallNameAvailability.Taken,
            enabled = !isLoading,
            modifier = Modifier.focusRequester(focusRequester),
        )

        Spacer(modifier = Modifier.height(Space.sm))

        // Status text
        AnimatedVisibility(
            visible = wallName.isNotEmpty(),
            enter = fadeIn(Motion.quickFade()),
            exit = fadeOut(Motion.quickFade()),
        ) {
            Text(
                text = when (availability) {
                    WallNameAvailability.TooShort -> stringResource(R.string.onboarding_wallname_too_short)
                    WallNameAvailability.Taken -> stringResource(R.string.onboarding_wallname_taken, wallName)
                    WallNameAvailability.Available -> stringResource(R.string.onboarding_wallname_available, wallName)
                    WallNameAvailability.Checking -> stringResource(R.string.onboarding_wallname_checking)
                    WallNameAvailability.Idle -> ""
                },
                style = Typo.caption,
                color = when (availability) {
                    WallNameAvailability.Available -> MaterialTheme.colorScheme.secondary
                    WallNameAvailability.Taken -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    else -> Color.White.copy(alpha = 0.5f)
                },
            )
        }

        Spacer(modifier = Modifier.height(Space.md))

        Text(
            text = stringResource(R.string.onboarding_wallname_rules),
            style = Typo.caption,
            color = Color.White.copy(alpha = 0.35f),
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                text = error,
                style = Typo.caption,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        OnboardingContinueButton(
            text = stringResource(R.string.onboarding_wallname_create_account),
            enabled = isValid,
            isLoading = isLoading,
            onClick = onContinue,
        )

        Spacer(modifier = Modifier.height(Space.lg))
    }
}

@Preview(showBackground = true)
@Composable
private fun WallNameEmptyPreview() {
    EnteGramTheme {
        WallNameScreen(
            wallName = "",
            availability = WallNameAvailability.Idle,
            isLoading = false,
            error = null,
            isValid = false,
            onWallNameChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WallNameTooShortPreview() {
    EnteGramTheme {
        WallNameScreen(
            wallName = "ab",
            availability = WallNameAvailability.TooShort,
            isLoading = false,
            error = null,
            isValid = false,
            onWallNameChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WallNameCheckingPreview() {
    EnteGramTheme {
        WallNameScreen(
            wallName = "myhandle",
            availability = WallNameAvailability.Checking,
            isLoading = false,
            error = null,
            isValid = false,
            onWallNameChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WallNameAvailablePreview() {
    EnteGramTheme {
        WallNameScreen(
            wallName = "myhandle",
            availability = WallNameAvailability.Available,
            isLoading = false,
            error = null,
            isValid = true,
            onWallNameChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WallNameTakenPreview() {
    EnteGramTheme {
        WallNameScreen(
            wallName = "lena",
            availability = WallNameAvailability.Taken,
            isLoading = false,
            error = null,
            isValid = false,
            onWallNameChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WallNameCreatingPreview() {
    EnteGramTheme {
        WallNameScreen(
            wallName = "myhandle",
            availability = WallNameAvailability.Available,
            isLoading = true,
            error = null,
            isValid = true,
            onWallNameChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}
