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
import javax.net.ssl.HttpsURLConnection

data class DownloadProgress(
    val currentFile: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percentage: Float
)

object LibsDownloader {
    private const val TAG = "LibsDownloader"
    
    // ✅ Multiple fallback URLs for JRE (try in order)
    private val JRE_URLS = listOf(
        "https://github.com/MojoLauncher/android-openjdk-build-multiarch/releases/download/rolling/jre8-pojav.zip",
        "https://github.com/PojavLauncher/PojavLauncher/releases/download/v3.3.1.2/jre_arm64_v8a.zip",
        "https://api.github.com/repos/MojoLauncher/android-openjdk-build-multiarch/releases/latest"
    )
    
    private const val GL4ES_URL = "https://github.com/ptitSeb/gl4es/releases/download/v1.1.5/libGL_arm64.so"
    
    // Expected sizes for validation (approximate)
    private const val JRE_MIN_SIZE = 50 * 1024 * 1024L  // 50 MB minimum
    private const val GL4ES_MIN_SIZE = 1 * 1024 * 1024L  // 1 MB minimum

    var onProgress: ((DownloadProgress) -> Unit)? = null

    suspend fun ensureAllLibsDownloaded(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val baseDir = File(context.filesDir, "game_runtime")
        
        try {
            // Check if already downloaded and valid
            val jreBin = File(baseDir, "jre/bin/java")
            val libjvm = File(baseDir, "jre/lib/server/libjvm.so")
            val libGL = File(baseDir, "natives/libGL.so")
            
            if (jreBin.exists() && libjvm.exists() && libGL.exists() && 
                jreBin.length() > 0 && libjvm.length() > 0) {                Log.d(TAG, "✅ All libs already present and valid")
                return@withContext Result.success("Already installed")
            }

            baseDir.mkdirs()
            File(baseDir, "jre").mkdirs()
            File(baseDir, "natives").mkdirs()

            // 1. Download and Extract JRE (with retry)
            onProgress?.invoke(DownloadProgress("JRE", 0, 100, 0f))
            Log.d(TAG, "📥 Starting JRE download...")
            
            var jreSuccess = false
            var lastError: Exception? = null
            
            for ((index, url) in JRE_URLS.withIndex()) {
                try {
                    Log.d(TAG, "🔗 Trying JRE URL #$${index + 1}: $url")
                    
                    val jreZipFile = File(context.cacheDir, "jre8-pojav.zip")
                    val downloaded = downloadFileWithRetry(url, jreZipFile, "JRE") { downloaded, total ->
                        val progress = (downloaded.toFloat() / total.toFloat()) * 50f
                        onProgress?.invoke(DownloadProgress("JRE", downloaded, total, progress))
                    }
                    
                    if (downloaded && jreZipFile.length() >= JRE_MIN_SIZE) {
                        Log.d(TAG, "📦 Extracting JRE (${jreZipFile.length()} bytes)...")
                        extractZip(jreZipFile, File(baseDir, "jre"))
                        jreZipFile.delete()
                        
                        // Set executable permissions
                        File(baseDir, "jre/bin/java").setExecutable(true)
                        File(baseDir, "jre/lib/server/libjvm.so").setExecutable(true)
                        
                        if (File(baseDir, "jre/bin/java").exists() && 
                            File(baseDir, "jre/lib/server/libjvm.so").exists()) {
                            jreSuccess = true
                            Log.d(TAG, "✅ JRE extracted successfully")
                            onProgress?.invoke(DownloadProgress("JRE", 100, 100, 50f))
                            break
                        }
                    } else {
                        Log.w(TAG, "⚠️ JRE download incomplete or too small: ${jreZipFile.length()} bytes")
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.e(TAG, "❌ JRE download attempt #$${index + 1} failed: ${e.message}", e)
                }
            }
                        if (!jreSuccess) {
                throw Exception("Failed to download JRE after ${JRE_URLS.size} attempts. Last error: ${lastError?.message}")
            }

            // 2. Download GL4ES (libGL.so)
            onProgress?.invoke(DownloadProgress("GL4ES", 0, 100, 50f))
            Log.d(TAG, "📥 Downloading GL4ES...")
            
            val libGLFile = File(baseDir, "natives/libGL.so")
            val gl4esDownloaded = downloadFileWithRetry(GL4ES_URL, libGLFile, "GL4ES") { downloaded, total ->
                val progress = 50f + (downloaded.toFloat() / total.toFloat()) * 50f
                onProgress?.invoke(DownloadProgress("GL4ES", downloaded, total, progress))
            }
            
            if (!gl4esDownloaded || libGLFile.length() < GL4ES_MIN_SIZE) {
                Log.w(TAG, "⚠️ GL4ES download incomplete, but continuing...")
                // Don't fail completely - game might still work without GL4ES on some devices
            } else {
                libGLFile.setExecutable(true)
                Log.d(TAG, "✅ GL4ES downloaded")
            }
            
            onProgress?.invoke(DownloadProgress("GL4ES", 100, 100, 100f))

            Log.d(TAG, "✅ All libs downloaded and ready at: ${baseDir.absolutePath}")
            Result.success("Installation complete!")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download failed: ${e.message}", e)
            // Cleanup partial downloads on failure
            cleanupPartialDownloads(baseDir)
            Result.failure(e)
        }
    }

    private suspend fun downloadFileWithRetry(
        url: String,
        destFile: File,
        label: String,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        val maxRetries = 3
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "$label download attempt #$attempt")
                
                val connection = URL(url).openConnection() as HttpsURLConnection
                connection.connectTimeout = 30000  // 30 seconds                connection.readTimeout = 60000      // 60 seconds
                connection.setRequestProperty("User-Agent", "FearLauncher/1.0")
                connection.connect()
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP $responseCode from $url")
                }
                
