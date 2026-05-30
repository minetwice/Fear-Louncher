package com.fearlauncher.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GameLauncher {
    init {
        System.loadLibrary("native_launch")
    }

    external fun startMinecraftNative(
        jrePath: String, jarPath: String, version: String, assetsPath: String
    ): Int

    suspend fun launch(context: Context, versionId: String): Boolean = withContext(Dispatchers.IO) {
        val baseDir = MinecraftManager.getBaseDir(context)
        val jreDir = File(baseDir, "jre_extracted")
        
        // Extract JRE from assets if not present
        if (!jreDir.exists()) {
            Log.d("Launcher", "Extracting JRE from assets...")
            copyAssets(context.assets, "jre", jreDir)
        }

        val jarPath = File(MinecraftManager.getVersionsDir(context), "$versionId/$versionId.jar").absolutePath
        val assetsPath = MinecraftManager.getAssetsDir(context).absolutePath

        if (!File(jarPath).exists()) {
            Log.e("Launcher", "❌ Minecraft Jar not found!")
            return@withContext false
        }

        // Call Native JVM Launcher
        val result: Int = startMinecraftNative(
            jreDir.absolutePath, jarPath, versionId, assetsPath
        )

        // FIX: Compare Int with Int (not Long)
        Log.d("Launcher", if (result == 0) "✅ Launch Success" else "❌ Launch Failed: $result")
        return@withContext result == 0
    }

    private fun copyAssets(assetManager: android.content.res.AssetManager, assetPath: String, targetDir: File) {
        val names = assetManager.list(assetPath) ?: return
        targetDir.mkdirs()
        for (name in names) {
            val isFile = assetManager.openFd("$assetPath/$name")?.length ?: -1
            if (isFile == -1L) {  // FIX: Compare Long with Long (-1L)
                copyAssets(assetManager, "$assetPath/$name", File(targetDir, name))
            } else {
                assetManager.open("$assetPath/$name").use { input ->
                    File(targetDir, name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
