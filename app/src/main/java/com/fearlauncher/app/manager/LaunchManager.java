package com.fearlauncher.app.manager;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LaunchManager {
    private static final String TAG = "FearLauncher_LM";
    private final Context ctx;
    private final VersionManager versionManager;
    private Process gameProcess;

    public interface LaunchListener {
        void onLog(String line);
        void onLaunchSuccess();
        void onLaunchError(String message);
        void onExit(int exitCode);
        void onJREProgress(int percent);
    }

    public LaunchManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.versionManager = new VersionManager(context);
    }

    /**
     * Checks for JRE in CacheDir (Primary) and FilesDir (Fallback)
     */
    public String getJavaPath() throws Exception {
        // ✅ CHECK CACHE DIR FIRST (Where Service installs it now)
        File cacheJreBin = new File(ctx.getCacheDir(), "jre-17/bin/java");
        if (cacheJreBin.exists() && cacheJreBin.canExecute()) {
            Log.d(TAG, "✅ JRE found in CacheDir: " + cacheJreBin.getAbsolutePath());
            return cacheJreBin.getAbsolutePath();
        }

        // Check FilesDir as fallback
        File filesJreBin = new File(ctx.getFilesDir(), "jre-17/bin/java");
        if (filesJreBin.exists() && filesJreBin.canExecute()) {
            Log.d(TAG, "✅ JRE found in FilesDir: " + filesJreBin.getAbsolutePath());
            return filesJreBin.getAbsolutePath();
        }

        throw new Exception("JRE_NOT_FOUND");
    }

    public void launchGame(String versionId, String username, String uuid,
                           String accessToken, LaunchListener listener) {        new Thread(() -> {
            try {
                listener.onLog("🚀 Initializing FearLauncher...");

                String javaPath;
                try {
                    javaPath = getJavaPath();
                } catch (Exception e) {
                    listener.onLaunchError("❌ Java Runtime Missing.\nPlease install it from the Home Screen.");
                    return;
                }

                if (!versionManager.isVersionInstalled(versionId)) {
                    listener.onLaunchError("❌ Minecraft version '" + versionId + "' not installed.");
                    return;
                }

                if (!versionManager.isFirstLaunchComplete(versionId)) {
                    listener.onLog("📦 First launch: Downloading natives...");
                    versionManager.downloadFirstLaunchFiles(versionId, new VersionManager.FirstLaunchListener() {
                        @Override public void onProgress(int p, String s, long speed) {
                            listener.onLog("⬇️ " + s + " " + p + "%");
                        }
                        @Override public void onComplete() { 
                            listener.onLog("✅ Natives ready! Starting Game...");
                            startMinecraftProcess(javaPath, versionId, username, uuid, accessToken, listener);
                        }
                        @Override public void onError(String e) { 
                            listener.onLaunchError("❌ Natives download failed: " + e); 
                        }
                    });
                } else {
                    startMinecraftProcess(javaPath, versionId, username, uuid, accessToken, listener);
                }

            } catch (Exception e) {
                Log.e(TAG, "Critical Launch Error", e);
                listener.onLaunchError("❌ Critical Error: " + e.getMessage());
            }
        }).start();
    }

    private void startMinecraftProcess(String javaPath, String versionId, String username, 
                                       String uuid, String accessToken, LaunchListener listener) {
        try {
            File baseDir = versionManager.getBaseDir();
            File versionDir = new File(baseDir, "versions/" + versionId);
            File gameJar = new File(versionDir, versionId + ".jar");
            File nativesDir = new File(baseDir, "natives/" + versionId);
            File assetsDir = new File(baseDir, "assets");            File instanceDir = new File(baseDir, "instances/" + versionId);

            List<String> cmd = new ArrayList<>();
            cmd.add(javaPath);
            cmd.add("-Xmx2G");
            cmd.add("-Xms1G");
            cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
            cmd.add("-Dminecraft.client.jar=" + gameJar.getAbsolutePath());
            cmd.add("-Dminecraft.gameDir=" + instanceDir.getAbsolutePath());
            cmd.add("-cp");
            cmd.add(gameJar.getAbsolutePath());
            cmd.add("net.minecraft.client.main.Main");
            
            cmd.add("--username"); cmd.add(username);
            cmd.add("--version"); cmd.add(versionId);
            cmd.add("--gameDir"); cmd.add(instanceDir.getAbsolutePath());
            cmd.add("--assetsDir"); cmd.add(assetsDir.getAbsolutePath());
            cmd.add("--assetIndex"); cmd.add(versionId);
            cmd.add("--uuid"); cmd.add(uuid != null ? uuid : "0");
            cmd.add("--accessToken"); cmd.add(accessToken != null ? accessToken : "0");
            cmd.add("--userType"); cmd.add("mojang");
            cmd.add("--versionType"); cmd.add("FearLauncher");

            Log.d(TAG, "Starting Process with Java: " + javaPath);
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(instanceDir);
            pb.redirectErrorStream(true);
            gameProcess = pb.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(gameProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (listener != null) listener.onLog(line);
                    }
                } catch (Exception e) { Log.e(TAG, "Output error", e); }
            }).start();

            int exitCode = gameProcess.waitFor();
            if (listener != null) {
                listener.onExit(exitCode);
                if (exitCode == 0) listener.onLaunchSuccess();
                else listener.onLaunchError("Game exited with code: " + exitCode);
            }

        } catch (Exception e) {
            Log.e(TAG, "Process Start Failed", e);
            if (listener != null) listener.onLaunchError("❌ Failed to start game: " + e.getMessage());
        }    }

    public void stopGame() {
        if (gameProcess != null && gameProcess.isAlive()) gameProcess.destroy();
    }

    public boolean isLaunchReady(String versionId) {
        return versionManager.isVersionInstalled(versionId);
    }
}
