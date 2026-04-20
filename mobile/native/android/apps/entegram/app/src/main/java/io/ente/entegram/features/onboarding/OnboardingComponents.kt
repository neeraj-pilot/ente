package io.ente.entegram.features.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private val GradientStart = Color(0xFF0D0D1E)
private val GradientEnd = Color(0xFF2B1247)

@Composable
fun OnboardingBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(GradientStart, GradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
            .navigationBarsPadding()
            .imePadding(),
    ) {
        content()
    }
}

/**
 * Animated step indicator: current step stretches into a pill.
 */
@Composable
fun OnboardingStepIndicator(
    currentStep: Int,
    totalSteps: Int = 5,
    modifier: Modifier = Modifier,
) {
    val stepLabel = stringResource(R.string.onboarding_step_n_of_total, currentStep + 1, totalSteps)
    Row(
        modifier = modifier
            .semantics { contentDescription = stepLabel },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i == currentStep
            val isPast = i < currentStep

            val width by animateDpAsState(
                targetValue = if (isActive) 20.dp else 6.dp,
                animationSpec = Motion.soft(),
                label = "step-width-$i",
            )

            val color by animateColorAsState(
                targetValue = when {
                    isActive -> Color.White.copy(alpha = 0.9f)
                    isPast -> Color.White.copy(alpha = 0.45f)
                    else -> Color.White.copy(alpha = 0.18f)
                },
                animationSpec = Motion.quickFade(),
                label = "step-color-$i",
            )

            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .background(color, CircleShape),
            )
        }
    }
}

/**
 * Shared scaffold for onboarding screens: gradient background + step indicator + optional back.
 * Content receives a stagger-in animation on first appearance.
 * The content lambda is scoped to [ColumnScope] so callers can use `Modifier.weight(1f)`.
 */
@Composable
fun OnboardingScaffold(
    currentStep: Int,
    onBack: (() -> Unit)? = null,
    totalSteps: Int = 5,
    showStepIndicator: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Stagger-in: content appears after a short delay so the nav transition settles first
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60) // let the nav slide begin
        contentVisible = true
    }

    OnboardingBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Space.lg),
        ) {
            // Top bar row: back button (if present) + step indicator centered
            Spacer(modifier = Modifier.height(Space.md))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                // Back button (left)
                if (onBack != null) {
                    val haptic = LocalHapticFeedback.current
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.onboarding_go_back),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (showStepIndicator) {
                    OnboardingStepIndicator(
                        currentStep = currentStep,
                        totalSteps = totalSteps,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Space.xl))

            // Content with stagger-in wrapped in a Column that fills remaining space
            AnimatedVisibility(
                visible = contentVisible,
                modifier = Modifier.weight(1f),
                enter = slideInVertically(
                    animationSpec = Motion.soft(),
                    initialOffsetY = { (it * 0.06f).toInt() },
                ) + fadeIn(Motion.quickFade()),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}

@Composable
fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = placeholder,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            isFocused -> Color.White.copy(alpha = 0.4f)
            else -> Color.White.copy(alpha = 0.15f)
        },
        animationSpec = Motion.quickFade(),
        label = "border-color",
    )

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityLabel }
            .clip(RoundedCornerShape(Radius.md))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, borderColor, RoundedCornerShape(Radius.md)),
        textStyle = Typo.composer.copy(
            color = Color.White,
        ),
        placeholder = {
            Text(
                text = placeholder,
                style = Typo.composer,
                color = Color.White.copy(alpha = 0.35f),
            )
        },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        enabled = enabled,
        isError = isError,
        interactionSource = interactionSource,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
fun OnboardingHeadline(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = Typo.titleXL,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(Space.sm))
        Text(
            text = subtitle,
            style = Typo.body,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
fun OnboardingContinueButton(
    text: String? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedText = text ?: stringResource(R.string.onboarding_continue)
    val haptic = LocalHapticFeedback.current
    val alpha by animateFloatAsState(
        targetValue = if (enabled && !isLoading) 1f else 0.5f,
        animationSpec = Motion.quickFade(),
        label = "btn-alpha",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = Motion.snap(),
        label = "btn-scale",
    )

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .scale(scale),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = alpha),
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.5f),
            disabledContentColor = Color.Black.copy(alpha = 0.5f),
        ),
        interactionSource = interactionSource,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.Black,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = resolvedText,
                style = Typo.bodyEmphasized,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingBackgroundPreview() {
    EnteGramTheme {
        OnboardingBackground {
            Column(
                modifier = Modifier.padding(Space.lg),
            ) {
                OnboardingHeadline(
                    title = "Welcome",
                    subtitle = "Enter your email to get started.",
                )
                Spacer(modifier = Modifier.height(Space.xl))
                OnboardingTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = "Email address",
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StepIndicatorStep1Preview() {
    EnteGramTheme {
        OnboardingBackground {
            Column(modifier = Modifier.padding(Space.lg)) {
                OnboardingStepIndicator(currentStep = 0)
                Spacer(modifier = Modifier.height(Space.md))
                OnboardingStepIndicator(currentStep = 2)
                Spacer(modifier = Modifier.height(Space.md))
                OnboardingStepIndicator(currentStep = 4)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScaffoldPreview() {
    EnteGramTheme {
        OnboardingScaffold(
            currentStep = 1,
            onBack = {},
        ) {
            Column {
                OnboardingHeadline(
                    title = "Check your email",
                    subtitle = "We sent a 6-digit code to user@example.com",
                )
                Spacer(modifier = Modifier.height(Space.xl))
                OnboardingTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = "Enter code",
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingContinueButtonPreview() {
    EnteGramTheme {
        OnboardingBackground {
            Column(modifier = Modifier.padding(Space.lg)) {
                OnboardingContinueButton(
                    text = "Continue",
                    enabled = true,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(Space.md))
                OnboardingContinueButton(
                    text = "Continue",
                    enabled = false,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(Space.md))
                OnboardingContinueButton(
                    text = "Continue",
                    isLoading = true,
                    onClick = {},
                )
            }
        }
    }
}
