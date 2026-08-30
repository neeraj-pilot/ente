package io.ente.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

enum class InputMessageKind { Helper, Error, Alert, Success }

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    placeholder: String? = null,
    message: String? = null,
    messageKind: InputMessageKind = InputMessageKind.Helper,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalEntePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val messageColor = when (messageKind) {
        InputMessageKind.Helper -> palette.mutedText
        InputMessageKind.Error, InputMessageKind.Alert -> palette.danger
        InputMessageKind.Success -> palette.primary
    }
    val isError = messageKind == InputMessageKind.Error || messageKind == InputMessageKind.Alert
    val border = when {
        isError -> palette.danger
        messageKind == InputMessageKind.Success -> palette.primary
        focused -> palette.hintText
        else -> palette.faintBorder
    }
    val fieldModifier = Modifier
        .fillMaxWidth()
        .then(if (singleLine) Modifier.height(52.dp) else Modifier.heightIn(min = 52.dp))
        .clip(RoundedCornerShape(EnteRadius.large))
        .background(if (enabled) palette.surface else palette.fill)
        .border(1.dp, border, RoundedCornerShape(EnteRadius.large))
        .padding(horizontal = EnteSpacing.lg, vertical = if (singleLine) 0.dp else EnteSpacing.lg)
    Column(modifier) {
        if (label != null) {
            Row {
                Text(label, style = EnteTypography.body, color = palette.text)
                if (required) {
                    Spacer(Modifier.width(2.dp))
                    Text("*", style = EnteTypography.bodyBold, color = palette.danger)
                }
            }
            Spacer(Modifier.height(EnteSpacing.sm))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = EnteTypography.body.copy(color = if (enabled) palette.text else palette.disabledText),
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(palette.primary),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                CompositionLocalProvider(LocalContentColor provides if (focused) palette.primary else palette.hintText) {
                    Row(
                        modifier = if (singleLine) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    ) {
                        leading?.let {
                            Box(Modifier.size(EnteIconSize.medium), contentAlignment = Alignment.Center) { it() }
                            Spacer(Modifier.width(EnteSpacing.sm))
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                        ) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = EnteTypography.body, color = palette.hintText)
                            }
                            innerTextField()
                        }
                        trailing?.let {
                            Spacer(Modifier.width(EnteSpacing.sm))
                            Box(Modifier.size(EnteIconSize.medium), contentAlignment = Alignment.Center) { it() }
                        }
                    }
                }
            },
        )
        if (message != null) {
            Spacer(Modifier.height(EnteSpacing.sm))
            Text(message, style = EnteTypography.mini, color = messageColor)
        }
    }
}
