package com.fearlauncher.launcher

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LibsDownloader {
    
    private const val JRE_URL = "https://github.com/MojoLauncher/android-openjdk-build-multiarch/releases/download/rolling/jre8-pojav.zip"
    private const val GL4ES_URL = "https://github.com/ptitSeb/gl4es/releases/download/v1.1.5/libGL_arm64.so"
    
    suspend fun ensureAllLibsDownloaded(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseDir = File(context.filesDir, "game_runtime")
                baseDir.mkdirs()
                File(baseDir, "jre").mkdirs()
                File(baseDir, "natives").mkdirs()
                
                // Check if already installed
                val jreBin = File(baseDir, "jre/bin/java")
                if (jreBin.exists() && jreBin.length() > 0) {
                    Log.d("Libs", "Already installed")
                    return@withContext true
                }
                
                // Download JRE
                val jreZip = File(context.cacheDir, "jre.zip")
                if (!downloadSimple(JRE_URL, jreZip)) {
                    Log.e("Libs", "JRE download failed")
                    return@withContext false
                }
                
                // Extract JRE
                extractSimple(jreZip, File(baseDir, "jre"))
                jreZip.delete()
                
                // Set executable
                File(baseDir, "jre/bin/java").setExecutable(true)
                
                // Download GL4ES (optional)
                val libGL = File(baseDir, "natives/libGL.so")
                downloadSimple(GL4ES_URL, libGL)                if (libGL.exists()) {
                    libGL.setExecutable(true)
                }
                
                Log.d("Libs", "Installation complete")
                true
                
            } catch (e: Exception) {
                Log.e("Libs", "Error: " + e.message)
                false
            }
        }
    }
    
    private fun downloadSimple(url: String, dest: File): Boolean {
        var conn: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 60000
            conn.readTimeout = 120000
            conn.connect()
            if (conn.responseCode != 200) return false
            input = conn.inputStream
            output = FileOutputStream(dest)
            val buffer = ByteArray(8192)
            var count: Int
            while (true) {
                count = input.read(buffer)
                if (count == -1) break
                output.write(buffer, 0, count)
            }
            output.close()
            input.close()
            conn.disconnect()
            dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            Log.e("Libs", "Download error: " + e.message)
            if (dest.exists()) dest.delete()
            false
        } finally {
            try { input?.close() } catch (e: Exception) {}
            try { output?.close() } catch (e: Exception) {}
            try { conn?.disconnect() } catch (e: Exception) {}
        }
    }
    
    private fun extractSimple(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { ins ->
                        FileOutputStream(outFile).use { outs ->
                            ins.copyTo(outs)
                        }
                    }
                }
            }
        }
    }
    
    fun isLibsReady(context: Context): Boolean {
        val path = File(context.filesDir, "game_runtime/jre/bin/java")
        return path.exists() && path.length() > 0
    }
    
    fun getJREPath(context: Context): String {
        return File(context.filesDir, "game_runtime/jre").absolutePath
    }
    
    fun getNativesPath(context: Context): String {
        return File(context.filesDir, "game_runtime/natives").absolutePath
    }
}
