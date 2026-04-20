package io.ente.entegram.features.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo
import kotlinx.coroutines.delay

private val StrengthWeak = Color(0xFFF87171)
private val StrengthFair = Color(0xFFFB923C)
private val StrengthGood = Color(0xFFFBBF24)
private val StrengthStrong = Color(0xFF4ADE80)

@Composable
fun PasswordSetupScreen(
    password: String,
    confirmPassword: String,
    strength: Int,
    passwordsMatch: Boolean,
    isValid: Boolean,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200) // wait for OnboardingScaffold's AnimatedVisibility to compose the field
        focusRequester.requestFocus()
    }

    OnboardingScaffold(
        currentStep = 2,
        onBack = onBack,
        modifier = modifier,
    ) {
        OnboardingHeadline(
            title = stringResource(R.string.onboarding_password_title),
            subtitle = stringResource(R.string.onboarding_password_subtitle),
        )

        Spacer(modifier = Modifier.height(Space.xl))

        val passwordPlaceholder = stringResource(R.string.onboarding_password_placeholder)
        OnboardingTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = passwordPlaceholder,
            accessibilityLabel = passwordPlaceholder,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = if (passwordVisible) stringResource(R.string.onboarding_password_hide) else stringResource(R.string.onboarding_password_show),
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            modifier = Modifier.focusRequester(focusRequester),
        )

        Spacer(modifier = Modifier.height(Space.sm))

        // Strength meter
        if (password.isNotEmpty()) {
            PasswordStrengthMeter(strength = strength)
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                text = when (strength) {
                    0 -> stringResource(R.string.onboarding_password_strength_weak)
                    1 -> stringResource(R.string.onboarding_password_strength_weak)
                    2 -> stringResource(R.string.onboarding_password_strength_fair)
                    3 -> stringResource(R.string.onboarding_password_strength_good)
                    else -> stringResource(R.string.onboarding_password_strength_strong)
                },
                style = Typo.caption,
                color = when (strength) {
                    0, 1 -> StrengthWeak
                    2 -> StrengthFair
                    3 -> StrengthGood
                    else -> StrengthStrong
                },
            )
        }

        Spacer(modifier = Modifier.height(Space.md))

        val confirmPlaceholder = stringResource(R.string.onboarding_password_confirm_placeholder)
        OnboardingTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = confirmPlaceholder,
            accessibilityLabel = confirmPlaceholder,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { if (isValid) onContinue() },
            ),
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            isError = confirmPassword.isNotEmpty() && !passwordsMatch,
        )

        if (confirmPassword.isNotEmpty() && !passwordsMatch) {
            Spacer(modifier = Modifier.height(Space.xs))
            Text(
                text = stringResource(R.string.onboarding_password_mismatch),
                style = Typo.caption,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
            )
        }

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
            enabled = isValid,
            onClick = onContinue,
        )

        Spacer(modifier = Modifier.height(Space.lg))
    }
}

@Composable
private fun PasswordStrengthMeter(
    strength: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        for (i in 0 until 4) {
            val active = i < strength
            val color by animateColorAsState(
                targetValue = if (active) {
                    when (strength) {
                        1 -> StrengthWeak
                        2 -> StrengthFair
                        3 -> StrengthGood
                        else -> StrengthStrong
                    }
                } else {
                    Color.White.copy(alpha = 0.15f)
                },
                animationSpec = Motion.quickFade(),
                label = "strength-segment-$i",
            )

            val width by animateFloatAsState(
                targetValue = if (active) 1f else 1f,
                animationSpec = Motion.snap(),
                label = "strength-width-$i",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color, RoundedCornerShape(Radius.pill)),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordEmptyPreview() {
    EnteGramTheme {
        PasswordSetupScreen(
            password = "",
            confirmPassword = "",
            strength = 0,
            passwordsMatch = true,
            isValid = false,
            error = null,
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordWeakPreview() {
    EnteGramTheme {
        PasswordSetupScreen(
            password = "hello",
            confirmPassword = "",
            strength = 0,
            passwordsMatch = true,
            isValid = false,
            error = null,
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordStrongPreview() {
    EnteGramTheme {
        PasswordSetupScreen(
            password = "MyStr0ng!Pass",
            confirmPassword = "MyStr0ng!Pass",
            strength = 4,
            passwordsMatch = true,
            isValid = true,
            error = null,
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordMismatchPreview() {
    EnteGramTheme {
        PasswordSetupScreen(
            password = "MyStr0ng!Pass",
            confirmPassword = "different",
            strength = 4,
            passwordsMatch = false,
            isValid = false,
            error = null,
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onContinue = {},
            onBack = {},
        )
    }
}
