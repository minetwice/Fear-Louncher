package com.fearlauncher.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

data class DownloadProgress(
    val currentFile: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percentage: Float
)

object LibsDownloader {
    private const val TAG = "LibsDownloader"
    
    // ✅ EXACT DIRECT LINK (Jo maine pehle diya tha)
    private const val JRE_DIRECT_URL = "https://github.com/MojoLauncher/android-openjdk-build-multiarch/releases/download/rolling/jre8-pojav.zip"
    private const val GL4ES_URL = "https://github.com/ptitSeb/gl4es/releases/download/v1.1.5/libGL_arm64.so"
    
    private const val JRE_MIN_SIZE = 50 * 1024 * 1024L  // 50 MB
    private const val GL4ES_MIN_SIZE = 1 * 1024 * 1024L  // 1 MB

    var onProgress: ((DownloadProgress) -> Unit)? = null

    suspend fun ensureAllLibsDownloaded(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val baseDir = File(context.filesDir, "game_runtime")
        
        try {
            // Check if already installed
            val jreBin = File(baseDir, "jre/bin/java")
            val libjvm = File(baseDir, "jre/lib/server/libjvm.so")
            
            if (jreBin.exists() && libjvm.exists() && jreBin.length() > 0) {
                Log.d(TAG, "✅ JRE already installed")
                return@withContext Result.success("Already installed")
            }

            baseDir.mkdirs()
            File(baseDir, "jre").mkdirs()
            File(baseDir, "natives").mkdirs()

            // 1. Download JRE from DIRECT LINK            onProgress?.invoke(DownloadProgress("JRE", 0, 100, 0f))
            Log.d(TAG, "📥 Downloading JRE from: $JRE_DIRECT_URL")
            
            val jreZipFile = File(context.cacheDir, "jre8-pojav.zip")
            
            val jreDownloaded = downloadFileSimple(
                JRE_DIRECT_URL, 
                jreZipFile, 
                "JRE"
            ) { downloaded, total ->
                val progress = (downloaded.toFloat() / total.toFloat()) * 50f
                onProgress?.invoke(DownloadProgress("JRE", downloaded, total, progress))
            }
            
            if (!jreDownloaded || jreZipFile.length() < JRE_MIN_SIZE) {
                throw Exception("JRE download failed or incomplete. Size: ${jreZipFile.length()} bytes")
            }
            
            Log.d(TAG, "📦 Extracting JRE (${jreZipFile.length()} bytes)...")
            extractZipSimple(jreZipFile, File(baseDir, "jre"))
            jreZipFile.delete()
            
            // Set executable
            File(baseDir, "jre/bin/java").setExecutable(true)
            File(baseDir, "jre/lib/server/libjvm.so").setExecutable(true)
            
            if (!File(baseDir, "jre/bin/java").exists()) {
                throw Exception("JRE extraction failed - bin/java not found")
            }
            
            onProgress?.invoke(DownloadProgress("JRE", 100, 100, 50f))
            Log.d(TAG, "✅ JRE installed successfully")

            // 2. Download GL4ES (optional but recommended)
            onProgress?.invoke(DownloadProgress("GL4ES", 0, 100, 50f))
            Log.d(TAG, "📥 Downloading GL4ES...")
            
            val libGLFile = File(baseDir, "natives/libGL.so")
            val gl4esDownloaded = downloadFileSimple(
                GL4ES_URL, 
                libGLFile, 
                "GL4ES"
            ) { downloaded, total ->
                val progress = 50f + (downloaded.toFloat() / total.toFloat()) * 50f
                onProgress?.invoke(DownloadProgress("GL4ES", downloaded, total, progress))
            }
            
            if (gl4esDownloaded && libGLFile.length() >= GL4ES_MIN_SIZE) {
                libGLFile.setExecutable(true)
                Log.d(TAG, "✅ GL4ES installed")            } else {
                Log.w(TAG, "⚠️ GL4ES skipped (optional)")
            }
            
            onProgress?.invoke(DownloadProgress("GL4ES", 100, 100, 100f))

            Log.d(TAG, "✅ All libs ready at: ${baseDir.absolutePath}")
            Result.success("Installation complete!")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Installation failed: ${e.message}", e)
            cleanupPartial(baseDir)
            Result.failure(e)
        }
    }

    // Simple download function (no complex retry logic)
    private suspend fun downloadFileSimple(
        url: String,
        destFile: File,
        label: String,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 60000
            connection.readTimeout = 120000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) FearLauncher")
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}")
            }
            
            val totalBytes = connection.contentLengthLong
            val input: InputStream = connection.inputStream
            val output = FileOutputStream(destFile)
            
            val buffer = ByteArray(16384)  // Larger buffer for faster download
            var downloaded: Long = 0
            var bytesRead: Int
            
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                if (totalBytes > 0) {
                    onProgress(downloaded, totalBytes)
                }
            }
                        output.close()
            input.close()
            connection.disconnect()
            
            Log.d(TAG, "✅ $label downloaded: ${destFile.length()} bytes")
            destFile.exists() && destFile.length() > 0
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ $label download error: ${e.message}")
            destFile.delete()
            false
        }
    }

    // Simple extraction (no complex error handling)
    private fun extractZipSimple(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun cleanupPartial(baseDir: File) {
        try {
            File(baseDir, "jre").deleteRecursively()
            File(baseDir, "natives").deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }

    fun getJREPath(context: Context): String = 
        File(context.filesDir, "game_runtime/jre").absolutePath

    fun getNativesPath(context: Context): String = 
        File(context.filesDir, "game_runtime/natives").absolutePath

    fun isLibsReady(context: Context): Boolean {
        val jreBin = File(context.filesDir, "game_runtime/jre/bin/java")        val libjvm = File(context.filesDir, "game_runtime/jre/lib/server/libjvm.so")
        return jreBin.exists() && libjvm.exists() && jreBin.length() > 0
    }
}
