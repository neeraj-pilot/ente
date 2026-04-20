package io.ente.entegram.features.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.ente.entegram.R
import io.ente.entegram.core.models.Wall
import io.ente.entegram.ui.components.AvatarView
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    wall: Wall,
    displayName: String,
    bio: String,
    selectedAvatarPreviewUri: Uri?,
    removeAvatarRequested: Boolean,
    state: SettingsProfileEditUiState,
    onDisplayNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = Space.xs, bottom = Space.sm)
                    .size(width = 40.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(percent = 50),
                    ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.xl),
        ) {
            // ── Header ─────────────────────────────────────────
            Text(
                text = stringResource(R.string.settings_edit_profile),
                style = Typo.titleXL,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = stringResource(R.string.settings_edit_profile_body),
                style = Typo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.lg))

            // ── Avatar ─────────────────────────────────────────
            EditableAvatar(
                wall = wall,
                previewUri = selectedAvatarPreviewUri,
                removeAvatarRequested = removeAvatarRequested,
                onPickAvatar = onPickAvatar,
                onRemoveAvatar = onRemoveAvatar,
            )

            Spacer(Modifier.height(Space.lg))

            // ── Form fields ────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                SheetField(title = stringResource(R.string.settings_display_name_label)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = onDisplayNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.isSaving,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(Radius.lg),
                        colors = sheetFieldColors(),
                    )
                }

                SheetField(title = stringResource(R.string.settings_bio_label)) {
                    OutlinedTextField(
                        value = bio,
                        onValueChange = onBioChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        enabled = !state.isSaving,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                        shape = RoundedCornerShape(Radius.lg),
                        colors = sheetFieldColors(),
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = state.errorMessage,
                    style = Typo.caption,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Space.lg))

            // ── Save ───────────────────────────────────────────
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(Radius.lg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_save_profile),
                        style = Typo.bodyEmphasized,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetField(
    title: String,
    supporting: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            text = title,
            style = Typo.caption.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
        if (!supporting.isNullOrBlank()) {
            Text(
                text = supporting,
                style = Typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun EditableAvatar(
    wall: Wall,
    previewUri: Uri?,
    removeAvatarRequested: Boolean,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
) {
    val context = LocalContext.current
    val hasAvatar = previewUri != null || (wall.avatarObjectKey != null && !removeAvatarRequested)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Avatar with camera badge
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPickAvatar),
            ) {
                when {
                    previewUri != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(previewUri)
                                .build(),
                            contentDescription = stringResource(
                                R.string.avatar_description,
                                wall.displayName ?: "@${wall.slug}",
                            ),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    removeAvatarRequested -> {
                        AvatarView(
                            slug = wall.slug,
                            displayName = wall.displayName,
                            modifier = Modifier.fillMaxSize(),
                            size = 100.dp,
                        )
                    }

                    else -> {
                        AvatarView(
                            slug = wall.slug,
                            displayName = wall.displayName,
                            avatarObjectKey = wall.avatarObjectKey,
                            modifier = Modifier.fillMaxSize(),
                            size = 100.dp,
                        )
                    }
                }
            }

            // Camera badge — bottom-end of avatar
            IconButton(
                onClick = onPickAvatar,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = stringResource(R.string.settings_change_avatar),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // "Remove photo" text link below avatar
        if (hasAvatar) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = stringResource(R.string.settings_remove_avatar),
                style = Typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onRemoveAvatar),
            )
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
)
