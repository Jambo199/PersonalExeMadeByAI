package com.jambo199.vividmusic.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.MainSection

private fun MainSection.icon(selected: Boolean): ImageVector = when (this) {
    MainSection.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    MainSection.LIBRARY -> if (selected) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
    MainSection.FOLDERS -> if (selected) Icons.Rounded.Folder else Icons.Outlined.Folder
    MainSection.NCS -> if (selected) Icons.Rounded.Headphones else Icons.Outlined.Headphones
}

/** The bottom bar: a rounded, floating card with a pill around the page you're on. */
@Composable
fun MainNavigationBar(section: MainSection, onSelect: (MainSection) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.height(72.dp),
        ) {
            MainSection.entries.forEach { s ->
                val selected = s == section
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(s) },
                    icon = { Icon(s.icon(selected), contentDescription = null) },
                    label = { Text(s.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                )
            }
        }
    }
}

/** The same choices down the side, for landscape where height is short. */
@Composable
fun MainNavigationRail(section: MainSection, onSelect: (MainSection) -> Unit, modifier: Modifier = Modifier) {
    NavigationRail(modifier.fillMaxHeight()) {
        MainSection.entries.forEach { s ->
            val selected = s == section
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(s) },
                icon = { Icon(s.icon(selected), contentDescription = null) },
                label = { Text(s.label) },
            )
        }
    }
}