                val totalBytes = connection.contentLengthLong
                if (totalBytes <= 0) {
                    Log.w(TAG, "⚠️ Could not determine content length for $label")
                }
                
                val input: InputStream = connection.inputStream
                val output = FileOutputStream(destFile)
                
                val buffer = ByteArray(8192)
                var downloadedBytes: Long = 0
                var bytesRead: Int
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
                
                output.close()
                input.close()
                connection.disconnect()
                
                // Verify download
                if (destFile.exists() && destFile.length() > 0) {
                    Log.d(TAG, "✅ $label downloaded: ${destFile.length()} bytes")
                    return true
                } else {
                    throw Exception("$label file invalid after download")
                }
                
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "⚠️ $label download attempt #$attempt failed: ${e.message}")
                destFile.delete()  // Cleanup failed download
                
                if (attempt < maxRetries) {
                    // Wait before retry (exponential backoff)
                    kotlinx.coroutines.delay(1000L * attempt)
                }            }
        }
        
        Log.e(TAG, "❌ $label download failed after $maxRetries attempts")
        return false
    }

    private fun extractZip(zipFile: File, destDir: File) {
        Log.d(TAG, "📦 Extracting ${zipFile.name} to ${destDir.absolutePath}")
        
        if (!zipFile.exists() || zipFile.length() == 0L) {
            throw Exception("Zip file missing or empty: ${zipFile.absolutePath}")
        }
        
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
        
        Log.d(TAG, "✅ Extraction complete")
    }

    private fun cleanupPartialDownloads(baseDir: File) {
        try {
            Log.d(TAG, "🧹 Cleaning up partial downloads...")
            File(baseDir, "jre").deleteRecursively()
            File(baseDir, "natives").deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Cleanup failed: ${e.message}")
        }
    }

    // Get paths for GameLauncher
    fun getJREPath(context: Context): String {
        return File(context.filesDir, "game_runtime/jre").absolutePath
    }

    fun getNativesPath(context: Context): String {        return File(context.filesDir, "game_runtime/natives").absolutePath
    }

    fun isLibsReady(context: Context): Boolean {
        val jreBin = File(context.filesDir, "game_runtime/jre/bin/java")
        val libjvm = File(context.filesDir, "game_runtime/jre/lib/server/libjvm.so")
        val libGL = File(context.filesDir, "game_runtime/natives/libGL.so")
        
        return jreBin.exists() && libjvm.exists() && libjvm.length() > 0
    }
}
