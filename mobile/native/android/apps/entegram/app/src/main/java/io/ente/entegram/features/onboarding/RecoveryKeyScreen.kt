package io.ente.entegram.features.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@Composable
fun RecoveryKeyScreen(
    recoveryKey: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    OnboardingScaffold(
        currentStep = 4,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(Space.lg))

            Text(
                text = stringResource(R.string.onboarding_recovery_title),
                style = Typo.titleXL,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(Space.sm))

            Text(
                text = stringResource(R.string.onboarding_recovery_subtitle),
                style = Typo.body,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(Space.xxl))

        // Recovery key display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(Radius.lg),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.15f),
                    RoundedCornerShape(Radius.lg),
                )
                .padding(Space.lg),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = recoveryKey,
                    style = Typo.mono,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(Space.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Recovery Key", recoveryKey),
                            )
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            copied = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.onboarding_recovery_copy),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = copied,
                        enter = fadeIn(Motion.quickFade()),
                        exit = fadeOut(Motion.quickFade()),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_recovery_copied),
                            style = Typo.caption,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Space.lg))

        Text(
            text = stringResource(R.string.onboarding_recovery_warning),
            style = Typo.caption,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        OnboardingContinueButton(
            text = stringResource(R.string.onboarding_recovery_done),
            enabled = true,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDone()
            },
        )

        Spacer(modifier = Modifier.height(Space.lg))
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoveryKeyPreview() {
    EnteGramTheme {
        RecoveryKeyScreen(
            recoveryKey = "ab3k-m7np-q2st-uvw4-x8yz-h6jk",
            onDone = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecoveryKeyCopiedPreview() {
    EnteGramTheme {
        RecoveryKeyScreen(
            recoveryKey = "ab3k-m7np-q2st-uvw4-x8yz-h6jk",
            onDone = {},
        )
    }
}
