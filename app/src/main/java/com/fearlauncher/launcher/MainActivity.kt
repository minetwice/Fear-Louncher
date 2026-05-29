package com.fearlauncher.launcher

import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// --- DATA CLASSES ---
data class McVersion(val id: String, val type: String, val url: String?, val releaseTime: String)
data class DownloadState(val isDownloading: Boolean = false, val currentVersion: String = "", val progress: Float = 0f, val statusMessage: String = "")

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

// --- THEME COLORS ---
val DeepBlack = Color(0xFF050505)
val SurfaceBlack = Color(0xFF121212)
val CardGray = Color(0xFF1E1E1E)
val BorderGray = Color(0xFF333333)
val TextPrimary = Color(0xFFFFFFFF)val TextSecondary = Color(0xFFA0A0A0)
val AccentGreen = Color(0xFF2E7D32)

@Composable
fun MainAppNavigator(filesDir: File) {
    var activeTab by remember { mutableStateOf("Home") }
    var selectedVersionId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // State variables
    var allVersions by remember { mutableStateOf<List<McVersion>>(listOf()) }
    var isLoading by remember { mutableStateOf(false) }
    var downloadState by remember { mutableStateOf(DownloadState()) }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(activeTab) { activeTab = it }
            
            Column(modifier = Modifier.weight(1f)) {
                TopBar()
                
                Box(modifier = Modifier.weight(1f).padding(24.dp)) {
                    when (activeTab) {
                        "Home" -> HomeTabContent(selectedVersionId ?: "Unknown")
                        "Java Edition" -> {
                            if (isLoading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = AccentGreen)
                                }
                            } else if (allVersions.isEmpty()) {
                                // Manual Load Button to avoid LaunchedEffect errors
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Button(onClick = {
                                        scope.launch {
                                            isLoading = true
                                            try {
                                                val fetched = fetchMinecraftVersions()
                                                allVersions = fetched
                                                selectedVersionId = fetched.find { it.type == "release" }?.id
                                            } catch (e: Exception) {
                                                Log.e("Launcher", "Error", e)
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }) {
                                        Text("Load Versions from Mojang")
                                    }
                                }
                            } else {                                VersionManagerTab(
                                    allVersions = allVersions,
                                    selectedVersionId = selectedVersionId,
                                    onVersionSelect = { selectedVersionId = it.id },
                                    onInstallRequest = { version ->
                                        scope.launch {
                                            performDownload(version, filesDir) { state ->
                                                downloadState = state
                                            }
                                        }
                                    },
                                    downloadState = downloadState
                                )
                            }
                        }
                        else -> PlaceholderContent("Coming Soon")
                    }
                }
                
                PlayBar(selectedVersionId ?: "No Version", filesDir)
                
                if (downloadState.isDownloading) {
                    DownloadStatusBar(state = downloadState)
                }
            }
        }
    }
}

// --- LOGIC FUNCTIONS ---

suspend fun performDownload(version: McVersion, filesDir: File, onUpdate: (DownloadState) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            onUpdate(DownloadState(true, version.id, 0f, "Fetching Metadata..."))
            
            val metaUrl = version.url ?: throw Exception("No URL")
            val jsonStr = URL(metaUrl).readText()
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            
            val downloads = json["downloads"]?.jsonObject
            val clientObj = downloads?.get("client")?.jsonObject
            val jarUrl = clientObj?.get("url")?.jsonPrimitive?.content
            val jarSize = clientObj?.get("size")?.jsonPrimitive?.long ?: 0L

            if (jarUrl == null) throw Exception("JAR URL not found")

            val versionDir = File(filesDir, "versions/" + version.id)
            if (!versionDir.exists()) versionDir.mkdirs()
            val outputFile = File(versionDir, version.id + ".jar")
            onUpdate(DownloadState(true, version.id, 0f, "Downloading Core..."))
            
            val connection = URL(jarUrl).openConnection() as HttpURLConnection
            connection.connect()
            val input = connection.inputStream
            val output = FileOutputStream(outputFile)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                val progress = if (jarSize > 0) (totalBytesRead.toFloat() / jarSize.toFloat()) else 0f
                val mbRead = totalBytesRead / (1024 * 1024)
                val totalMb = jarSize / (1024 * 1024)
                
                // Safe string concatenation
                val msg = mbRead.toString() + " MB / " + totalMb.toString() + " MB"
                onUpdate(DownloadState(true, version.id, progress, msg))
            }
            
            output.close()
            input.close()
            onUpdate(DownloadState(false, version.id, 1f, "Installed"))
            delay(2000)
            onUpdate(DownloadState())
        } catch (e: Exception) {
            val errMsg = "Error: " + e.message
            onUpdate(DownloadState(false, version.id, 0f, errMsg))
            delay(3000)
            onUpdate(DownloadState())
        }
    }
}

suspend fun fetchMinecraftVersions(): List<McVersion> {
    return withContext(Dispatchers.IO) {
        val url = URL("https://launchermeta.mojang.com/mc/game/version_manifest.json")
        val json = Json.parseToJsonElement(url.readText()).jsonObject
        val versionsArray = json["versions"]?.jsonArray ?: emptyList()
        
        versionsArray.map {
            val obj = it.jsonObject
            McVersion(
                id = obj["id"]?.jsonPrimitive?.content ?: "Unknown",
                type = obj["type"]?.jsonPrimitive?.content ?: "release",                url = obj["url"]?.jsonPrimitive?.content,
                releaseTime = obj["releaseTime"]?.jsonPrimitive?.content ?: ""
            )
        }.reversed()
    }
}

