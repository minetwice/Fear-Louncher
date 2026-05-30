package com.fearlauncher.launcher

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

data class McVersion(val id: String, val type: String, val url: String?, val releaseTime: String)
data class GameInstance(val versionId: String, val isInstalled: Boolean)

object MinecraftManager {
    
    // FIX: Use Public Directory so you can see files in File Manager
    fun getBaseDir(): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(publicDir, "FearLauncher")
    }

    fun getInstancesDir(): File = File(getBaseDir(), "instances")
    fun getInstanceDir(versionId: String): File = File(getInstancesDir(), versionId)
    fun getNativesDir(versionId: String): File = File(getInstanceDir(versionId), "natives")
    fun getLibsDir(): File = File(getBaseDir(), "libraries")

    fun isInstanceInstalled(versionId: String): Boolean {
        val jarFile = File(getInstanceDir(versionId), "$versionId.jar")
        val nativesDir = getNativesDir(versionId)
        // Check if jar exists and natives folder has files
        return jarFile.exists() && nativesDir.exists() && nativesDir.listFiles()?.isNotEmpty() == true
    }

    fun getInstalledInstances(): List<GameInstance> {
        val instancesDir = getInstancesDir()
        if (!instancesDir.exists()) return emptyList()
        return instancesDir.listFiles()?.filter { it.isDirectory }?.map { dir ->
            GameInstance(dir.name, isInstanceInstalled(dir.name))
        } ?: emptyList()
    }

    suspend fun fetchAllVersions(): List<McVersion> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://launchermeta.mojang.com/mc/game/version_manifest.json")
                val json = Json.parseToJsonElement(url.readText()).jsonObject
                val versionsArray = json["versions"]?.jsonArray ?: emptyList()                versionsArray.map {
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

    suspend fun installVersion(version: McVersion, onProgress: (String, Float) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val instanceDir = getInstanceDir(version.id)
                val nativesDir = getNativesDir(version.id)
                if (!instanceDir.exists()) instanceDir.mkdirs()
                if (!nativesDir.exists()) nativesDir.mkdirs()
                if (!getLibsDir().exists()) getLibsDir().mkdirs()

                onProgress("Fetching Metadata...", 0.1f)
                val metaUrl = version.url ?: throw Exception("No URL")
                val jsonStr = URL(metaUrl).readText()
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                
                // 1. Download Client Jar
                val clientObj = json["downloads"]?.jsonObject?.get("client")?.jsonObject
                val jarUrl = clientObj?.get("url")?.jsonPrimitive?.content
                val jarSize = clientObj?.get("size")?.jsonPrimitive?.long ?: 0L
                if (jarUrl != null) {
                    val jarFile = File(instanceDir, "${version.id}.jar")
                    onProgress("Downloading Core...", 0.2f)
                    downloadFile(jarUrl, jarFile, jarSize) { progress ->
                        onProgress("Downloading Core...", 0.2f + (progress * 0.2f))
                    }
                }

                // 2. Download Libraries & Extract Natives
                onProgress("Processing Libraries...", 0.4f)
                val libraries = json["libraries"]?.jsonArray ?: emptyList()
                var libCount = 0
                for (lib in libraries) {
                    val libObj = lib.jsonObject
                    val downloadsLib = libObj["downloads"]?.jsonObject
                    val artifact = downloadsLib?.get("artifact")?.jsonObject                    
                    if (artifact != null) {
                        val libUrl = artifact["url"]?.jsonPrimitive?.content
                        val path = artifact["path"]?.jsonPrimitive?.content
                        val size = artifact["size"]?.jsonPrimitive?.long ?: 0L
                        
                        if (libUrl != null && path != null) {
                            val libFile = File(getLibsDir(), path)
                            if (!libFile.exists()) {
                                libFile.parentFile?.mkdirs()
                                downloadFile(libUrl, libFile, size) {
                                    val libProgress = (libCount.toFloat() / libraries.size.toFloat()) * 0.5f
                                    onProgress("Libs: ${path.substringAfterLast('/')}", 0.4f + libProgress)
                                }
                            }
                            
                            // Extract Natives
                            val classifiers = downloadsLib["classifiers"]?.jsonObject
                            if (classifiers != null) {
                                // Android uses Linux natives
                                val nativeObj = classifiers["natives-linux"]?.jsonObject 
                                    ?: classifiers["natives-windows"]?.jsonObject 
                                
                                if (nativeObj != null) {
                                    val nativeUrl = nativeObj["url"]?.jsonPrimitive?.content
                                    val nativePath = nativeObj["path"]?.jsonPrimitive?.content
                                    if (nativeUrl != null && nativePath != null) {
                                        val nativeFile = File(getLibsDir(), nativePath)
                                        if (!nativeFile.exists()) {
                                            downloadFile(nativeUrl, nativeFile, 0) {
                                                onProgress("Extracting Natives...", 0.9f)
                                            }
                                        }
                                        extractNatives(nativeFile, nativesDir)
                                    }
                                }
                            }
                        }
                    }
                    libCount++
                }
                
                onProgress("Finalizing...", 0.95f)
                File(instanceDir, "installed.lock").createNewFile()
                onProgress("Installed!", 1.0f)
            } catch (e: Exception) {
                Log.e("Manager", "Install Error", e)
                onProgress("Error: ${e.message}", -1f)
            }
        }    }

    private fun extractNatives(zipFile: File, outputDir: File) {
        try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory && entry.name.endsWith(".so")) {
                        val outFile = File(outputDir, entry.name.substringAfterLast('/'))
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        outFile.setExecutable(true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Manager", "Extraction Error", e)
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
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            if (totalSize > 0) onProgress(totalBytesRead.toFloat() / totalSize.toFloat())
        }
        output.close()
        input.close()
    }

    // Launch Logic
    fun launchGame(versionId: String) {
        val instanceDir = getInstanceDir(versionId)
        val nativesDir = getNativesDir(versionId)
        val jarFile = File(instanceDir, "$versionId.jar")
        
        if (!jarFile.exists()) {
            Log.e("Launcher", "Jar file not found!")
            return
        }
        // Construct Classpath
        val libsDir = getLibsDir()
        val classpath = StringBuilder()
        classpath.append(jarFile.absolutePath)
        
        // Add all libraries to classpath (Simplified)
        libsDir.walkTopDown().filter { it.extension == "jar" }.forEach {
            classpath.append(":").append(it.absolutePath)
        }

        // Command to run Java (This requires Termux or a JVM installed on Android)
        // Standard Android doesn't have 'java' command in PATH. 
        // We try to use app's internal java or termux if available.
        val command = "java -Djava.library.path=${nativesDir.absolutePath} -cp $classpath net.minecraft.client.main.Main --version $versionId --accessToken demo --userType demo"
        
        Log.d("Launcher", "Running: $command")
        
        try {
            // Try to execute via Shell (May fail on non-rooted/non-termux devices)
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            Log.d("Launcher", "Process Started: ${process.pid}")
        } catch (e: Exception) {
            Log.e("Launcher", "Launch Failed: ${e.message}")
            e.printStackTrace()
        }
    }
}
