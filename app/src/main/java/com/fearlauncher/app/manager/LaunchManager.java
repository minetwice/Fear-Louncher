package com.fearlauncher.app.manager;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
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

    /**
     * Launch Minecraft Java Edition
     */
    public void launchGame(String versionId, String username, String uuid,
                           String accessToken, LaunchListener listener) {
        new Thread(() -> {
            try {
                listener.onLog("🚀 Preparing launch for " + versionId + "...");

                // 1. Verify installation
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

                // 2. Get Java Runtime
                String javaPath = getOrExtractJava();
                if (javaPath == null) {
                    // ⚠️ TEST MODE FALLBACK (so UI flow works without real JRE)
                    listener.onLog("⚠️ Real JRE not found. Using shell fallback for testing.");
                    javaPath = "/system/bin/sh";
                } else {
                    listener.onLog("✅ Java found at: " + javaPath);
                }

                // 3. Build Launch Command
                List<String> cmd = new ArrayList<>();
                cmd.add(javaPath);
                cmd.add("-Xmx2G");
                cmd.add("-Xms1G");
                cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
                cmd.add("-Dminecraft.client.jar=" + gameJar.getAbsolutePath());
                cmd.add("-cp", gameJar.getAbsolutePath());
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

                // 4. Execute Process
                listener.onLog("⚙️ Starting process...");
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(baseDir);
                pb.redirectErrorStream(true);
                gameProcess = pb.start();

                // 5. Stream Output to Logcat & Listener
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

                // 6. Wait for game to exit
                int exitCode = gameProcess.waitFor();
                if (listener != null) {
                    listener.onExit(exitCode);
                    if (exitCode == 0) listener.onLaunchSuccess();
                    else listener.onLaunchError(" Game exited with code: " + exitCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Launch failed", e);
                if (listener != null) listener.onLaunchError("❌ Launch failed: " + e.getMessage());
            }
        }).start();
    }

    /** Stop running game process */
    public void stopGame() {
        if (gameProcess != null && gameProcess.isAlive()) {
            gameProcess.destroy();
            Log.d(TAG, "🛑 Game process stopped.");
        }
    }

    /** Find or extract ARM-compatible Java Runtime */
    private String getOrExtractJava() {
        File jreDir = new File(ctx.getFilesDir(), "jre");
        File javaBin = new File(jreDir, "bin/java");

        if (javaBin.exists() && javaBin.canExecute()) {
            return javaBin.getAbsolutePath();
        }

        // Extract from assets/jre if available
        try {
            String[] assetJre = ctx.getAssets().list("jre");
            if (assetJre != null && assetJre.length > 0) {
                Log.d(TAG, "📦 Extracting JRE from assets...");
                copyAssetFolder(ctx.getAssets(), "jre", jreDir.getAbsolutePath());
                if (javaBin.exists()) {
                    javaBin.setExecutable(true);
                    return javaBin.getAbsolutePath();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "JRE extraction failed", e);
        }

        // Fallback paths (Termux / Rooted devices)
        String[] fallbackPaths = {
            "/data/data/com.termux/files/usr/bin/java",
            "/system/bin/java",
            "/usr/bin/java"
        };
        for (String path : fallbackPaths) {
            if (new File(path).canExecute()) return path;
        }

        return null; // Not found
    }

    /** Recursively copy files from assets to internal storage */
    private void copyAssetFolder(AssetManager assets, String srcPath, String destPath) throws IOException {
        File destDir = new File(destPath);
        if (!destDir.exists()) destDir.mkdirs();
        
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

    /** Check if launch prerequisites are met */
    public boolean isLaunchReady(String versionId) {
        File baseDir = versionManager.getBaseDir();
        File versionDir = new File(baseDir, "versions/" + versionId);
        return new File(versionDir, ".installed").exists();
    }
}
