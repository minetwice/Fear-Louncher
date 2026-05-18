package com.fearlauncher.app.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LaunchManager {
    private static final String TAG = "FearLauncher_Launch";
    private final Context ctx;
    private final VersionManager versionManager;
    private Process gameProcess;

    public interface LaunchListener {
        void onLog(String line);
        void onLaunchSuccess();
        void onLaunchError(String message);
        void onExit(int exitCode);
    }

    public LaunchManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.versionManager = new VersionManager(context);
    }

    public void launchGame(String versionId, String username, String uuid,
                           String accessToken, LaunchListener listener) {
        new Thread(() -> {
            try {
                listener.onLog("🚀 Preparing launch for " + versionId + "...");

                if (!versionManager.isVersionInstalled(versionId)) {
                    listener.onLaunchError("❌ Version not installed.");
                    return;
                }

                File baseDir = versionManager.getBaseDir();
                File versionDir = new File(baseDir, "versions/" + versionId);
                File gameJar = new File(versionDir, versionId + ".jar");
                File nativesDir = new File(baseDir, "natives");
                File assetsDir = new File(baseDir, "assets");

                if (!gameJar.exists()) {
                    listener.onLaunchError("❌ game.jar missing. Redownload version.");
                    return;
                }

                String javaPath = findJavaRuntime();
                if (javaPath == null) {
                    listener.onLaunchError("❌ Java runtime not found.\n\nGo to Settings → Select Java version\nMake sure JRE is in assets/jre-*/");
                    return;
                }

                listener.onLog("✅ Java found at: " + javaPath);

                List<String> cmd = new ArrayList<>();
                cmd.add(javaPath);
                cmd.add("-Xmx2G");
                cmd.add("-Xms1G");
                cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
                cmd.add("-Dminecraft.client.jar=" + gameJar.getAbsolutePath());
                cmd.add("-cp");
                cmd.add(gameJar.getAbsolutePath());
                cmd.add("net.minecraft.client.main.Main");
                
                cmd.add("--username"); cmd.add(username);
                cmd.add("--version"); cmd.add(versionId);
                cmd.add("--gameDir"); cmd.add(baseDir.getAbsolutePath());
                cmd.add("--assetsDir"); cmd.add(assetsDir.getAbsolutePath());
                cmd.add("--assetIndex"); cmd.add(versionId);
                cmd.add("--uuid"); cmd.add(uuid != null ? uuid : "0");
                cmd.add("--accessToken"); cmd.add(accessToken != null ? accessToken : "0");
                cmd.add("--userType"); cmd.add("mojang");
                cmd.add("--versionType"); cmd.add("FearLauncher");

                listener.onLog("⚙️ Starting process...");
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(baseDir);
                pb.redirectErrorStream(true);
                gameProcess = pb.start();

                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(gameProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (listener != null) listener.onLog(line);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Output stream error", e);
                    }
                }).start();

                int exitCode = gameProcess.waitFor();
                if (listener != null) {
                    listener.onExit(exitCode);
                    if (exitCode == 0) listener.onLaunchSuccess();
                    else listener.onLaunchError("Game exited with code: " + exitCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Launch failed", e);
                if (listener != null) listener.onLaunchError("❌ Launch failed: " + e.getMessage());
            }
        }).start();
    }

    public void stopGame() {
        if (gameProcess != null && gameProcess.isAlive()) {
            gameProcess.destroy();
            Log.d(TAG, "🛑 Game process stopped.");
        }
    }

    private String findJavaRuntime() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String preferredJre = prefs.getString("java_version", "jre-17");
        
        Log.d(TAG, "🔍 Looking for user-selected Java: " + preferredJre);
        
        File preferredBin = new File(ctx.getFilesDir(), preferredJre + "/bin/java");
        if (preferredBin.exists() && preferredBin.canExecute()) {
            Log.d(TAG, "✅ Found preferred Java at: " + preferredJre);
            return preferredBin.getAbsolutePath();
        }
        
        String[] fallbackPaths = {
            "jre-17/bin/java",
            "jre-21/bin/java",
            "jre-8/bin/java",
            "jre-25/bin/java",
            "jre/bin/java"
        };
        
        for (String path : fallbackPaths) {
            File jreBin = new File(ctx.getFilesDir(), path);
            if (jreBin.exists() && jreBin.canExecute()) {
                Log.w(TAG, "⚠️ Preferred JRE not found, using fallback: " + path);
                return jreBin.getAbsolutePath();
            }
        }
        
        String[] systemPaths = {
            "/data/data/com.termux/files/usr/bin/java",
            "/system/bin/java",
            "/usr/bin/java"
        };
        for (String path : systemPaths) {
            if (new File(path).canExecute()) return path;
        }
        
        Log.e(TAG, "❌ No Java runtime found!");
        return null;
    }

    public boolean isLaunchReady(String versionId) {
        File baseDir = versionManager.getBaseDir();
        File versionDir = new File(baseDir, "versions/" + versionId);
        return new File(versionDir, ".installed").exists();
    }
}
