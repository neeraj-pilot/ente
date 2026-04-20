package io.ente.entegram.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@Composable
fun <T> SegmentedTabBar(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var rowSize by remember { mutableIntStateOf(0) }
    var rowHeight by remember { mutableIntStateOf(0) }
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val tabCount = items.size.coerceAtLeast(1)

    val tabWidthDp = with(density) { (rowSize / tabCount).toDp() }
    val rowHeightDp = with(density) { rowHeight.toDp() }
    val pillOffset by animateDpAsState(
        targetValue = tabWidthDp * selectedIndex,
        animationSpec = Motion.soft(),
        label = "pill-offset",
    )

    val minTabHeight = 36.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Space.xs)
            .onSizeChanged { size: IntSize -> rowSize = size.width },
    ) {
        // Animated selection pill — matches Row height
        if (rowSize > 0 && rowHeight > 0) {
            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(tabWidthDp)
                    .height(rowHeightDp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        // Tab labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size: IntSize -> rowHeight = size.height },
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = minTabHeight)
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onItemSelected(item)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(item),
                        style = if (isSelected) Typo.bodyEmphasized else Typo.body,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SegmentedTabBarPreview() {
    EnteGramTheme {
        var selected by remember { mutableIntStateOf(0) }
        val tabs = listOf("Requests", "Sent", "Followers")
        SegmentedTabBar(
            items = tabs,
            selectedItem = tabs[selected],
            onItemSelected = { selected = tabs.indexOf(it) },
            label = { it },
            modifier = Modifier.padding(Space.md),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SegmentedTabBarTwoTabsPreview() {
    EnteGramTheme {
        var selected by remember { mutableIntStateOf(1) }
        val tabs = listOf("Following", "Followers")
        SegmentedTabBar(
            items = tabs,
            selectedItem = tabs[selected],
            onItemSelected = { selected = tabs.indexOf(it) },
            label = { it },
            modifier = Modifier.padding(Space.md),
        )
    }
}
