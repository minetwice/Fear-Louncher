package com.fearlauncher.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainAppNavigator(filesDir)
            }
        }
    }
}

// THEME PALETTE
val DeepBlack = Color(0xFF0A0A0A)
val MetallicBlack = Color(0xFF141414)
val DarkGrayCard = Color(0xFF1C1C1C)
val SilverBright = Color(0xFFF5F5F7)
val SilverMedium = Color(0xFF9E9E9E)
val SilverDark = Color(0xFF3A3A3C)
val NeonGreenPlay = Color(0xFF2E7D32)

@Composable
fun MainAppNavigator(filesDir: File) {
    var isDownloadComplete by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "GlobalShine")
    val shineProgress by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart)
    )

    val silverShineBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, SilverBright.copy(alpha = 0.3f), Color.Transparent),
        start = androidx.compose.ui.geometry.Offset(shineProgress * 1500f, 0f),
        end = androidx.compose.ui.geometry.Offset((shineProgress * 1500f) + 350f, 350f)
    )

    Crossfade(targetState = isDownloadComplete, animationSpec = tween(700), label = "Transition") { screen ->
        if (!screen) DownloaderScreen(filesDir, silverShineBrush) { isDownloadComplete = true }
        else DashboardScreen(silverShineBrush)
    }
}

@Composable
fun DownloaderScreen(filesDir: File, shineBrush: Brush, onComplete: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("Initializing Core...") }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MetallicBlack),
            border = BorderStroke(1.dp, SilverDark)
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FEAR LAUNCHER", fontSize = 24.sp, fontWeight = FontWeight.Black, color = SilverBright)
                Spacer(modifier = Modifier.height(30.dp))
                
                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(SilverDark)) {
                    Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(SilverBright))
                    Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { 
                    scope.launch(Dispatchers.IO) {
                        for(i in 1..100) { delay(30); progress = i/100f }
                        withContext(Dispatchers.Main) { onComplete() }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = SilverBright, contentColor = DeepBlack)) {
                    Text("START DOWNLOAD")
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(shineBrush: Brush) {
    var activeTab by remember { mutableStateOf("Play") }
    
    Row(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        // SIDEBAR
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(MetallicBlack).border(BorderStroke(0.5.dp, SilverDark))) {
            Text("FEAR HUB", modifier = Modifier.padding(20.dp), color = SilverBright, fontWeight = FontWeight.Bold)
            listOf("HOME", "JAVA EDITION", "SETTINGS").forEach { 
                Text(it, modifier = Modifier.padding(20.dp).clickable { }, color = SilverMedium)
            }
        }
        
        // MAIN
        Column(modifier = Modifier.weight(1f)) {
            // TOP BAR
            Row(modifier = Modifier.fillMaxWidth().height(60.dp).background(MetallicBlack), verticalAlignment = Alignment.CenterVertically) {
                listOf("Play", "Install", "Skins").forEach { tab ->
                    Text(tab, modifier = Modifier.padding(16.dp).clickable { activeTab = tab }, color = if(activeTab==tab) SilverBright else SilverMedium)
                }
            }
            // CENTER
            Box(modifier = Modifier.weight(1f).padding(20.dp).clip(RoundedCornerShape(12.dp)).background(DarkGrayCard).border(BorderStroke(1.dp, SilverDark))) {
                Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                Text("MINECRAFT", modifier = Modifier.align(Alignment.Center), fontSize = 40.sp, color = SilverBright, fontWeight = FontWeight.ExtraBold)
            }
            // PLAY BUTTON
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(MetallicBlack), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(200.dp).height(45.dp).clip(RoundedCornerShape(4.dp)).background(NeonGreenPlay), contentAlignment = Alignment.Center) {
                    Text("PLAY", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
