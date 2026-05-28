package com.fearlauncher.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

// --- DATA CLASS FOR VERSIONS ---
data class MinecraftVersion(
    val id: String,
    val type: String, // "release", "snapshot", "beta"
    val url: String,
    val releaseTime: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainAppNavigator(filesDir)
            }
        }
    }}

// --- THEME PALETTE ---
val DeepBlack = Color(0xFF050505)
val SurfaceBlack = Color(0xFF121212)
val CardGray = Color(0xFF1E1E1E)
val BorderGray = Color(0xFF333333)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0A0A0)
val AccentGreen = Color(0xFF2E7D32)
val AccentGreenGlow = Color(0xFF4CAF50)

@Composable
fun MainAppNavigator(filesDir: File) {
    var isDownloadComplete by remember { mutableStateOf(false) }
    
    // Global Shine Animation
    val infiniteTransition = rememberInfiniteTransition(label = "GlobalShine")
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -200f, 
        targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart)
    )

    val shineBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent),
        start = androidx.compose.ui.geometry.Offset(shineOffset, 0f),
        end = androidx.compose.ui.geometry.Offset(shineOffset + 200f, 0f)
    )

    Crossfade(targetState = isDownloadComplete, animationSpec = tween(800)) { screen ->
        if (!screen) DownloaderScreen(filesDir, shineBrush) { isDownloadComplete = true }
        else DashboardScreen(shineBrush, filesDir)
    }
}

// --- DASHBOARD WITH VERSION MANAGER ---
@Composable
fun DashboardScreen(shineBrush: Brush, filesDir: File) {
    var activeTab by remember { mutableStateOf("Home") }
    var selectedVersion by remember { mutableStateOf("Release 1.20.4") }
    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf(0f) }

    // Mock Data for Versions (In real app, fetch from Mojang API)
    val allVersions = remember {
        listOf(
            MinecraftVersion("1.20.4", "release", "", "2023-12-01"),
            MinecraftVersion("1.20.1", "release", "", "2023-06-01"),
            MinecraftVersion("1.19.4", "release", "", "2023-03-01"),            MinecraftVersion("24w14a", "snapshot", "", "2024-04-01"),
            MinecraftVersion("24w13a", "snapshot", "", "2024-03-01"),
            MinecraftVersion("b1.7.3", "beta", "", "2011-07-01")
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(activeTab) { activeTab = it }

            Column(modifier = Modifier.weight(1f)) {
                TopBar()

                Box(modifier = Modifier.weight(1f).padding(24.dp)) {
                    when (activeTab) {
                        "Home" -> HomeTabContent(selectedVersion, shineBrush)
                        "Java Edition" -> VersionManagerTab(
                            allVersions = allVersions,
                            selectedVersion = selectedVersion,
                            onVersionSelect = { selectedVersion = it.id },
                            onInstall = { version ->
                                isInstalling = true
                                // Simulate Download
                                CoroutineScope(Dispatchers.IO).launch {
                                    for(i in 1..100) { delay(30); installProgress = i/100f }
                                    withContext(Dispatchers.Main) {
                                        isInstalling = false
                                        selectedVersion = version.id
                                    }
                                }
                            },
                            isInstalling = isInstalling,
                            installProgress = installProgress
                        )
                        else -> PlaceholderContent("Coming Soon")
                    }
                }

                PlayBar(selectedVersion)
            }
        }
    }
}

