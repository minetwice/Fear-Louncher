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
                
                val jreBin = File(baseDir, "jre/bin/java")
                if (jreBin.exists() && jreBin.length() > 0) {
                    Log.d("Libs", "Already installed")
                    return@withContext true
                }
                
                Log.d("Libs", "Downloading JRE")
                
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
                    val code = conn.responseCode
                    if (code == 200) {
                        input = conn.inputStream
                        output = FileOutputStream(jreZip)
                        val buffer = ByteArray(8192)                        var count = 0
                        while (true) {
                            count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                        }
                        output.close()
                        input.close()
                        conn.disconnect()
                        val exists = jreZip.exists()
                        val len = jreZip.length()
                        jreDownloaded = exists && len > 0
                    }
                } catch (e: Exception) {
                    val msg = e.message
                    Log.e("Libs", "JRE download error: $msg")
                    if (jreZip.exists()) jreZip.delete()
                } finally {
                    try { if (input != null) input.close() } catch (e: Exception) {}
                    try { if (output != null) output.close() } catch (e: Exception) {}
                    try { if (conn != null) conn.disconnect() } catch (e: Exception) {}
                }
                
                if (!jreDownloaded) {
                    Log.e("Libs", "JRE download failed")
                    return@withContext false
                }
                
                Log.d("Libs", "Extracting JRE")
                try {
                    val zipFile = ZipFile(jreZip)
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val jreDir = File(baseDir, "jre")
                        val outFile = File(jreDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            val parent = outFile.parentFile
                            if (parent != null) parent.mkdirs()
                            val ins = zipFile.getInputStream(entry)
                            val outs = FileOutputStream(outFile)
                            val buf = ByteArray(8192)
                            var n = 0
                            while (true) {
                                n = ins.read(buf)
                                if (n == -1) break
                                outs.write(buf, 0, n)
                            }                            outs.close()
                            ins.close()
                        }
                    }
                    zipFile.close()
                    jreZip.delete()
                    val javaFile = File(baseDir, "jre/bin/java")
                    javaFile.setExecutable(true)
                } catch (e: Exception) {
                    val msg = e.message
                    Log.e("Libs", "Extraction error: $msg")
                    return@withContext false
                }
                
                Log.d("Libs", "Downloading GL4ES")
                val libGL = File(baseDir, "natives/libGL.so")
                var gl4esDownloaded = false
                
                try {
                    conn = URL(GL4ES_URL).openConnection() as HttpURLConnection
                    conn.connectTimeout = 60000
                    conn.readTimeout = 120000
                    conn.connect()
                    val code = conn.responseCode
                    if (code == 200) {
                        input = conn.inputStream
                        output = FileOutputStream(libGL)
                        val buffer = ByteArray(8192)
                        var count = 0
                        while (true) {
                            count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                        }
                        output.close()
                        input.close()
                        conn.disconnect()
                        val exists = libGL.exists()
                        val len = libGL.length()
                        gl4esDownloaded = exists && len > 0
                    }
                } catch (e: Exception) {
                    val msg = e.message
                    Log.e("Libs", "GL4ES download error: $msg")
                } finally {
                    try { if (input != null) input.close() } catch (e: Exception) {}
                    try { if (output != null) output.close() } catch (e: Exception) {}
                    try { if (conn != null) conn.disconnect() } catch (e: Exception) {}
                }
                                if (gl4esDownloaded) {
                    libGL.setExecutable(true)
                    Log.d("Libs", "GL4ES installed")
                }
                
                Log.d("Libs", "Installation complete")
                true
                
            } catch (e: Exception) {
                val msg = e.message
                Log.e("Libs", "Install failed: $msg")
                false
            }
        }
    }
    
    fun isLibsReady(context: Context): Boolean {
        val path = File(context.filesDir, "game_runtime/jre/bin/java")
        val exists = path.exists()
        val len = path.length()
        return exists && len > 0
    }
    
    fun getJREPath(context: Context): String {
        val file = File(context.filesDir, "game_runtime/jre")
        return file.absolutePath
    }
    
    fun getNativesPath(context: Context): String {
        val file = File(context.filesDir, "game_runtime/natives")
        return file.absolutePath
    }
}
