package io.ente.entegram.features.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo
import kotlinx.coroutines.delay

@Composable
fun OTTVerificationScreen(
    title: String = stringResource(R.string.onboarding_ott_title),
    subtitle: String = "",
    verifyLabel: String = stringResource(R.string.onboarding_ott_verify),
    currentStep: Int = 1,
    totalSteps: Int = 5,
    showProgress: Boolean = true,
    code: String,
    email: String,
    isLoading: Boolean,
    error: String?,
    resendCooldown: Int,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        delay(200) // wait for OnboardingScaffold's AnimatedVisibility to compose the field
        focusRequester.requestFocus()
    }

    // Auto-submit on complete
    LaunchedEffect(code) {
        if (code.length == 6) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onVerify()
        }
    }

    OnboardingScaffold(
        currentStep = currentStep,
        totalSteps = totalSteps,
        showStepIndicator = showProgress,
        onBack = onBack,
        modifier = modifier,
    ) {
        OnboardingHeadline(
            title = title,
            subtitle = subtitle,
        )

        Spacer(modifier = Modifier.height(Space.xxl))

        // Hidden text field overlays the digit boxes to capture keyboard input
        Box(modifier = Modifier.fillMaxWidth()) {
            // Visual digit boxes
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                for (i in 0 until 6) {
                    val digit = code.getOrNull(i)?.toString() ?: ""
                    val isCurrent = i == code.length && code.length < 6
                    val isFilled = digit.isNotEmpty()

                    DigitBox(
                        digit = digit,
                        isCurrent = isCurrent,
                        isFilled = isFilled,
                    )
                }
            }

            // Invisible text field spanning the whole box
            val verificationCodeLabel = stringResource(R.string.onboarding_ott_verification_code)
            BasicTextField(
                value = code,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() }.take(6)
                    if (filtered.length > code.length) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onCodeChange(filtered)
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .matchParentSize()
                    .background(Color.Transparent)
                    .semantics { contentDescription = verificationCodeLabel },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = SolidColor(Color.Transparent),
            )
        }

        Spacer(modifier = Modifier.height(Space.lg))

        // Error
        if (error != null) {
            Text(
                text = error,
                style = Typo.caption,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
            )
            Spacer(modifier = Modifier.height(Space.sm))
        }

        // Resend button
        TextButton(
            onClick = onResend,
            enabled = resendCooldown == 0 && !isLoading,
        ) {
            Text(
                text = if (resendCooldown > 0) {
                    stringResource(R.string.onboarding_ott_resend_cooldown, resendCooldown)
                } else {
                    stringResource(R.string.onboarding_ott_resend)
                },
                style = Typo.body,
                color = if (resendCooldown > 0) {
                    Color.White.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        OnboardingContinueButton(
            text = verifyLabel,
            enabled = code.length == 6,
            isLoading = isLoading,
            onClick = onVerify,
        )

        Spacer(modifier = Modifier.height(Space.lg))
    }
}

@Composable
private fun DigitBox(
    digit: String,
    isCurrent: Boolean,
    isFilled: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrent -> Color.White.copy(alpha = 0.5f)
            isFilled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.15f)
        },
        animationSpec = Motion.quickFade(),
        label = "digit-border",
    )

    val scale by animateFloatAsState(
        targetValue = if (isFilled) 1f else 0.95f,
        animationSpec = Motion.snap(),
        label = "digit-scale",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 58.dp)
            .scale(scale)
            .background(
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(Radius.md),
            )
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(Radius.md),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit,
            style = Typo.title.copy(
                textAlign = TextAlign.Center,
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OTTEmptyPreview() {
    EnteGramTheme {
        OTTVerificationScreen(
            code = "",
            email = "user@example.com",
            isLoading = false,
            error = null,
            resendCooldown = 30,
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OTTPartialPreview() {
    EnteGramTheme {
        OTTVerificationScreen(
            code = "123",
            email = "user@example.com",
            isLoading = false,
            error = null,
            resendCooldown = 15,
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OTTCompletePreview() {
    EnteGramTheme {
        OTTVerificationScreen(
            code = "123456",
            email = "user@example.com",
            isLoading = false,
            error = null,
            resendCooldown = 0,
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OTTResendReadyPreview() {
    EnteGramTheme {
        OTTVerificationScreen(
            code = "",
            email = "user@example.com",
            isLoading = false,
            error = null,
            resendCooldown = 0,
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onBack = {},
        )
    }
}
