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
import java.security.MessageDigest

data class DownloadProgress(
    val currentFile: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percentage: Float
)

object LibsDownloader {
    private const val TAG = "LibsDownloader"
    
    // ✅ Download URLs (MojoLauncher/PojavLauncher se)
    private const val JRE_URL = "https://github.com/MojoLauncher/android-openjdk-build-multiarch/releases/download/rolling/jre8-pojav.zip"
    private const val GL4ES_URL = "https://github.com/ptitSeb/gl4es/releases/download/v1.1.5/libGL_arm64.so"
    
    // Expected SHA256 hashes for security (update these with actual hashes)
    private const val JRE_HASH = "" // Fill this after downloading
    private const val GL4ES_HASH = "" // Fill this after downloading

    // Progress callback
    var onProgress: ((DownloadProgress) -> Unit)? = null

    suspend fun ensureAllLibsDownloaded(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val baseDir = File(context.filesDir, "game_runtime")
        
        try {
            // Check if already downloaded
            val jreBin = File(baseDir, "jre/bin/java")
            val libjvm = File(baseDir, "jre/lib/server/libjvm.so")
            val libGL = File(baseDir, "natives/libGL.so")
            
            if (jreBin.exists() && libjvm.exists() && libGL.exists()) {
                Log.d(TAG, "✅ All libs already present")
                return@withContext Result.success("Already installed")
            }

            baseDir.mkdirs()
            File(baseDir, "jre").mkdirs()
            File(baseDir, "natives").mkdirs()

            // 1. Download and Extract JRE
            onProgress?.invoke(DownloadProgress("JRE", 0, 100, 0f))
            Log.d(TAG, "📥 Downloading JRE...")
            
            val jreZipFile = File(context.cacheDir, "jre8-pojav.zip")
            downloadFileWithProgress(JRE_URL, jreZipFile) { downloaded, total ->
                val progress = (downloaded.toFloat() / total.toFloat()) * 50f // JRE is 50% of total
                onProgress?.invoke(DownloadProgress("JRE", downloaded, total, progress))
            }
            
            Log.d(TAG, "📦 Extracting JRE...")
            extractZip(jreZipFile, File(baseDir, "jre"))
            jreZipFile.delete()
            
            // Set executable permissions
            File(baseDir, "jre/bin/java").setExecutable(true)
            File(baseDir, "jre/lib/server/libjvm.so").setExecutable(true)
            
            onProgress?.invoke(DownloadProgress("JRE", 100, 100, 50f))

            // 2. Download GL4ES (libGL.so)
            onProgress?.invoke(DownloadProgress("GL4ES", 0, 100, 50f))
            Log.d(TAG, "📥 Downloading GL4ES...")
            
            val libGLFile = File(baseDir, "natives/libGL.so")
            downloadFileWithProgress(GL4ES_URL, libGLFile) { downloaded, total ->
                val progress = 50f + (downloaded.toFloat() / total.toFloat()) * 50f
                onProgress?.invoke(DownloadProgress("GL4ES", downloaded, total, progress))
            }
            
            libGLFile.setExecutable(true)
            onProgress?.invoke(DownloadProgress("GL4ES", 100, 100, 100f))

            Log.d(TAG, "✅ All libs downloaded and ready at: ${baseDir.absolutePath}")
            Result.success("Installation complete!")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun downloadFileWithProgress(
        url: String,
        destFile: File,
        onProgress: (Long, Long) -> Unit
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connect()
        
        val totalBytes = connection.contentLengthLong
        val input: InputStream = connection.inputStream
        val output = FileOutputStream(destFile)
        
        val buffer = ByteArray(8192)
        var downloadedBytes: Long = 0
        var bytesRead: Int
        
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead
            onProgress(downloadedBytes, totalBytes)
        }
        
        output.close()
        input.close()
        connection.disconnect()
    }

    private fun downloadFile(url: String, destFile: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connect()
        
        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        
        connection.disconnect()
    }

    private fun extractZip(zipFile: File, destDir: File) {
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

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        if (expectedHash.isEmpty()) return true // Skip if hash not set
        
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    // Get paths for GameLauncher
    fun getJREPath(context: Context): String {
        return File(context.filesDir, "game_runtime/jre").absolutePath
    }

    fun getNativesPath(context: Context): String {
        return File(context.filesDir, "game_runtime/natives").absolutePath
    }

    fun isLibsReady(context: Context): Boolean {
        val jreBin = File(context.filesDir, "game_runtime/jre/bin/java")
        val libjvm = File(context.filesDir, "game_runtime/jre/lib/server/libjvm.so")
        val libGL = File(context.filesDir, "game_runtime/natives/libGL.so")
        
        return jreBin.exists() && libjvm.exists() && libGL.exists()
    }
}
