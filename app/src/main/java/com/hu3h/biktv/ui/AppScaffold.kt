package com.hu3h.biktv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

enum class AppTab(val label: String) {
    Main("KTV"),
    Settings("设置")
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    contentForTab: @Composable (AppTab) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 720
    var tab by rememberSaveable { mutableStateOf(AppTab.Main) }
    var showSidebar by rememberSaveable { mutableStateOf(isTablet) }

    val bg = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        if (isTablet && showSidebar) {
            Row(modifier = Modifier.fillMaxSize()) {
                SideRail(
                    tab = tab,
                    onTab = { tab = it },
                    onToggle = { showSidebar = !showSidebar }
                )
                Spacer(modifier = Modifier.size(12.dp))
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.98f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 1.02f))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(8.dp)
                ) { t ->
                    contentForTab(t)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.98f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 1.02f))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                ) { t ->
                    contentForTab(t)
                }
                SolidBottomBar(
                    tab = tab,
                    onTab = { tab = it },
                    onToggleSidebar = if (isTablet) {
                        { showSidebar = !showSidebar }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun SolidBottomBar(
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    onToggleSidebar: (() -> Unit)?
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = tab == AppTab.Main,
            onClick = { onTab(AppTab.Main) },
            icon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = "KTV") },
            label = { Text("KTV") }
        )
        NavigationBarItem(
            selected = tab == AppTab.Settings,
            onClick = { onTab(AppTab.Settings) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
            label = { Text("设置") }
        )
        if (onToggleSidebar != null) {
            NavigationBarItem(
                selected = false,
                onClick = { onToggleSidebar() },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = "侧边栏") },
                label = { Text("侧边栏") }
            )
        }
    }
}

@Composable
private fun SideRail(
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("BiKTV", style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(12.dp))
        NavPill(
            label = "KTV",
            icon = Icons.Outlined.VideoLibrary,
            selected = tab == AppTab.Main,
            onClick = { onTab(AppTab.Main) }
        )
        NavPill(
            label = "设置",
            icon = Icons.Outlined.Settings,
            selected = tab == AppTab.Settings,
            onClick = { onTab(AppTab.Settings) }
        )
        Spacer(modifier = Modifier.weight(1f))
        NavPill(
            label = "隐藏",
            icon = Icons.Outlined.Settings,
            selected = false,
            onClick = onToggle
        )
    }
}

@Composable
private fun NavPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        tonalElevation = if (selected) 4.dp else 0.dp,
        modifier = Modifier
            .height(44.dp)
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = fg)
            }
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}
