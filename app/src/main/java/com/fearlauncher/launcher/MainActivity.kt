package com.fearlauncher.launcher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainAppNavigator()
            }
        }
    }
}

val DeepBlack = Color(0xFF050505)
val SurfaceBlack = Color(0xFF121212)
val CardGray = Color(0xFF1E1E1E)
val BorderGray = Color(0xFF333333)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0A0A0)
val AccentGreen = Color(0xFF2E7D32)

@Composable
fun MainAppNavigator() {
    var activeTab by remember { mutableStateOf("Home") }
    var installedInstances by remember { mutableStateOf<List<GameInstance>>(emptyList()) }
    
    LaunchedEffect(activeTab) {
        if (activeTab == "Home") {
            // FIX: Removed filesDir argument            installedInstances = MinecraftManager.getInstalledInstances()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(activeTab) { activeTab = it }
            Column(modifier = Modifier.weight(1f)) {
                TopBar()
                Box(modifier = Modifier.weight(1f).padding(24.dp)) {
                    when (activeTab) {
                        "Home" -> HomeScreen(
                            instances = installedInstances, 
                            onRefresh = { installedInstances = MinecraftManager.getInstalledInstances() }
                        )
                        "Java Edition" -> LibraryScreen()
                        else -> PlaceholderContent("Coming Soon")
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(instances: List<GameInstance>, onRefresh: () -> Unit) {
    var selectedInstanceId by remember { mutableStateOf<String?>(instances.firstOrNull()?.versionId) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text("My Instances", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(16.dp))

        if (instances.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Instances Found", color = TextSecondary)
                    Text("Go to Java Edition to install one", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(instances) { instance ->
                    InstanceCard(
                        instance = instance,
                        isSelected = selectedInstanceId == instance.versionId,
                        onSelect = { selectedInstanceId = instance.versionId }
                    )                }
            }
        }

        selectedInstanceId?.let { id ->
            PlayBar(instanceId = id, onPlayed = {
                Toast.makeText(context, "Launching $id... Check Logcat", Toast.LENGTH_LONG).show()
            })
        }
    }
}

@Composable
fun InstanceCard(instance: GameInstance, isSelected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) SurfaceBlack else CardGray),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AccentGreen else BorderGray)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Computer, contentDescription = null, tint = if (isSelected) AccentGreen else TextSecondary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(instance.versionId, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("Ready to Launch", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PlayBar(instanceId: String, onPlayed: () -> Unit) {
    // FIX: Removed filesDir argument
    val isInstalled = MinecraftManager.isInstanceInstalled(instanceId)

    Box(Modifier.fillMaxWidth().height(80.dp).background(SurfaceBlack).border(androidx.compose.foundation.BorderStroke(1.dp, BorderGray)), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 30.dp)) {
                Text("Selected Instance", color = TextSecondary, fontSize = 12.sp)
                Text(instanceId, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { 
                    if (isInstalled) {
                        MinecraftManager.launchGame(instanceId)
                        onPlayed()
                    }
                },
                enabled = isInstalled,                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInstalled) AccentGreen else CardGray,
                    disabledContainerColor = CardGray
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(150.dp).height(45.dp)
            ) {
                Text("PLAY", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(30.dp))
        }
    }
}

@Composable
fun Sidebar(activeTab: String, onTabClick: (String) -> Unit) {
    val items = listOf("Home" to Icons.Default.Home, "Java Edition" to Icons.Default.Code, "Settings" to Icons.Default.Settings)
    Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(SurfaceBlack).border(androidx.compose.foundation.BorderStroke(1.dp, BorderGray))) {
        Box(Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text("FEAR", modifier = Modifier.padding(start = 24.dp), fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text("LAUNCHER", modifier = Modifier.padding(start = 24.dp).offset(y = 18.dp), fontSize = 10.sp, color = AccentGreen, letterSpacing = 2.sp)
        }
        Divider(color = BorderGray, thickness = 1.dp)
        items.forEach { (label, icon) ->
            val isSelected = activeTab == label
            Row(Modifier.fillMaxWidth().height(56.dp).clickable { onTabClick(label) }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = label, tint = if (isSelected) AccentGreen else TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text(label, color = if (isSelected) TextPrimary else TextSecondary, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun TopBar() {
    Row(Modifier.fillMaxWidth().height(60.dp).background(SurfaceBlack).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text("Steve", color = TextSecondary)
    }
}

@Composable
fun PlaceholderContent(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
        Text(text, color = TextSecondary) 
    }
}
