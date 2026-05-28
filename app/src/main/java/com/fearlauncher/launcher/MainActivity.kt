package com.fearlauncher.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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

// ----------------- PREMIUM COLOR PALETTE -----------------
val DeepBlack = Color(0xFF0A0A0A)
val MetallicBlack = Color(0xFF141414)
val DarkGrayCard = Color(0xFF1C1C1C)
val SilverBright = Color(0xFFF5F5F7)
val SilverMedium = Color(0xFF9E9E9E)
val SilverDark = Color(0xFF3A3A3C)
val NeonGreenPlay = Color(0xFF2E7D32)

@Composable
fun MainAppNavigator(filesDir: File) {
    // Is state se screen automatic badlegi jab download complete hoga
    var isDownloadComplete by remember { mutableStateOf(false) }

    // Silver Shine Animation Loop (Jo continuously flow karegi)
    val infiniteTransition = rememberInfiniteTransition(label = "GlobalShineEffect")
    val shineProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShineProgress"
    )

    val silverShineBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, SilverBright.copy(alpha = 0.3f), Color.Transparent),
        start = androidx.compose.ui.geometry.Offset(shineProgress * 1500f, 0f),
        end = androidx.compose.ui.geometry.Offset((shineProgress * 1500f) + 350f, 350f)
    )

    // Smooth Screen Crossfade Animation
    Crossfade(targetState = isDownloadComplete, animationSpec = tween(700), label = "ScreenSwitch") { screenState ->
        if (!screenState) {
            DownloaderScreen(filesDir, silverShineBrush) {
                isDownloadComplete = true
            }
        } else {
            DashboardScreen(silverShineBrush)
        }
    }
}

