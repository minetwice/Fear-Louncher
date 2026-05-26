package com.fearlauncher.app.manager;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public String getJavaPath() throws Exception {
        File internalJreBin = new File(ctx.getFilesDir(), "jre/bin/java");
        Log.d(TAG, "Checking Java binary at: " + internalJreBin.getAbsolutePath());
        Log.d(TAG, "Java binary exists: " + internalJreBin.exists());
        Log.d(TAG, "Java binary canExecute: " + internalJreBin.canExecute());

        if (internalJreBin.exists() && internalJreBin.canExecute()) {
            Log.d(TAG, "✅ JRE found at: " + internalJreBin.getAbsolutePath());
            return internalJreBin.getAbsolutePath();
        }
        throw new Exception("JRE_NOT_FOUND");
    }

    public void launchGame(String versionId, String username, String uuid, String accessToken, LaunchListener listener) {
        new Thread(() -> {
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
                        @Override
                        public void onProgress(int p, String s, long speed) {
                            listener.onLog("⬇️ " + s + " " + p + "%");
                        }
                        @Override
                        public void onComplete() {
                            listener.onLog("✅ Natives ready! Starting Game...");
                            startMinecraftProcess(javaPath, versionId, username, uuid, accessToken, listener);
                        }
                        @Override
                        public void onError(String e) {
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

    private void startMinecraftProcess(String javaPath, String versionId, String username, String uuid, String accessToken, LaunchListener listener) {
        try {
            File baseDir = versionManager.getBaseDir();
            File versionDir = new File(baseDir, "versions/" + versionId);
            File gameJar = new File(versionDir, versionId + ".jar");
            File nativesDir = new File(baseDir, "natives/" + versionId);
            File assetsDir = new File(baseDir, "assets");
            File instanceDir = new File(baseDir, "instances/" + versionId);

            if (!assetsDir.exists()) {
                assetsDir.mkdirs();
            }

            List<String> cmd = new ArrayList<>();
            cmd.add(javaPath);

            // Set library path explicitly
            cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());

            // Memory settings
            cmd.add("-Xmx2G");
            cmd.add("-Xms1G");

            // Classpath
            cmd.add("-cp");
            cmd.add(gameJar.getAbsolutePath());

            // Main class
            cmd.add("net.minecraft.client.main.Main");

            // Minecraft arguments
            cmd.add("--username");
            cmd.add(username);
            cmd.add("--version");
            cmd.add(versionId);
            cmd.add("--gameDir");
            cmd.add(instanceDir.getAbsolutePath());
            cmd.add("--assetsDir");
            cmd.add(assetsDir.getAbsolutePath());
            cmd.add("--assetIndex");
            cmd.add(versionId);
            cmd.add("--uuid");
            cmd.add(uuid != null ? uuid : "0");
            cmd.add("--accessToken");
            cmd.add(accessToken != null ? accessToken : "0");
            cmd.add("--userType");
            cmd.add("mojang");
            cmd.add("--versionType");
            cmd.add("FearLauncher");

            Log.d(TAG, "Starting Process with Java: " + javaPath);
            Log.d(TAG, "Command: " + String.join(" ", cmd));
            Log.d(TAG, "Working directory: " + instanceDir.getAbsolutePath());

            // Set environment variables
            Map<String, String> env = new HashMap<>();
            env.put("LD_LIBRARY_PATH", nativesDir.getAbsolutePath());
            env.put("PATH", new File(ctx.getFilesDir(), "jre/bin").getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(instanceDir);
            pb.redirectErrorStream(true);

            // Set environment
            Map<String, String> processEnv = pb.environment();
            processEnv.putAll(env);

            gameProcess = pb.start();

            // Log process info
            Log.d(TAG, "Process started with PID: " + gameProcess.pid());

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(gameProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.d("MinecraftLog", line);
                        if (listener != null) {
                            listener.onLog(line);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Output error", e);
                }
            }).start();

            int exitCode = gameProcess.waitFor();
            if (listener != null) {
                listener.onExit(exitCode);
                if (exitCode == 0) {
                    listener.onLaunchSuccess();
                } else {
                    listener.onLaunchError("Game exited with code: " + exitCode + "\nCheck logs for details.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Process Start Failed", e);
            if (listener != null) {
                listener.onLaunchError("❌ Failed to start game: " + e.getMessage());
            }
        }
    }

    public void stopGame() {
        if (gameProcess != null && gameProcess.isAlive()) {
            gameProcess.destroy();
        }
    }

    public boolean isLaunchReady(String versionId) {
        return versionManager.isVersionInstalled(versionId);
    }
}
