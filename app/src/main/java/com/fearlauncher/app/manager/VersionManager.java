package com.fearlauncher.app.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
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

                // ✅ Extract JRE from assets to internal storage (if not done)
                extractJreIfNeeded();

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
                    listener.onLaunchError("❌ Java runtime not found.\n\nMake sure JRE folders exist in assets/");
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

    // ✅ Extract JRE from assets to internal storage
    private void extractJreIfNeeded() {
        String[] jreFolders = {"jre-8", "jre-17", "jre-21", "jre-25"};
        File destBase = ctx.getFilesDir();
        
        for (String folder : jreFolders) {
            File destDir = new File(destBase, folder);
            // Check if already extracted (contains bin/java)
            if (new File(destDir, "bin/java").exists()) continue;
            
            try {
                Log.d(TAG, "📦 Extracting " + folder + " from assets...");
                copyAssetFolder(ctx.getAssets(), folder, destDir.getAbsolutePath());
                
                // Make java executable
                File javaBin = new File(destDir, "bin/java");
                if (javaBin.exists()) {
                    javaBin.setExecutable(true, true);
                    Log.d(TAG, "✅ Made " + folder + "/bin/java executable");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to extract " + folder, e);
            }
        }
    }

    private void copyAssetFolder(AssetManager assets, String srcPath, String destPath) throws IOException {
        File destDir = new File(destPath);        if (!destDir.exists()) destDir.mkdirs();
        
        String[] files = assets.list(srcPath);
        if (files == null) return;

        for (String file : files) {
            String src = srcPath.isEmpty() ? file : srcPath + "/" + file;
            File destFile = new File(destDir, file);
            
            String[] subFiles = assets.list(src);
            if (subFiles != null && subFiles.length > 0) {
                copyAssetFolder(assets, src, destFile.getAbsolutePath());
            } else {
                try (InputStream in = assets.open(src);
                     FileOutputStream out = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private String findJavaRuntime() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String preferredJre = prefs.getString("java_version", "jre-17"); // Default to 17
        
        Log.d(TAG, "🔍 Looking for Java: " + preferredJre);
        
        // Check extracted internal storage path
        File preferredBin = new File(ctx.getFilesDir(), preferredJre + "/bin/java");
        if (preferredBin.exists() && preferredBin.canExecute()) {
            return preferredBin.getAbsolutePath();
        }
        
        // Fallback to other extracted versions
        String[] fallbackPaths = {"jre-17", "jre-21", "jre-8", "jre-25"};
        for (String folder : fallbackPaths) {
            File bin = new File(ctx.getFilesDir(), folder + "/bin/java");
            if (bin.exists() && bin.canExecute()) {
                Log.w(TAG, "⚠️ Using fallback: " + folder);
                return bin.getAbsolutePath();
            }
        }
        
        // System paths (Termux/Root)
        String[] systemPaths = {"/data/data/com.termux/files/usr/bin/java", "/system/bin/java"};
        for (String path : systemPaths) {            if (new File(path).canExecute()) return path;
        }
        
        return null;
    }

    public boolean isLaunchReady(String versionId) {
        File baseDir = versionManager.getBaseDir();
        File versionDir = new File(baseDir, "versions/" + versionId);
        return new File(versionDir, ".installed").exists();
    }
}
