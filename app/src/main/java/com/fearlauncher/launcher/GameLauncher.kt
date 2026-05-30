package com.fearlauncher.launcher

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

object GameLauncher {
    private const val TAG = "GameLauncher"

    fun launchGame(context: Context, versionId: String) {
        val baseDir = MinecraftManager.getBaseDir(context)
        val versionDir = File(MinecraftManager.getVersionsDir(context), versionId)
        val jarFile = File(versionDir, "$versionId.jar")
        val libsDir = MinecraftManager.getLibsDir(context)
        val assetsDir = MinecraftManager.getAssetsDir(context)
        val nativesDir = File(versionDir, "natives")

        if (!jarFile.exists()) {
            Log.e(TAG, "❌ Core Jar missing: ${jarFile.absolutePath}")
            return
        }

        // 1. Build Classpath
        val classpath = buildString {
            append(jarFile.absolutePath)
            libsDir.walkTopDown().filter { it.extension == "jar" }.forEach {
                append(":").append(it.absolutePath)
            }
        }

        // 2. Prepare Natives Path
        if (!nativesDir.exists()) nativesDir.mkdirs()

        // 3. Construct Launch Command
        val command = listOf(
            "java",
            "-Xmx2G",
            "-Xms1G",
            "-Djava.library.path=${nativesDir.absolutePath}",
            "-Dorg.lwjgl.librarypath=${nativesDir.absolutePath}",
            "-Dminecraft.launcher.brand=FearLauncher",
            "-Dminecraft.launcher.version=1.0",
            "-cp", classpath,
            "net.minecraft.client.main.Main",
            "--version", versionId,
            "--gameDir", baseDir.absolutePath,
            "--assetsDir", assetsDir.absolutePath,
            "--assetIndex", versionId,
            "--uuid", "00000000-0000-0000-0000-000000000000",
            "--username", "FearPlayer",
            "--accessToken", "0",
            "--userType", "mojang",
            "--width", "854",
            "--height", "480"
        )

        // 4. Check if Java is available
        val javaAvailable = isJavaAvailable(context)
        if (!javaAvailable) {
            Log.e(TAG, "⚠️ Java not found. Install OpenJDK via Termux or use POJavLauncher JVM.")
            // Fallback instruction can be shown via UI/Toast
            return
        }

        // 5. Execute Process
        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(baseDir)
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            Log.d(TAG, "🚀 Game Process Started (PID: ${process.pid()})")

            // Read output in background
            Thread {
                BufferedReader(InputStreamReader(process.inputStream, Charset.forName("UTF-8"))).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.i(TAG, "[MC] $line")
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Launch Failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun isJavaAvailable(context: Context): Boolean {
        // Check common Android Java paths
        val paths = listOf(
            "/data/data/com.termux/files/usr/bin/java", // Termux
            "/system/bin/java",                         // System (rare)
            "java"                                     // PATH
        )
        return paths.any { path ->
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "$path -version 2>&1"))
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }
    }
}
