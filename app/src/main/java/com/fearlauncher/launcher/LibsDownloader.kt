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
                
                Log.d("Libs", "Downloading JRE")
                
                // Download JRE - INLINE CODE (no function call)
                val jreZip = File(context.cacheDir, "jre.zip")
                var conn: HttpURLConnection? = null
                var input: InputStream? = null
                var output: FileOutputStream? = null
                var jreDownloaded = false
                
                try {
                    conn = URL(JRE_URL).openConnection() as HttpURLConnection
                    conn.connectTimeout = 60000
                    conn.readTimeout = 120000
                    conn.connect()
                    if (conn.responseCode == 200) {
                        input = conn.inputStream
                        output = FileOutputStream(jreZip)                        val buffer = ByteArray(8192)
                        var count: Int
                        while (true) {
                            count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                        }
                        output.close()
                        input.close()
                        conn.disconnect()
                        jreDownloaded = jreZip.exists() && jreZip.length() > 0
                    }
                } catch (e: Exception) {
                    Log.e("Libs", "JRE download error: " + e.message)
                    if (jreZip.exists()) jreZip.delete()
                } finally {
                    try { input?.close() } catch (e: Exception) {}
                    try { output?.close() } catch (e: Exception) {}
                    try { conn?.disconnect() } catch (e: Exception) {}
                }
                
                if (!jreDownloaded) {
                    Log.e("Libs", "JRE download failed")
                    return@withContext false
                }
                
                // Extract JRE - INLINE CODE
                Log.d("Libs", "Extracting JRE")
                try {
                    ZipFile(jreZip).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            val outFile = File(File(baseDir, "jre"), entry.name)
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
                    jreZip.delete()
                    File(baseDir, "jre/bin/java").setExecutable(true)
                } catch (e: Exception) {
                    Log.e("Libs", "Extraction error: " + e.message)
                    return@withContext false
                }                
                // Download GL4ES - INLINE CODE (optional)
                Log.d("Libs", "Downloading GL4ES")
                val libGL = File(baseDir, "natives/libGL.so")
                var gl4esDownloaded = false
                
                try {
                    conn = URL(GL4ES_URL).openConnection() as HttpURLConnection
                    conn.connectTimeout = 60000
                    conn.readTimeout = 120000
                    conn.connect()
                    if (conn.responseCode == 200) {
                        input = conn.inputStream
                        output = FileOutputStream(libGL)
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
                        gl4esDownloaded = libGL.exists() && libGL.length() > 0
                    }
                } catch (e: Exception) {
                    Log.e("Libs", "GL4ES download error: " + e.message)
                } finally {
                    try { input?.close() } catch (e: Exception) {}
                    try { output?.close() } catch (e: Exception) {}
                    try { conn?.disconnect() } catch (e: Exception) {}
                }
                
                if (gl4esDownloaded) {
                    libGL.setExecutable(true)
                    Log.d("Libs", "GL4ES installed")
                }
                
                Log.d("Libs", "Installation complete")
                true
                
            } catch (e: Exception) {
                Log.e("Libs", "Install failed: " + e.message)
                false
            }
        }
    }
    
    fun isLibsReady(context: Context): Boolean {        val path = File(context.filesDir, "game_runtime/jre/bin/java")
        return path.exists() && path.length() > 0
    }
    
    fun getJREPath(context: Context): String {
        return File(context.filesDir, "game_runtime/jre").absolutePath
    }
    
    fun getNativesPath(context: Context): String {
        return File(context.filesDir, "game_runtime/natives").absolutePath
    }
}
