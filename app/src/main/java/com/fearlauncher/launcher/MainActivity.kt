package com.fearlauncher.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainAppNavigator(filesDir)
            }
        }
    }
}

// --- THEME PALETTE (Refined) ---
val DeepBlack = Color(0xFF050505)
val SurfaceBlack = Color(0xFF121212)
val CardGray = Color(0xFF1E1E1E)
val BorderGray = Color(0xFF333333)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0A0A0)
val AccentGreen = Color(0xFF2E7D32)
val AccentGreenGlow = Color(0xFF4CAF50)val DangerRed = Color(0xFFCF6679)

@Composable
fun MainAppNavigator(filesDir: File) {
    var isDownloadComplete by remember { mutableStateOf(false) }
    
    // Global Shine Animation
    val infiniteTransition = rememberInfiniteTransition(label = "GlobalShine")
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -200f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart)
    )

    val shineBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent),
        start = androidx.compose.ui.geometry.Offset(shineOffset, 0f),
        end = androidx.compose.ui.geometry.Offset(shineOffset + 200f, 0f)
    )

    Crossfade(targetState = isDownloadComplete, animationSpec = tween(800), label = "ScreenTransition") { screen ->
        if (!screen) DownloaderScreen(filesDir, shineBrush) { isDownloadComplete = true }
        else DashboardScreen(shineBrush)
    }
}

// --- DOWNLOADER SCREEN (Polished) ---
@Composable
fun DownloaderScreen(filesDir: File, shineBrush: Brush, onComplete: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready to Initialize") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(DeepBlack), 
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(0.75f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceBlack),
            border = BorderStroke(1.dp, BorderGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(40.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("FEAR LAUNCHER", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary, letterSpacing = 1.sp)                Text("Core Setup Required", fontSize = 14.sp, color = TextSecondary)
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Progress Bar
                Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardGray)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(AccentGreen, AccentGreenGlow)))
                    )
                    // Shine effect on progress bar
                    Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("${(progress * 100).toInt()}%", color = TextPrimary, fontWeight = FontWeight.Medium)
                
                Spacer(modifier = Modifier.height(30.dp))
                
                Button(
                    onClick = { 
                        scope.launch(Dispatchers.IO) {
                            statusText = "Downloading Assets..."
                            for(i in 1..100) { 
                                delay(20); progress = i/100f 
                            }
                            withContext(Dispatchers.Main) { 
                                statusText = "Complete"
                                delay(500)
                                onComplete() 
                            }
                        }
                    }, 
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = DeepBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("INITIALIZE SYSTEM", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- DASHBOARD SCREEN (Major Upgrade) ---
@Composable
fun DashboardScreen(shineBrush: Brush) {
    var activeTab by remember { mutableStateOf("Home") }    var selectedVersion by remember { mutableStateOf("Release 1.20.4") }

    Row(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        
        // --- SIDEBAR ---
        Sidebar(activeTab) { activeTab = it }

        // --- MAIN CONTENT AREA ---
        Column(modifier = Modifier.weight(1f)) {
            
            // TOP HEADER
            Row(
                modifier = Modifier.fillMaxWidth().height(70.dp).background(SurfaceBlack).padding(horizontal = 24.dp), 
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LIBRARY", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextSecondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Steve", color = TextSecondary, fontSize = 14.sp)
                }
            }

            // DYNAMIC CONTENT BASED ON TAB
            Box(modifier = Modifier.weight(1f).padding(24.dp)) {
                when (activeTab) {
                    "Home" -> HomeTabContent(selectedVersion, shineBrush)
                    "Java Edition" -> JavaEditionTabContent(selectedVersion) { selectedVersion = it }
                    "Skins" -> PlaceholderContent("Skin Manager Coming Soon")
                    "Settings" -> PlaceholderContent("System Settings")
                }
            }

            // BOTTOM PLAY BAR
            PlayBar(selectedVersion)
        }
    }
}

@Composable
fun Sidebar(activeTab: String, onTabClick: (String) -> Unit) {
    val items = listOf("Home" to Icons.Default.Home, "Java Edition" to Icons.Default.Code, "Skins" to Icons.Default.Face, "Settings" to Icons.Default.Settings)
    
    Column(
        modifier = Modifier.width(240.dp).fillMaxHeight().background(SurfaceBlack).border(BorderStroke(1.dp, BorderGray)),
        horizontalAlignment = Alignment.Start
    ) {
        // Logo Area
        Box(modifier = Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {            Text("FEAR", modifier = Modifier.padding(start = 24.dp), fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text("LAUNCHER", modifier = Modifier.padding(start = 24.dp).offset(y = 18.dp), fontSize = 10.sp, fontWeight = FontWeight.Light, color = AccentGreen, letterSpacing = 2.sp)
        }

        Divider(color = BorderGray, thickness = 1.dp)

        // Nav Items
        items.forEach { (label, icon) ->
            val isSelected = activeTab == label
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .clickable { onTabClick(label) }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = icon, 
                    contentDescription = label, 
                    tint = if (isSelected) AccentGreen else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = label, 
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 15.sp
                )
                
                // Active Indicator
                if (isSelected) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 0.dp), contentAlignment = Alignment.CenterEnd) {
                        Box(modifier = Modifier.width(4.dp).height(24.dp).background(AccentGreen, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTabContent(version: String, shineBrush: Brush) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Hero Card
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardGray),
            border = BorderStroke(1.dp, BorderGray)        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                Column(modifier = Modifier.padding(24.dp).align(Alignment.BottomStart)) {
                    Text("MINECRAFT JAVA", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("Ready to Launch", color = AccentGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Recent News", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(12.dp))
        
        // News Item Dummy
        Card(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceBlack),
            border = BorderStroke(1.dp, BorderGray)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).background(CardGray, RoundedCornerShape(8.dp)))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Update 1.20.4 Released", color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("New features and bug fixes...", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun JavaEditionTabContent(selectedVersion: String, onVersionChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Installation Manager", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(24.dp))
        
        // Version Selector
        Text("Select Version", color = TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        listOf("Release 1.20.4", "Release 1.20.1", "Snapshot 24w14a").forEach { ver ->
            val isSelected = selectedVersion == ver
            Row(
                modifier = Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CardGray else Color.Transparent)
                    .border(if(isSelected) BorderStroke(1.dp, AccentGreen) else BorderStroke(1.dp, BorderGray), RoundedCornerShape(8.dp))                    .clickable { onVersionChange(ver) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(ver, color = if(isSelected) TextPrimary else TextSecondary)
                if(isSelected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Java Arguments", color = TextSecondary, fontSize = 14.sp)
        Card(
            modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceBlack),
            border = BorderStroke(1.dp, BorderGray)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                Text("-Xmx4G -XX:+UseG1GC", color = TextSecondary, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun PlaceholderContent(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, fontSize = 18.sp)
    }
}

@Composable
fun PlayBar(version: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(90.dp).background(SurfaceBlack).border(BorderStroke(1.dp, BorderGray)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Version Info
            Column(modifier = Modifier.weight(1f).padding(start = 30.dp)) {
                Text("Current Selection", color = TextSecondary, fontSize = 12.sp)
                Text(version, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
            
            // PLAY BUTTON
            Box(
                modifier = Modifier
                    .width(180.dp)                    .height(50.dp)
                    .shadow(8.dp, RoundedCornerShape(8.dp), spotColor = AccentGreen)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.horizontalGradient(listOf(AccentGreen, AccentGreenGlow)))
                    .clickable { /* Launch Logic */ },
                contentAlignment = Alignment.Center
            ) {
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