// --- VERSION MANAGER TAB (The Core Feature) ---
@Composable
fun VersionManagerTab(
    allVersions: List<MinecraftVersion>,
    selectedVersion: String,
    onVersionSelect: (MinecraftVersion) -> Unit,    onInstall: (MinecraftVersion) -> Unit,
    isInstalling: Boolean,
    installProgress: Float
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") } // All, Release, Snapshot, Beta

    // Filter Logic
    val filteredVersions = allVersions.filter { v ->
        val matchesSearch = v.id.contains(searchQuery, ignoreCase = true)
        val matchesType = if (filterType == "All") true else v.type.equals(filterType, ignoreCase = true)
        matchesSearch && matchesType
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header & Search
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Library", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            
            // Aesthetic Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search versions...", color = TextSecondary, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.width(250.dp).height(40.dp),
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardGray,
                    unfocusedContainerColor = CardGray,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Release", "Snapshot", "Beta").forEach { type ->
                val isSelected = filterType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { filterType = type },
                    label = { Text(type, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(                        selectedContainerColor = AccentGreen,
                        selectedLabelColor = Color.White,
                        containerColor = CardGray,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Version List
        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(CardGray)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(filteredVersions) { version ->
                    VersionItem(
                        version = version,
                        isSelected = selectedVersion == version.id,
                        onSelect = { onVersionSelect(version) },
                        onInstall = { onInstall(version) },
                        isInstalling = isInstalling && selectedVersion == version.id,
                        progress = installProgress
                    )
                }
            }
        }
    }
}

@Composable
fun VersionItem(
    version: MinecraftVersion,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInstall: () -> Unit,
    isInstalling: Boolean,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) SurfaceBlack else CardGray),
        border = BorderStroke(1.dp, if (isSelected) AccentGreen else BorderGray)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {                Text(version.id, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("${version.type.uppercase()} • ${version.releaseTime}", color = TextSecondary, fontSize = 10.sp)
            }

            if (isSelected && !isInstalling) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Installed", tint = AccentGreen)
            } else if (isInstalling) {
                Box(modifier = Modifier.width(60.dp).height(6.dp).background(BorderGray, RoundedCornerShape(3.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(progress).height(6.dp).background(AccentGreen, RoundedCornerShape(3.dp)))
                }
            } else {
                TextButton(onClick = onInstall) {
                    Text("Install", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- HELPER COMPOSABLES (Sidebar, TopBar, etc.) ---
@Composable
fun Sidebar(activeTab: String, onTabClick: (String) -> Unit) {
    val items = listOf("Home" to Icons.Default.Home, "Java Edition" to Icons.Default.Code, "Skins" to Icons.Default.Face, "Settings" to Icons.Default.Settings)
    Column(modifier = Modifier.width(240.dp).fillMaxHeight().background(SurfaceBlack).border(BorderStroke(1.dp, BorderGray))) {
        Box(modifier = Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text("FEAR", modifier = Modifier.padding(start = 24.dp), fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text("LAUNCHER", modifier = Modifier.padding(start = 24.dp).offset(y = 18.dp), fontSize = 10.sp, fontWeight = FontWeight.Light, color = AccentGreen, letterSpacing = 2.sp)
        }
        HorizontalDivider(color = BorderGray)
        items.forEach { (label, icon) ->
            val isSelected = activeTab == label
            Row(modifier = Modifier.fillMaxWidth().height(56.dp).clickable { onTabClick(label) }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = label, tint = if (isSelected) AccentGreen else TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = label, color = if (isSelected) TextPrimary else TextSecondary, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
                if (isSelected) Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) { Box(modifier = Modifier.width(4.dp).height(24.dp).background(AccentGreen, RoundedCornerShape(2.dp))) }
            }
        }
    }
}

@Composable
fun TopBar() {
    Row(modifier = Modifier.fillMaxWidth().height(70.dp).background(SurfaceBlack).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("LIBRARY", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Steve", color = TextSecondary, fontSize = 14.sp)
        }    }
}

@Composable
fun HomeTabContent(version: String, shineBrush: Brush) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(modifier = Modifier.fillMaxWidth().height(200.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardGray), border = BorderStroke(1.dp, BorderGray)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                Column(modifier = Modifier.padding(24.dp).align(Alignment.BottomStart)) {
                    Text("MINECRAFT JAVA", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("Ready to Launch", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun PlayBar(version: String) {
    Box(modifier = Modifier.fillMaxWidth().height(90.dp).background(SurfaceBlack).border(BorderStroke(1.dp, BorderGray)), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(start = 30.dp)) {
                Text("Current Selection", color = TextSecondary, fontSize = 12.sp)
                Text(version, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
            Box(modifier = Modifier.width(180.dp).height(50.dp).shadow(8.dp, RoundedCornerShape(8.dp), spotColor = AccentGreen).clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(AccentGreen, AccentGreenGlow))).clickable { /* Launch Game */ }, contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PLAY", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 1.sp)
                }
            }
            Spacer(modifier = Modifier.width(30.dp))
        }
    }
}

@Composable
fun PlaceholderContent(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = TextSecondary, fontSize = 18.sp) }
}
