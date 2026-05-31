package com.fearlauncher.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission Required for Game Files", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request permission for Android 9 and below
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
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
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("Home") }
    var installedVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Installation state
    var isInstalling by remember { mutableStateOf(false) }
    var installationProgress by remember { mutableStateOf(0f) }
    var installationStatus by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()

    // Check and install libs on first launch
    LaunchedEffect(Unit) {
        if (!LibsDownloader.isLibsReady(context)) {
            isInstalling = true
            LibsDownloader.onProgress = { progress ->
                installationProgress = progress.percentage
                installationStatus = "Downloading ${progress.currentFile}... ${(progress.percentage).toInt()}%"
            }
            
            val result = LibsDownloader.ensureAllLibsDownloaded(context)
            isInstalling = false
            
            result.onSuccess {
                installationStatus = "✅ Installation complete!"
                Toast.makeText(context, "Libraries installed successfully", Toast.LENGTH_SHORT).show()
            }
            result.onFailure {
                installationStatus = "❌ Installation failed: ${it.message}"
                Toast.makeText(context, "Failed to install libraries", Toast.LENGTH_LONG).show()
            }
        }    }

    // Refresh installed versions when switching to Home tab
    LaunchedEffect(activeTab) {
        if (activeTab == "Home") {
            installedVersions = MinecraftManager.getInstalledInstances(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(activeTab) { activeTab = it }
            Column(modifier = Modifier.weight(1f)) {
                TopBar()
                Box(modifier = Modifier.weight(1f).padding(24.dp)) {
                    when (activeTab) {
                        "Home" -> HomeScreen(installedVersions = installedVersions, context = context)
                        "Java Edition" -> LibraryScreen(context = context)
                        else -> PlaceholderContent("Coming Soon")
                    }
                }
            }
        }
        
        // Installation overlay
        if (isInstalling) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = AccentGreen
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Installing Game Libraries...",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = installationStatus,                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { installationProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentGreen,
                        trackColor = BorderGray
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(installedVersions: List<String>, context: android.content.Context) {
    var selectedVersion by remember { mutableStateOf<String?>(installedVersions.firstOrNull()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("My Instances", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(16.dp))

        if (installedVersions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Instances Found", color = TextSecondary)
                    Text("Go to Java Edition to download a version", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(installedVersions) { versionId ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedVersion = versionId },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selectedVersion == versionId) SurfaceBlack else CardGray),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedVersion == versionId) AccentGreen else BorderGray)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Computer, contentDescription = null, tint = if (selectedVersion == versionId) AccentGreen else TextSecondary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(versionId, color = TextPrimary, fontWeight = FontWeight.Medium)                                Text("Ready to Launch", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        selectedVersion?.let { id ->
            PlayBar(versionId = id, context = context)
        }
    }
}

@Composable
fun PlayBar(versionId: String, context: android.content.Context) {
    val scope = rememberCoroutineScope()
    val isInstalled = MinecraftManager.isVersionInstalled(context, versionId)
    var isLaunching by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(SurfaceBlack)
            .border(androidx.compose.foundation.BorderStroke(1.dp, BorderGray)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 30.dp)
            ) {
                Text("Selected Instance", color = TextSecondary, fontSize = 12.sp)
                Text(versionId, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = {
                    if (isInstalled && !isLaunching) {
                        isLaunching = true
                        scope.launch {
                            val result = GameLauncher.launch(context, versionId)
                            isLaunching = false
                            
                            result.onSuccess {                                Toast.makeText(context, "Game Launched!", Toast.LENGTH_SHORT).show()
                            }
                            result.onFailure {
                                Toast.makeText(context, "Launch Failed: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = isInstalled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInstalled) AccentGreen else CardGray,
                    disabledContainerColor = CardGray
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(150.dp).height(45.dp)
            ) {
                Text(if (isLaunching) "Launching..." else "PLAY", color = Color.White, fontWeight = FontWeight.ExtraBold)
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
    }}

@Composable
fun PlaceholderContent(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
        Text(text, color = TextSecondary) 
    }
}
