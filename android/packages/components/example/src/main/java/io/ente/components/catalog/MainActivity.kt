package io.ente.components.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ente.components.ActionButton
import io.ente.components.ActionKind
import io.ente.components.Avatar
import io.ente.components.AvatarSize
import io.ente.components.Banner
import io.ente.components.BannerKind
import io.ente.components.Checkmark
import io.ente.components.EnteApp
import io.ente.components.EnteIconSize
import io.ente.components.EnteModalSheet
import io.ente.components.EnteSpacing
import io.ente.components.EnteTheme
import io.ente.components.EnteTypography
import io.ente.components.FilterChip
import io.ente.components.FloatingAction
import io.ente.components.IconAction
import io.ente.components.InputField
import io.ente.components.InputMessageKind
import io.ente.components.IconActionKind
import io.ente.components.LabeledControl
import io.ente.components.LocalEntePalette
import io.ente.components.MenuGroup
import io.ente.components.MenuRow
import io.ente.components.Radio
import io.ente.components.SettingsRow
import io.ente.components.SheetContent
import io.ente.components.Tag
import io.ente.components.Toggle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Catalog() }
    }
}

@Composable
private fun Catalog() {
    var dark by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var chipSelected by rememberSaveable { mutableStateOf(true) }
    var tagSelected by rememberSaveable { mutableStateOf(true) }
    var checked by rememberSaveable { mutableStateOf(true) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var showSheet by rememberSaveable { mutableStateOf(false) }

    EnteTheme(app = EnteApp.Photos, darkTheme = dark) {
        val palette = LocalEntePalette.current
        Surface(color = palette.background, modifier = Modifier.fillMaxSize()) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(EnteSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(EnteSpacing.xl),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Components", style = EnteTypography.display2, color = palette.text, modifier = Modifier.weight(1f))
                        IconAction(onClick = { dark = !dark }) {
                            Text(if (dark) "☀" else "☾", style = EnteTypography.heading2)
                        }
                    }
                    Text("A compact visual catalog for the shared native layer.", style = EnteTypography.body, color = palette.mutedText)

                    Section("Identity") {
                        Row(horizontalArrangement = Arrangement.spacedBy(EnteSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Avatar("Aman Gupta", identity = "aman@example.com", size = AvatarSize.Contact)
                            Column {
                                Text("Aman Gupta", style = EnteTypography.large, color = palette.text)
                                Text("aman@example.com", style = EnteTypography.mini, color = palette.mutedText)
                            }
                        }
                    }

                    Section("Choices") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(EnteSpacing.sm),
                        ) {
                            FilterChip("Recent", selected = chipSelected, onClick = { chipSelected = !chipSelected })
                            FilterChip("Favorites", selected = !chipSelected, onClick = { chipSelected = !chipSelected })
                            Tag("Private", selected = tagSelected, onClick = { tagSelected = !tagSelected })
                        }
                    }

                    Section("Input") {
                        InputField(
                            value = query,
                            onValueChange = { query = it },
                            label = "Album name",
                            required = true,
                            placeholder = "Summer 2026",
                            message = "Visible only to people you share it with.",
                            messageKind = InputMessageKind.Helper,
                        )
                    }

                    Section("Controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(EnteSpacing.sm)) {
                            LabeledControl("Include metadata", subtitle = "Location and camera details") {
                                Checkmark(checked = checked, onCheckedChange = { checked = it })
                            }
                            LabeledControl("Notifications", subtitle = "Share activity") {
                                Toggle(checked = notifications, onCheckedChange = { notifications = it })
                            }
                            LabeledControl("Keep original quality") {
                                Radio(selected = true, onClick = {})
                            }
                        }
                    }

                    Section("Feedback") {
                        Banner(
                            title = "Backup is protected",
                            subtitle = "Your photos are encrypted before upload.",
                            kind = BannerKind.Success,
                            onClick = {},
                            leading = { Text("✓", style = EnteTypography.heading2) },
                        )
                    }

                    Section("Rows") {
                        MenuGroup {
                            MenuRow("Account", subtitle = "aman@example.com", onClick = {}, leading = { Avatar("Aman Gupta") })
                            MenuRow("Storage", subtitle = "2.4 GB of 10 GB", onClick = {}, leading = { Text("◌", style = EnteTypography.heading2) })
                        }
                        SettingsRow("Delete account", destructive = true, onClick = {}, trailing = { Text("›", style = EnteTypography.heading1) })
                    }

                    Section("Actions") {
                        Row(horizontalArrangement = Arrangement.spacedBy(EnteSpacing.sm)) {
                            ActionButton("Save", onClick = {}, modifier = Modifier.weight(1f), fullWidth = false)
                            ActionButton("Cancel", onClick = {}, modifier = Modifier.weight(1f), kind = ActionKind.Secondary, fullWidth = false)
                        }
                        ActionButton("Open sheet", kind = ActionKind.Link, onClick = { showSheet = true })
                    }
                    Spacer(Modifier.size(72.dp))
                }
                FloatingAction(
                    onClick = { showSheet = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(EnteSpacing.lg),
                ) {
                    Text("+", style = EnteTypography.display2)
                }
            }
        }
        if (showSheet) {
            EnteModalSheet(onDismissRequest = { showSheet = false }) {
                SheetContent(
                    title = "Create album",
                    message = "Albums keep your library organized.",
                    close = { IconAction(kind = IconActionKind.Unfilled, onClick = { showSheet = false }) { Text("×", style = EnteTypography.heading2) } },
                ) {
                    ActionButton("Create album", onClick = { showSheet = false })
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val palette = LocalEntePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(EnteSpacing.md)) {
        Text(title, style = EnteTypography.heading2, color = palette.text)
        content()
    }
}
