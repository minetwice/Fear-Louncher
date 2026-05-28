package com.fearlauncher.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Color(0xFF0A0A0A)) {
                    FearLauncherApp(filesDir)
                }
            }
        }
    }
}

// ----------------- MODELS -----------------
data class GameVersion(val name: String, var isInstalled: Boolean = false)

@Composable
fun FearLauncherApp(filesDir: File) {
    var currentScreen by remember { mutableStateOf("DASHBOARD") }
    val versions = remember { mutableStateListOf(
        GameVersion("1.20.4"), GameVersion("1.19.2"), GameVersion("1.8.9")
    )}
    var selectedVersion by remember { mutableStateOf<GameVersion?>(versions[0]) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(targetState = currentScreen, label = "Nav") { screen ->
            when (screen) {
                "DASHBOARD" -> DashboardScreen(
                    selectedVersion, 
                    onOpenVersions = { currentScreen = "VERSIONS" },
                    onLaunch = { /* Launch Logic using selectedVersion */ }
                )
                "VERSIONS" -> VersionScreen(
                    versions, 
                    onBack = { currentScreen = "DASHBOARD" },
                    onInstall = { version -> 
                        val dir = File(filesDir, "instances/${version.name}")
                        dir.mkdirs()
                        version.isInstalled = true
                        selectedVersion = version
                    }
                )
            }
        }
    }
}

// ----------------- DASHBOARD -----------------
@Composable
fun DashboardScreen(version: GameVersion?, onOpenVersions: () -> Unit, onLaunch: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("FEAR LAUNCHER", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(modifier = Modifier.height(40.dp))
        
        // Version Selection Box
        Card(modifier = Modifier.fillMaxWidth().clickable { onOpenVersions() }, colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("SELECTED VERSION", color = Color.Gray, fontSize = 12.sp)
                Text(version?.name ?: "No Version", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Play Button
        Button(
            onClick = onLaunch,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("LAUNCH GAME", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ----------------- VERSION SCREEN -----------------
@Composable
fun VersionScreen(versions: List<GameVersion>, onBack: () -> Unit, onInstall: (GameVersion) -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("SELECT VERSION", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(versions) { version ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1C1C1C)).padding(20.dp).clickable { onInstall(version) },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(version.name, color = Color.White, fontSize = 16.sp)
                    Text(if (version.isInstalled) "INSTALLED" else "DOWNLOAD", color = if (version.isInstalled) Color.Green else Color.Cyan)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        TextButton(onClick = onBack) { Text("Back to Dashboard", color = Color.Gray) }
    }
}
