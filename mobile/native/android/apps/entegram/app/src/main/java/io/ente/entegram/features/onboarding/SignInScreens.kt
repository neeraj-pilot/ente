package io.ente.entegram.features.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingBackground(modifier = modifier) {
        ColumnScopeContent {
            Spacer(modifier = Modifier.weight(0.8f))

            Text(
                text = "enteGram",
                style = Typo.display,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(Space.md))
            Text(
                text = "Your private corner on the internet",
                style = Typo.title,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                text = "End-to-end encrypted updates,\nvisible only to people you choose.",
                style = Typo.body,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.weight(1f))

            OnboardingContinueButton(
                text = "Create account",
                onClick = onCreateAccount,
            )
            Spacer(modifier = Modifier.height(Space.sm))
            TextButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Sign in",
                    style = Typo.bodyEmphasized,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            Spacer(modifier = Modifier.height(Space.lg))
        }
    }
}

@Composable
fun SignInEmailScreen(
    email: String,
    isLoading: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
    isEmailValid: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    OnboardingScaffold(
        currentStep = 0,
        onBack = onBack,
        showStepIndicator = false,
        modifier = modifier,
    ) {
        OnboardingHeadline(
            title = "Sign in",
            subtitle = "Enter the email linked to your account",
        )

        Spacer(modifier = Modifier.height(Space.xl))

        OnboardingTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "you@example.com",
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onGo = { if (isEmailValid && !isLoading) onContinue() },
            ),
            enabled = !isLoading,
            modifier = Modifier.focusRequester(focusRequester),
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
            enabled = isEmailValid,
            isLoading = isLoading,
            onClick = onContinue,
        )

        Spacer(modifier = Modifier.height(Space.sm))

        TextButton(
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Create a new account",
                style = Typo.caption,
                color = Color.White.copy(alpha = 0.6f),
            )
        }

        Spacer(modifier = Modifier.height(Space.lg))
    }
}

@Composable
fun AuthPasswordScreen(
    title: String,
    subtitle: String,
    password: String,
    confirmPassword: String? = null,
    error: String?,
    isLoading: Boolean,
    actionLabel: String,
    secondaryActionLabel: String? = null,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: ((String) -> Unit)? = null,
    strength: Int? = null,
    passwordsMatch: Boolean = true,
    isValid: Boolean,
    onSubmit: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    OnboardingScaffold(
        currentStep = 0,
        onBack = onBack,
        showStepIndicator = false,
        modifier = modifier,
    ) {
        OnboardingHeadline(title = title, subtitle = subtitle)

        Spacer(modifier = Modifier.height(Space.xl))

        PasswordField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "Password",
            showPassword = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible },
            imeAction = if (confirmPassword != null) ImeAction.Next else ImeAction.Done,
            onDone = { if (confirmPassword == null && isValid) onSubmit() },
            modifier = Modifier.focusRequester(focusRequester),
        )

        if (strength != null && password.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Space.sm))
            AuthPasswordStrengthMeter(strength = strength)
            Spacer(modifier = Modifier.height(Space.xs))
            Text(
                text = when (strength) {
                    0, 1 -> "Weak"
                    2 -> "Fair"
                    3 -> "Good"
                    else -> "Strong"
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

        if (confirmPassword != null && onConfirmPasswordChange != null) {
            Spacer(modifier = Modifier.height(Space.md))
            PasswordField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                placeholder = "Confirm password",
                showPassword = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible },
                imeAction = ImeAction.Done,
                onDone = { if (isValid) onSubmit() },
                isError = confirmPassword.isNotEmpty() && !passwordsMatch,
            )
            if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                Spacer(modifier = Modifier.height(Space.xs))
                Text(
                    text = "Passwords don't match",
                    style = Typo.caption,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                )
            }
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
            text = actionLabel,
            enabled = isValid,
            isLoading = isLoading,
            onClick = onSubmit,
        )

        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(modifier = Modifier.height(Space.sm))
            TextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = secondaryActionLabel,
                    style = Typo.caption,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(modifier = Modifier.height(Space.lg))
    }
}

@Composable
fun RecoveryKeyEntryScreen(
    email: String,
    recoveryKey: String,
    error: String?,
    isLoading: Boolean,
    onRecoveryKeyChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    canContinue: Boolean,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        currentStep = 0,
        onBack = onBack,
        showStepIndicator = false,
        modifier = modifier,
    ) {
        OnboardingHeadline(
            title = "Recovery key",
            subtitle = "Enter the recovery key for $email to reset your password.",
        )

        Spacer(modifier = Modifier.height(Space.xl))

        OnboardingTextField(
            value = recoveryKey,
            onValueChange = onRecoveryKeyChange,
            placeholder = "xxxx-xxxx-xxxx-xxxx",
            singleLine = false,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { if (canContinue && !isLoading) onContinue() },
            ),
            modifier = Modifier.height(144.dp),
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                text = error,
                style = Typo.caption,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
            )
        }

        Spacer(modifier = Modifier.height(Space.sm))
        Text(
            text = "This is the key you saved when you created the account.",
            style = Typo.caption,
            color = Color.White.copy(alpha = 0.45f),
        )

        Spacer(modifier = Modifier.weight(1f))

        OnboardingContinueButton(
            text = "Continue",
            enabled = canContinue,
            isLoading = isLoading,
            onClick = onContinue,
        )

        Spacer(modifier = Modifier.height(Space.lg))
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction,
    onDone: () -> Unit,
    isError: Boolean = false,
) {
    OnboardingTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onDone() },
        ),
        visualTransformation = if (showPassword) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onTogglePassword) {
                Icon(
                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        isError = isError,
        modifier = modifier,
    )
}

@Composable
private fun AuthPasswordStrengthMeter(
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
                label = "auth-strength-$i",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        color = color,
                        shape = RoundedCornerShape(Radius.pill),
                    ),
            )
        }
    }
}

@Composable
private fun ColumnScopeContent(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.lg)
            .padding(top = Space.xxl)
            .padding(bottom = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
