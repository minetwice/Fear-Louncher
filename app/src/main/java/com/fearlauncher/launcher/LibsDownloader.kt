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
    
    // Direct download links
    private const val JRE_URL = "https://github.com/MojoLauncher/android-openjdk-build-multiarch/releases/download/rolling/jre8-pojav.zip"
    private const val GL4ES_URL = "https://github.com/ptitSeb/gl4es/releases/download/v1.1.5/libGL_arm64.so"
    
    private const val JRE_MIN_SIZE = 50 * 1024 * 1024L
    private const val GL4ES_MIN_SIZE = 1 * 1024 * 1024L

    var onProgress: ((DownloadProgress) -> Unit)? = null

    suspend fun ensureAllLibsDownloaded(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val baseDir = File(context.filesDir, "game_runtime")
        
        try {
            // Check if already installed
            val jreBin = File(baseDir, "jre/bin/java")
            val libjvm = File(baseDir, "jre/lib/server/libjvm.so")
            
            if (jreBin.exists() && libjvm.exists() && jreBin.length() > 0) {
                Log.d(TAG, "JRE already installed")
                return@withContext Result.success("Already installed")
            }

            // Create directories
            baseDir.mkdirs()
            File(baseDir, "jre").mkdirs()
            File(baseDir, "natives").mkdirs()
            // Download JRE
            Log.d(TAG, "Downloading JRE")
            
            val jreZipFile = File(context.cacheDir, "jre8-pojav.zip")
            
            val jreSuccess = downloadFile(JRE_URL, jreZipFile)
            
            if (!jreSuccess || jreZipFile.length() < JRE_MIN_SIZE) {
                throw Exception("JRE download failed")
            }
            
            Log.d(TAG, "Extracting JRE")
            extractZip(jreZipFile, File(baseDir, "jre"))
            jreZipFile.delete()
            
            // Set executable permissions
            File(baseDir, "jre/bin/java").setExecutable(true)
            File(baseDir, "jre/lib/server/libjvm.so").setExecutable(true)
            
            if (!File(baseDir, "jre/bin/java").exists()) {
                throw Exception("JRE extraction failed")
            }
            
            Log.d(TAG, "JRE installed")

            // Download GL4ES (optional)
            val libGLFile = File(baseDir, "natives/libGL.so")
            val gl4esSuccess = downloadFile(GL4ES_URL, libGLFile)
            
            if (gl4esSuccess && libGLFile.length() >= GL4ES_MIN_SIZE) {
                libGLFile.setExecutable(true)
                Log.d(TAG, "GL4ES installed")
            }
            
            Log.d(TAG, "Installation complete")
            Result.success("Done")
            
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: " + e.message)
            cleanupPartial(File(context.filesDir, "game_runtime"))
            Result.failure(e)
        }
    }

    // Simple download function - no lambdas, no complex logic
    private fun downloadFile(url: String, destFile: File): Boolean {
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null
                return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 60000
            connection.readTimeout = 120000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return false
            }
            
            input = connection.inputStream
            output = FileOutputStream(destFile)
            
            val buffer = ByteArray(16384)
            var bytesRead: Int
            
            while (true) {
                bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
            }
            
            output.close()
            input.close()
            connection.disconnect()
            
            destFile.exists() && destFile.length() > 0
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error: " + e.message)
            if (destFile.exists()) destFile.delete()
            false
        } finally {
            try { input?.close() } catch (e: Exception) {}
            try { output?.close() } catch (e: Exception) {}
            try { connection?.disconnect() } catch (e: Exception) {}
        }
    }

    // Simple extraction function
    private fun extractZip(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { inputStream ->                        FileOutputStream(outFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
        }
    }

    // Cleanup function
    private fun cleanupPartial(baseDir: File) {
        try {
            File(baseDir, "jre").deleteRecursively()
            File(baseDir, "natives").deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: " + e.message)
        }
    }

    // Helper functions
    fun getJREPath(context: Context): String {
        return File(context.filesDir, "game_runtime/jre").absolutePath
    }

    fun getNativesPath(context: Context): String {
        return File(context.filesDir, "game_runtime/natives").absolutePath
    }

    fun isLibsReady(context: Context): Boolean {
        val jreBin = File(context.filesDir, "game_runtime/jre/bin/java")
        val libjvm = File(context.filesDir, "game_runtime/jre/lib/server/libjvm.so")
        return jreBin.exists() && libjvm.exists() && jreBin.length() > 0
    }
}
