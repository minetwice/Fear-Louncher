package com.fearlauncher.launcher

import androidx.compose.foundation.background
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

@Composable
fun LibraryScreen(filesDir: java.io.File) {
    val scope = rememberCoroutineScope()
    var allVersions by remember { mutableStateOf<List<McVersion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    // State for Downloading
    var downloadingVersionId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        isLoading = true
        allVersions = MinecraftManager.fetchAllVersions()
        isLoading = false
    }

    val filteredVersions = allVersions.filter {
        val matchesSearch = it.id.contains(searchQuery, ignoreCase = true)
        val matchesType = if (filterType == "All") true else it.type.equals(filterType, ignoreCase = true)
        matchesSearch && matchesType
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search versions...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )
        }

        // Tabs
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Release", "Snapshot").forEach { type ->
                FilterChip(
                    selected = filterType == type,
                    onClick = { filterType = type },
                    label = { Text(type) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2E7D32),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E1E1E),
                        labelColor = Color.Gray
                    )
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E)) 
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2E7D32))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                    items(filteredVersions) { version ->                        val isInstalled = MinecraftManager.isInstanceInstalled(filesDir, version.id)
                        val isDownloading = downloadingVersionId == version.id
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isInstalled) Color(0xFF2E7D32) else Color(0xFF333333))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(version.id, color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(version.type.uppercase(), color = Color.Gray, fontSize = 10.sp)
                                }
                                
                                if (isDownloading) {
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
                                        Text(downloadStatusText, color = Color(0xFF2E7D32), fontSize = 10.sp, maxLines = 1)
                                        LinearProgressIndicator(
                                            progress = { downloadProgress }, 
                                            modifier = Modifier.fillMaxWidth().height(4.dp), 
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                } else if (isInstalled) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Installed", tint = Color(0xFF2E7D32))
                                } else {
                                    Button(onClick = {
                                        scope.launch {
                                            downloadingVersionId = version.id
                                            downloadProgress = 0f
                                            downloadStatusText = "Starting..."
                                            
                                            MinecraftManager.installVersion(version, filesDir) { status, progress ->
                                                downloadStatusText = status
                                                if (progress >= 0) downloadProgress = progress
                                            }
                                            
                                            downloadingVersionId = null
                                            downloadProgress = 0f
                                            downloadStatusText = ""
                                        }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) {
                                        Text("Install", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }            }
        }
    }
}
