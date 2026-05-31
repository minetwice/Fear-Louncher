package com.fearlauncher.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GameLauncher {
    init {
        // Load native library for JVM launch
        try {
            System.loadLibrary("native_launch")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("GameLauncher", "Failed to load native library: ${e.message}")
        }
    }

    external fun startMinecraftNative(
        jrePath: String,
        jarPath: String,
        version: String,
        assetsPath: String,
        nativesPath: String
    ): Int

    suspend fun launch(context: Context, versionId: String): Result<String> = withContext(Dispatchers.IO) {
        // Check if libs are ready
        if (!LibsDownloader.isLibsReady(context)) {
            return@withContext Result.failure(Exception("Libraries not installed. Please wait for installation to complete."))
        }

        val jrePath = LibsDownloader.getJREPath(context)
        val nativesPath = LibsDownloader.getNativesPath(context)
        
        val jarPath = File(
            MinecraftManager.getVersionsDir(context),
            "$versionId/$versionId.jar"
        ).absolutePath
        
        val assetsPath = MinecraftManager.getAssetsDir(context).absolutePath

        // Check if Minecraft jar exists
        if (!File(jarPath).exists()) {
            return@withContext Result.failure(Exception("Minecraft $versionId not found. Please install it first."))
        }

        try {
            val result = startMinecraftNative(
                jrePath,
                jarPath,
                versionId,
                assetsPath,
                nativesPath
            )

            if (result == 0) {
                Result.success("Game launched successfully")
            } else {
                Result.failure(Exception("Game launch failed with code: $result"))
            }
        } catch (e: Exception) {
            Log.e("GameLauncher", "Launch failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
