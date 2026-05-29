package com.fearlauncher.launcher

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class McVersion(val id: String, val type: String, val url: String?, val releaseTime: String)
data class GameInstance(val versionId: String, val isInstalled: Boolean)

object MinecraftManager {
    
    // Get Directory: Android/data/com.fearlauncher.launcher/files
    fun getLauncherDir(filesDir: File): File {
        return filesDir
    }

    fun getInstancesDir(filesDir: File): File {
        return File(getLauncherDir(filesDir), "instances")
    }

    fun getInstanceDir(filesDir: File, versionId: String): File {
        return File(getInstancesDir(filesDir), versionId)
    }

    // Check if an instance is installed by looking for the JAR file
    fun isInstanceInstalled(filesDir: File, versionId: String): Boolean {
        val jarFile = File(getInstanceDir(filesDir, versionId), "$versionId.jar")
        return jarFile.exists()
    }

    // Get List of ONLY Installed Instances
    fun getInstalledInstances(filesDir: File): List<GameInstance> {
        val instancesDir = getInstancesDir(filesDir)
        if (!instancesDir.exists()) return emptyList()

        return instancesDir.listFiles()?.filter { it.isDirectory }?.map { dir ->
            GameInstance(dir.name, isInstanceInstalled(filesDir, dir.name))
        } ?: emptyList()
    }

    suspend fun fetchAllVersions(): List<McVersion> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://launchermeta.mojang.com/mc/game/version_manifest.json")
                val json = Json.parseToJsonElement(url.readText()).jsonObject                val versionsArray = json["versions"]?.jsonArray ?: emptyList()
                
                versionsArray.map {
                    val obj = it.jsonObject
                    McVersion(
                        id = obj["id"]?.jsonPrimitive?.content ?: "Unknown",
                        type = obj["type"]?.jsonPrimitive?.content ?: "release",
                        url = obj["url"]?.jsonPrimitive?.content,
                        releaseTime = obj["releaseTime"]?.jsonPrimitive?.content ?: ""
                    )
                }.reversed()
            } catch (e: Exception) {
                Log.e("Manager", "Fetch Error", e)
                emptyList()
            }
        }
    }

    suspend fun installVersion(version: McVersion, filesDir: File, onProgress: (String, Float) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val instanceDir = getInstanceDir(filesDir, version.id)
                if (!instanceDir.exists()) instanceDir.mkdirs()

                onProgress("Fetching Metadata...", 0.1f)
                
                // 1. Get Version JSON
                val metaUrl = version.url ?: throw Exception("No URL")
                val jsonStr = URL(metaUrl).readText()
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                
                // 2. Download Client Jar
                val clientObj = json["downloads"]?.jsonObject?.get("client")?.jsonObject
                val jarUrl = clientObj?.get("url")?.jsonPrimitive?.content
                val jarSize = clientObj?.get("size")?.jsonPrimitive?.long ?: 0L

                if (jarUrl != null) {
                    val jarFile = File(instanceDir, "${version.id}.jar")
                    onProgress("Downloading Core...", 0.2f)
                    downloadFile(jarUrl, jarFile, jarSize) { progress ->
                        onProgress("Downloading Core...", 0.2f + (progress * 0.3f))
                    }
                }

                // 3. Download Libraries (Simplified for stability)
                onProgress("Downloading Libraries...", 0.5f)
                val libsDir = File(getLauncherDir(filesDir), "libraries")
                if (!libsDir.exists()) libsDir.mkdirs()

                val libraries = json["libraries"]?.jsonArray ?: emptyList()                var libCount = 0
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
                                    // Update progress based on lib count
                                    val libProgress = (libCount.toFloat() / libraries.size.toFloat()) * 0.4f
                                    onProgress("Libs: ${path.substringAfterLast('/')}", 0.5f + libProgress)
                                }
                            }
                        }
                    }
                    libCount++
                }

                onProgress("Finalizing...", 0.95f)
                // Create a marker file to confirm installation
                File(instanceDir, "installed.lock").createNewFile()
                
                onProgress("Installed!", 1.0f)
            } catch (e: Exception) {
                Log.e("Manager", "Install Error", e)
                onProgress("Error: ${e.message}", -1f)
            }
        }
    }

    private suspend fun downloadFile(urlString: String, file: File, totalSize: Long, onProgress: (Float) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()
        val input = connection.inputStream
        val output = FileOutputStream(file)
        
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            if (totalSize > 0) {
                onProgress(totalBytesRead.toFloat() / totalSize.toFloat())
            }
        }
        output.close()
        input.close()
    }
}