// ----------------- SCREEN 1: THE REAL DOWNLOADER -----------------
@Composable
fun DownloaderScreen(filesDir: File, shineBrush: Brush, onDownloadSuccess: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready to Download Core Files") }
    var isDownloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Stable URL with User-Agent to bypass server blocks
    val gameFileUrl = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.20/kotlin-stdlib-1.9.20.jar"
    val targetFile = File(filesDir, "client.jar")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MetallicBlack),
            border = BoxDefaults.thinSilverBorder()
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FEAR LAUNCHER",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SilverBright,
                    letterSpacing = 6.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Shine Line Design Element below title
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(2.dp)
                        .background(Brush.horizontalGradient(listOf(SilverDark, SilverBright, SilverDark)))
                )

                Spacer(modifier = Modifier.height(40.dp))
                Text(text = statusText, fontSize = 14.sp, color = SilverMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))

                // Massive Download Bar with Silver Shine Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SilverDark.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(SilverMedium, SilverBright)))
                    )
                    // The Moving Metallic Shine Line Line
                    Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                }

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = {
                        if (!isDownloading) {
                            scope.launch(Dispatchers.IO) {
                                isDownloading = true
                                try {
                                    val url = URL(gameFileUrl)
                                    val connection = url.openConnection() as HttpURLConnection
                                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                                    connection.connect()

                                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                        val fileLength = connection.contentLength
                                        val input = connection.inputStream
                                        val output = targetFile.outputStream()
                                        val data = ByteArray(8192)
                                        var total: Long = 0
                                        var count: Int

                                        while (input.read(data).also { count = it } != -1) {
                                            total += count
                                            if (fileLength > 0) {
                                                progress = total.toFloat() / fileLength
                                                withContext(Dispatchers.Main) {
                                                    statusText = "Downloading Game Assets: ${(progress * 100).toInt()}%"
                                                }
                                            }
                                            output.write(data, 0, count)
                                        }
                                        output.flush()
                                        output.close()
                                        input.close()

                                        withContext(Dispatchers.Main) {
                                            statusText = "Download Successful! Extracting Environment..."
                                            delay(1500) // Aesthetic delay for seamless cinema transition
                                            onDownloadSuccess() // Automatically shifts to dashboard
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        statusText = "Network Error: ${e.localizedMessage}"
                                        isDownloading = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SilverBright,
                        contentColor = DeepBlack,
                        disabledContainerColor = SilverDark
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isDownloading) "DOWNLOADING CORE..." else "DOWNLOAD CORE FILES",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ----------------- SCREEN 2: REAL MINECRAFT DASHBOARD -----------------
@Composable
fun DashboardScreen(shineBrush: Brush) {
    var selectedLeftTab by remember { mutableStateOf("MINECRAFT: JAVA EDITION") }
    var selectedTopTab by remember { mutableStateOf("Play") }

    Row(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        
        // 1. LEFT SIDEBAR PANEL
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(230.dp)
                .background(MetallicBlack)
                .border(end = 1.dp, color = SilverDark.copy(alpha = 0.2f))
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "FEAR LAUNCHER",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = SilverBright,
                modifier = Modifier.padding(horizontal = 20.dp),
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            val leftTabs = listOf("HOME", "MINECRAFT: JAVA EDITION", "MINECRAFT: BEDROCK EDITION", "MINECRAFT DUNGEONS", "SETTINGS")
            leftTabs.forEach { tab ->
                DashboardLeftRow(
                    text = tab,
                    isSelected = selectedLeftTab == tab,
                    onClick = { selectedLeftTab = tab }
                )
            }
        }

        // 2. MAIN WORKSPACE CONTENT AREA
        Column(modifier = Modifier.fillMaxSize()) {
            
            // TOP TABS NAVIGATION BAR (Play, Installations, Skins etc.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(MetallicBlack)
                    .border(bottom = 1.dp, color = SilverDark.copy(alpha = 0.2f)),
                verticalAlignment = Alignment.CenterVertizontally
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                val topTabs = listOf("Play", "Installations", "Realms", "Skins", "Patch Notes")
                topTabs.forEach { tab ->
                    val isTabSelected = selectedTopTab == tab
                    Text(
                        text = tab,
                        fontSize = 14.sp,
                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTabSelected) SilverBright else SilverMedium,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedTopTab = tab }
                    )
                }
            }

            // CENTER CINEMATIC ARTWORK BANNER CARD
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(20.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkGrayCard)
                        .border(1.dp, color = SilverDark.copy(alpha = 0.4f))
                ) {
                    // Continuous Moving Silver Metallic Shine Overlay Over the main banner zone
                    Box(modifier = Modifier.fillMaxSize().background(shineBrush))

                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "MINECRAFT",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SilverBright,
                            letterSpacing = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedLeftTab,
                            fontSize = 13.sp,
                            color = SilverMedium,
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. BOTTOM PANEL: ABSOLUTE CINEMA GIANT PLAY BUTTON
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(MetallicBlack)
                    .border(top = 1.dp, color = SilverDark.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                var isPlayPressed by remember { mutableStateOf(false) }
                val playButtonScale by animateFloatAsState(if (isPlayPressed) 0.94f else 1f, label = "PlayScaleAnim")

                Box(
                    modifier = Modifier
                        .scale(playButtonScale)
                        .width(220.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF43A047), NeonGreenPlay)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isPlayPressed = true
                            // Game launching logic connects here in future updates!
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PLAY",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 3.sp
                    )
                    // Beautiful moving silver line gleam inside the green button
                    Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                }
            }
        }
    }
}

// ----------------- COMPOSABLE UTILITIES & CLICK ANIMATIONS -----------------
@Composable
fun DashboardLeftRow(text: String, isSelected: Boolean, onClick: () -> Unit) {
    var isRowPressed by remember { mutableStateOf(false) }
    val rowScale by animateFloatAsState(if (isRowPressed) 0.96f else 1f, label = "LeftRowScaleAnim")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .scale(rowScale)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) SilverDark.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) SilverBright else SilverMedium,
            maxLines = 1
        )
    }
}

object BoxDefaults {
    fun thinSilverBorder() = Modifier.border(1.dp, SilverDark.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
}
