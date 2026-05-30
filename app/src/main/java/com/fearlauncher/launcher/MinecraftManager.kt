package com.fearlauncher.launcher

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

object MinecraftManager {
    
    // Base Directory: Android/data/com.fearlauncher.launcher/files
    fun getBaseDir(context: Context): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    fun getVersionsDir(context: Context): File = File(getBaseDir(context), "versions")
    fun getLibsDir(context: Context): File = File(getBaseDir(context), "libraries")
    fun getAssetsDir(context: Context): File = File(getBaseDir(context), "assets")
    fun getInstancesDir(context: Context): File = File(getBaseDir(context), "instances")

    // Check if a specific version instance is fully installed
    fun isVersionInstalled(context: Context, versionId: String): Boolean {
        val versionDir = File(getVersionsDir(context), versionId)
        val jarFile = File(versionDir, "$versionId.jar")
        val libsDir = getLibsDir(context)
        
        // Basic check: Jar exists and libraries folder is not empty
        return jarFile.exists() && libsDir.exists() && libsDir.listFiles()?.isNotEmpty() == true
    }

    // Get List of Installed Instances
    fun getInstalledInstances(context: Context): List<String> {
        val versionsDir = getVersionsDir(context)
        if (!versionsDir.exists()) return emptyList()
        
        return versionsDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    // Fetch All Versions from Mojang API
    suspend fun fetchAllVersions(): List<McVersion> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://launchermeta.mojang.com/mc/game/version_manifest.json")
                val json = Json.parseToJsonElement(url.readText()).jsonObject
                val versionsArray = json["versions"]?.jsonArray ?: emptyList()
                
                val mappedList = versionsArray.map {
                    val obj = it.jsonObject
                    McVersion(
                        id = obj["id"]?.jsonPrimitive?.content ?: "Unknown",
                        type = obj["type"]?.jsonPrimitive?.content ?: "release",
                        url = obj["url"]?.jsonPrimitive?.content,
                        releaseTime = obj["releaseTime"]?.jsonPrimitive?.content ?: ""
                    )
                }
                return@withContext mappedList.reversed()
            } catch (e: Exception) {
                Log.e("Manager", "Fetch Error", e)
                emptyList()
            }
        }
    }

    // Install Version (Core + Libs + Assets) - Real Download Logic
    suspend fun installVersion(context: Context, version: McVersion, onProgress: (String, Float) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val versionsDir = getVersionsDir(context)
                val libsDir = getLibsDir(context)
                val assetsDir = getAssetsDir(context)
                
                if (!versionsDir.exists()) versionsDir.mkdirs()
                if (!libsDir.exists()) libsDir.mkdirs()
                if (!assetsDir.exists()) assetsDir.mkdirs()

                val versionDir = File(versionsDir, version.id)
                if (!versionDir.exists()) versionDir.mkdirs()

                onProgress("Fetching Metadata...", 0.05f)
                
                // 1. Get Version Details JSON
                val metaUrl = version.url ?: throw Exception("No URL")
                val jsonStr = URL(metaUrl).readText()
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                
                // 2. Download Client Jar (Game Core)
                val clientObj = json["downloads"]?.jsonObject?.get("client")?.jsonObject
                val jarUrl = clientObj?.get("url")?.jsonPrimitive?.content
                val jarSize = clientObj?.get("size")?.jsonPrimitive?.long ?: 0L
                
                if (jarUrl != null) {
                    val jarFile = File(versionDir, "${version.id}.jar")
                    onProgress("Downloading Core Jar...", 0.1f)
                    downloadFile(jarUrl, jarFile, jarSize) { progress ->
                        onProgress("Downloading Core...", 0.1f + (progress * 0.2f))
                    }
                }

                // 3. Download Libraries (Heavy Part - ~500MB+)
                onProgress("Downloading Libraries...", 0.3f)
                val libraries = json["libraries"]?.jsonArray ?: emptyList()
                var libCount = 0
                val totalLibs = libraries.size
                
                for (lib in libraries) {
                    val libObj = lib.jsonObject
                    val downloadsLib = libObj["downloads"]?.jsonObject
                    val artifact = downloadsLib?.get("artifact")?.jsonObject
                    
                    if (artifact != null) {
                        val libUrl = artifact["url"]?.jsonPrimitive?.content
                        val path = artifact["path"]?.jsonPrimitive?.content
                        val size = artifact["size"]?.jsonPrimitive?.long ?: 0L
                        
                        if (libUrl != null && path != null) {
                            val libFile = File(libsDir, path)
                            if (!libFile.exists()) {
                                libFile.parentFile?.mkdirs()
                                downloadFile(libUrl, libFile, size) {
                                    val libProgress = (libCount.toFloat() / totalLibs.toFloat()) * 0.6f
                                    onProgress("Lib: ${path.substringAfterLast('/')}", 0.3f + libProgress)
                                }
                            }
                        }
                    }
                    libCount++
                }

                // 4. Download Assets Index
                onProgress("Downloading Assets...", 0.9f)
                val assetIndex = json["assetIndex"]?.jsonObject
                val assetUrl = assetIndex?.get("url")?.jsonPrimitive?.content
                val assetId = assetIndex?.get("id")?.jsonPrimitive?.content
                
                if (assetUrl != null && assetId != null) {
                    val assetFile = File(assetsDir, "indexes/$assetId.json")
                    if (!assetFile.exists()) {
                        assetFile.parentFile?.mkdirs()
                        downloadFile(assetUrl, assetFile, 0) {
                            onProgress("Assets...", 0.95f)
                        }
                    }
                }
                
                onProgress("Finalizing...", 0.99f)
                File(versionDir, "installed.lock").createNewFile()
                onProgress("Installed!", 1.0f)
                
            } catch (e: Exception) {
                Log.e("Manager", "Install Error", e)
                onProgress("Error: ${e.message}", -1f)
            }
        }
    }

    // Helper function to download a file with progress
    private suspend fun downloadFile(urlString: String, file: File, totalSize: Long, onProgress: (Float) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()
        val input = connection.inputStream
        val output = FileOutputStream(file)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0L
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            if (totalSize > 0) onProgress(totalBytesRead.toFloat() / totalSize.toFloat())
        }
        output.close()
        input.close()
    }

    // Launch Game Logic
    fun launchGame(context: Context, versionId: String) {
        val versionsDir = getVersionsDir(context)
        val jarFile = File(versionsDir, "$versionId/$versionId.jar")
        
        // Check if game files exist
        if (!jarFile.exists()) {
            Log.e("Launcher", "Game files missing!")
            return
        }

        // Construct Launch Command (Simplified for Android)
        // Note: Real launching requires a JVM. This logs the command for debugging.
        val command = "java -jar ${jarFile.absolutePath} --version $versionId"
        Log.d("Launcher", "Attempting to launch: $command")
        
        // In a real app with bundled JVM, you would use ProcessBuilder here:
        // val process = ProcessBuilder("java", "-jar", jarFile.absolutePath, ...).start()
    }
}

// Data class for Minecraft Version
data class McVersion(
    val id: String,
    val type: String,
    val url: String?,
    val releaseTime: String
)
