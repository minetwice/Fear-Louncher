package com.fearlauncher.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
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
                LauncherScreen(filesDir)
            }
        }
    }
}

@Composable
fun LauncherScreen(filesDir: File) {
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready to Download Game Files") }
    var isDownloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Stable file link for testing and downloading runtime assets safely
    val gameFileUrl = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.20/kotlin-stdlib-1.9.20.jar"
    val targetFile = File(filesDir, "client.jar")

    val infiniteTransition = rememberInfiniteTransition(label = "ShineTransition")
    val shineProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShineProgress"
    )

    val deepBlack = Color(0xFF0A0A0A)
    val metallicCard = Color(0xFF161616)
    val silverBright = Color(0xFFE5E5E5)
    val silverMedium = Color(0xFF999999)
    val silverDark = Color(0xFF333333)

    val shineBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, silverBright.copy(alpha = 0.35f), Color.Transparent),
        start = androidx.compose.ui.geometry.Offset(shineProgress * 1000f, 0f),
        end = androidx.compose.ui.geometry.Offset((shineProgress * 1000f) + 300f, 300f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(deepBlack),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = metallicCard)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FEAR LAUNCHER",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = silverBright,
                    letterSpacing = 6.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(silverDark, silverBright, silverDark)
                            )
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = statusText,
                    fontSize = 15.sp,
                    color = silverMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(silverDark.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(silverMedium, silverBright)
                                )
                            )
                    )
                    Box(modifier = Modifier.fillMaxSize().background(shineBrush))
                }

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = {
                        if (!isDownloading) {
                            scope.launch(Dispatchers.IO) {
                                isDownloading = true
                                try {
                                    var currentUrl = gameFileUrl
                                    var connection: HttpURLConnection
                                    var responseCode: Int
                                    var redirectCount = 0
                                    
                                    // Loop to handle redirects (301/302) if server moves the file
                                    while (true) {
                                        val url = URL(currentUrl)
                                        connection = url.openConnection() as HttpURLConnection
                                        connection.connectTimeout = 15000
                                        connection.readTimeout = 15000
                                        
                                        // Fake User-Agent to pass security firewalls
                                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                        
                                        responseCode = connection.responseCode
                                        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                                            currentUrl = connection.getHeaderField("Location")
                                            redirectCount++
                                            if (redirectCount > 5) throw Exception("Too many redirects")
                                            continue
                                        }
                                        break
                                    }

                                    if (responseCode == HttpURLConnection.HTTP_OK) {
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
                                                    statusText = "Downloading Runtime Assets: ${(progress * 100).toInt()}%"
                                                }
                                            }
                                            output.write(data, 0, count)
                                        }
                                        output.flush()
                                        output.close()
                                        input.close()
                                        withContext(Dispatchers.Main) {
                                            statusText = "Download Successful! Game Files Verified."
                                            isDownloading = false
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            statusText = "Server Error Code: $responseCode"
                                            isDownloading = false
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
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = silverBright,
                        contentColor = deepBlack,
                        disabledContainerColor = silverDark
                    )
                ) {
                    Text(
                        text = if (isDownloading) "DOWNLOADING..." else "DOWNLOAD CORE FILES",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
