package com.hu3h.biktv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.hu3h.biktv.data.session.BiliSessionStoreImpl
import com.hu3h.biktv.data.session.NcmSessionStoreImpl
import com.hu3h.biktv.ui.login.BiliLoginScreen
import com.hu3h.biktv.ui.login.NcmLoginScreen

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val biliSession by BiliSessionStoreImpl(context).sessionFlow.collectAsState(initial = null)
    val ncmSession by NcmSessionStoreImpl(context).sessionFlow.collectAsState(initial = null)

    val mode = remember { mutableStateOf(SettingPage.Main) }

    val bg = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface
        )
    )

    when (mode.value) {
        SettingPage.Bili -> BiliLoginScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { mode.value = SettingPage.Main }
        )
        SettingPage.Ncm -> NcmLoginScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { mode.value = SettingPage.Main }
        )
        SettingPage.Main -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        onClick = onBack
                    ) {
                        Box(
                            modifier = Modifier.padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Text("设置", style = MaterialTheme.typography.titleLarge)
                }

                SectionTitle("账号")
                CardBlock {
                    SettingRow(
                        icon = Icons.Outlined.Person,
                        iconTint = Color(0xFF4D8EFF),
                        title = "Bilibili 登录",
                        subtitle = if (biliSession != null) "已登录" else "未登录",
                        onClick = { mode.value = SettingPage.Bili }
                    )
                    SettingRow(
                        icon = Icons.Outlined.Person,
                        iconTint = Color(0xFF2CC06B),
                        title = "网易云登录",
                        subtitle = if (ncmSession != null) "已登录" else "未登录",
                        onClick = { mode.value = SettingPage.Ncm }
                    )
                }
            }
        }
    }
}

private enum class SettingPage { Main, Bili, Ncm }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = iconTint)
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "进入")
        }
    }
}