// --- UI COMPONENTS ---

@Composable
fun VersionManagerTab(
    allVersions: List<McVersion>,
    selectedVersionId: String?,
    onVersionSelect: (McVersion) -> Unit,
    onInstallRequest: (McVersion) -> Unit,
    downloadState: DownloadState
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }

    val filtered = allVersions.filter {
        val matchesSearch = it.id.contains(searchQuery, ignoreCase = true)
        val matchesType = if (filterType == "All") true else it.type.equals(filterType, ignoreCase = true)
        matchesSearch && matchesType
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Library", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search...", color = TextSecondary, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.width(200.dp).height(40.dp),
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
            )        }
        Spacer(Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Release", "Snapshot").forEach { type ->
                FilterChip(
                    selected = filterType == type,
                    onClick = { filterType = type },
                    label = { Text(type, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentGreen, 
                        selectedLabelColor = Color.White,
                        containerColor = CardGray, 
                        labelColor = TextSecondary
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(CardGray)) {
            LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                items(filtered) { version ->
                    VersionItem(
                        version = version,
                        isSelected = selectedVersionId == version.id,
                        isCurrentlyDownloading = downloadState.isDownloading && downloadState.currentVersion == version.id,
                        onSelect = { onVersionSelect(version) },
                        onInstall = { onInstallRequest(version) }
                    )
                }
            }
        }
    }
}

@Composable
fun VersionItem(
    version: McVersion,
    isSelected: Boolean,
    isCurrentlyDownloading: Boolean,
    onSelect: () -> Unit,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) SurfaceBlack else CardGray),
        border = BorderStroke(1.dp, if (isSelected) AccentGreen else BorderGray)
    ) {        Row(
            Modifier.padding(16.dp), 
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(version.id, color = TextPrimary, fontWeight = FontWeight.Medium)
                // Safe string concatenation
                val typeText = version.type.uppercase() + " • " + version.releaseTime.take(10)
                Text(typeText, color = TextSecondary, fontSize = 10.sp)
            }
            if (isCurrentlyDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentGreen, strokeWidth = 2.dp)
            } else if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = AccentGreen)
            } else {
                TextButton(onClick = onInstall) {
                    Text("Install", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DownloadStatusBar(state: DownloadState) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a1a)).padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val installingText = "Installing " + state.currentVersion + "..."
            Text(installingText, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(state.statusMessage, color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = AccentGreen,
            trackColor = BorderGray
        )
    }
}

@Composable
fun Sidebar(activeTab: String, onTabClick: (String) -> Unit) {
    val items = listOf("Home" to Icons.Default.Home, "Java Edition" to Icons.Default.Code, "Settings" to Icons.Default.Settings)
    Column(
        modifier = Modifier.width(220.dp).fillMaxHeight().background(SurfaceBlack).border(BorderStroke(1.dp, BorderGray))
    ) {        Box(Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text("FEAR", modifier = Modifier.padding(start = 24.dp), fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
            Text("LAUNCHER", modifier = Modifier.padding(start = 24.dp).offset(y = 18.dp), fontSize = 10.sp, color = AccentGreen, letterSpacing = 2.sp)
        }
        // Using Divider instead of HorizontalDivider for compatibility
        Divider(color = BorderGray, thickness = 1.dp)
        items.forEach { (label, icon) ->
            val isSelected = activeTab == label
            Row(
                Modifier.fillMaxWidth().height(56.dp).clickable { onTabClick(label) }.padding(horizontal = 24.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = label, tint = if (isSelected) AccentGreen else TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text(label, color = if (isSelected) TextPrimary else TextSecondary, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun TopBar() {
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(SurfaceBlack).padding(horizontal = 24.dp), 
        verticalAlignment = Alignment.CenterVertically, 
        horizontalArrangement = Arrangement.End
    ) {
        Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text("Steve", color = TextSecondary)
    }
}

@Composable
fun HomeTabContent(version: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MINECRAFT JAVA", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            val selectedText = "Selected: " + version
            Text(selectedText, color = AccentGreen)
        }
    }
}

@Composable
fun PlayBar(version: String, filesDir: File) {
    // Check if version is installed
    val isInstalled = remember(version) {
        if (version == "No Version") false        else File(filesDir, "versions/" + version + "/" + version + ".jar").exists()
    }

    Box(
        Modifier.fillMaxWidth().height(80.dp).background(SurfaceBlack).border(BorderStroke(1.dp, BorderGray)), 
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 30.dp)) {
                Text("Ready to Play", color = TextSecondary, fontSize = 12.sp)
                Text(version, color = TextPrimary, fontWeight = FontWeight.Bold)
                if (!isInstalled && version != "No Version") {
                    Text("Not Installed", color = Color.Red, fontSize = 10.sp)
                }
            }
            Button(
                onClick = { 
                    if (isInstalled) {
                        Log.d("Launcher", "Launching " + version)
                        // Add actual launch logic here
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
                Text("PLAY", color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(30.dp))
        }
    }
}

@Composable
fun PlaceholderContent(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
        Text(text, color = TextSecondary) 
    }
}
