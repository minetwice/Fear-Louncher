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
        
        // ✅ Auto-extract JRE on init
        extractAllJREs();
    }

    // ✅ Extract ALL JRE versions from assets to internal storage
    private void extractAllJREs() {
        String[] jreVersions = {"jre-8", "jre-17", "jre-21", "jre-25"};
        File filesDir = ctx.getFilesDir();
        
        for (String jreVersion : jreVersions) {
            File jreDest = new File(filesDir, jreVersion);
            File javaBin = new File(jreDest, "bin/java");
            
            // Skip if already extracted
            if (javaBin.exists() && javaBin.canExecute()) {
                Log.d(TAG, "✅ " + jreVersion + " already extracted");
                continue;
            }
            
            try {
                Log.d(TAG, "📦 Extracting " + jreVersion + "...");
                copyAssetFolder(ctx.getAssets(), jreVersion, jreDest.getAbsolutePath());                
                // Make java executable
                if (javaBin.exists()) {
                    boolean success = javaBin.setExecutable(true, true);
                    Log.d(TAG, "Made java executable: " + success);
                    
                    // Also fix permissions for all .so files
                    fixNativePermissions(new File(jreDest, "lib"));
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to extract " + jreVersion, e);
            }
        }
    }

    private void fixNativePermissions(File libDir) {
        if (!libDir.exists()) return;
        File[] files = libDir.listFiles();
        if (files == null) return;
        
        for (File f : files) {
            if (f.isDirectory()) {
                fixNativePermissions(f);
            } else if (f.getName().endsWith(".so")) {
                f.setExecutable(true, true);
                f.setReadable(true, false);
            }
        }
    }

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
                    while ((read = in.read(buffer)) != -1) {                        out.write(buffer, 0, read);
                    }
                }
            }
        }
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

                // ✅ First launch: Download binaries & natives
                if (!versionManager.isFirstLaunchComplete(versionId)) {
                    listener.onLog("📦 First launch: Downloading natives & binaries...");
                    versionManager.downloadFirstLaunchFiles(versionId, new VersionManager.FirstLaunchListener() {
                        @Override
                        public void onProgress(int percent, String status, long speed) {
                            String speedStr = speed > 0 ? " (" + formatSpeed(speed) + ")" : "";
                            listener.onLog("⬇️ " + status + speedStr + " " + percent + "%");
                        }
                        @Override
                        public void onComplete() {
                            listener.onLog("✅ Natives downloaded!");
                        }
                        @Override
                        public void onError(String e) {
                            listener.onLaunchError("❌ Failed to download natives: " + e);
                        }
                    });
                }

                File baseDir = versionManager.getBaseDir();
                File versionDir = new File(baseDir, "versions/" + versionId);
                File gameJar = new File(versionDir, versionId + ".jar");
                File nativesDir = new File(baseDir, "natives/" + versionId);
                File assetsDir = new File(baseDir, "assets");
                File instanceDir = new File(baseDir, "instances/" + versionId);

                if (!gameJar.exists()) {
                    listener.onLaunchError("❌ game.jar missing. Redownload version.");
                    return;
                }
                String javaPath = findJavaRuntime();
                if (javaPath == null) {
                    listener.onLaunchError("❌ Java runtime not found.\n\nMake sure JRE folders exist in app assets/\nExtracted to: " + ctx.getFilesDir().getAbsolutePath());
                    return;
                }

                listener.onLog("✅ Java: " + javaPath);
                listener.onLog("📂 Instance: " + instanceDir.getAbsolutePath());

                // Build launch command
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

                listener.onLog("⚙️ Starting Minecraft...");
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(instanceDir);
                pb.redirectErrorStream(true);
                gameProcess = pb.start();

                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(gameProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (listener != null) listener.onLog(line);
                            Log.d("Minecraft", line);
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
                if (listener != null) listener.onLaunchError("❌ " + e.getMessage());
            }
        }).start();
    }

    private String formatSpeed(long bytesPerSec) {
        if (bytesPerSec < 1024) return bytesPerSec + " B/s";
        if (bytesPerSec < 1024 * 1024) return (bytesPerSec / 1024) + " KB/s";
        return String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0));
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
        
        Log.d(TAG, "🔍 Looking for: " + preferredJre);
        
        // Check extracted internal storage
        File preferredBin = new File(ctx.getFilesDir(), preferredJre + "/bin/java");
        if (preferredBin.exists() && preferredBin.canExecute()) {
            Log.d(TAG, "✅ Found: " + preferredBin.getAbsolutePath());
            return preferredBin.getAbsolutePath();
        }
        
        // Fallback
        String[] fallbacks = {"jre-17", "jre-21", "jre-8", "jre-25"};
        for (String folder : fallbacks) {
            File bin = new File(ctx.getFilesDir(), folder + "/bin/java");
            if (bin.exists() && bin.canExecute()) {
                return bin.getAbsolutePath();
            }
        }
        
        return null;    }

    public boolean isLaunchReady(String versionId) {
        return versionManager.isVersionInstalled(versionId);
    }
}
