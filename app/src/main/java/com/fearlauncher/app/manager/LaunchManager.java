package com.fearlauncher.app.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LaunchManager {
    private static final String TAG = "FearLauncher_Launch";
    private final Context ctx;
    private final VersionManager versionManager;
    private Process gameProcess;
    private boolean extractionComplete = false; // Track extraction status

    public interface LaunchListener {
        void onLog(String line);
        void onLaunchSuccess();
        void onLaunchError(String message);
        void onExit(int exitCode);
    }

    public LaunchManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.versionManager = new VersionManager(context);
        // Start extraction in background immediately
        new Thread(this::extractAllJREs).start();
    }

    private void extractAllJREs() {
        String[] jreVersions = {
            "components/jre-8", 
            "components/jre-17", 
            "components/jre-21", 
            "components/jre-25"
        };
        File filesDir = ctx.getFilesDir();
        AssetManager assets = ctx.getAssets();
        
        // ✅ DEBUG: Check if 'components' folder exists
        try {
            String[] components = assets.list("components");
            Log.d(TAG, "📂 Found inside components: " + Arrays.toString(components));
        } catch (IOException e) {
            Log.e(TAG, "Cannot list components folder", e);
        }
        for (String jrePath : jreVersions) {
            String jreVersion = jrePath.substring(jrePath.lastIndexOf('/') + 1);
            File jreDest = new File(filesDir, jreVersion);
            File javaBin = new File(jreDest, "bin/java");
            
            // Check if valid executable already exists
            if (javaBin.exists() && javaBin.length() > 0 && javaBin.canExecute()) {
                Log.d(TAG, "✅ " + jreVersion + " already valid.");
                continue;
            }

            // If exists but broken, delete and re-extract
            if (jreDest.exists()) {
                Log.w(TAG, "⚠️ " + jreVersion + " exists but seems broken. Re-extracting...");
                deleteRecursive(jreDest);
            }
            
            try {
                String[] assetFiles = assets.list(jrePath);
                if (assetFiles == null || assetFiles.length == 0) {
                    Log.e(TAG, "❌ " + jrePath + " is EMPTY or NOT FOUND in assets!");
                    continue;
                }
                
                Log.d(TAG, "📦 Extracting " + jreVersion + " (" + assetFiles.length + " items)...");
                copyAssetFolder(assets, jrePath, jreDest.getAbsolutePath());
                
                if (javaBin.exists()) {
                    // Force executable permission
                    boolean execSuccess = javaBin.setExecutable(true, true);
                    // Fallback chmod via Runtime if setExecutable fails (rare but possible)
                    if (!execSuccess) {
                        Runtime.getRuntime().exec("chmod 755 " + javaBin.getAbsolutePath());
                    }
                    Log.d(TAG, "✅ Extraction complete for " + jreVersion + ". Executable: " + javaBin.canExecute());
                    fixNativePermissions(new File(jreDest, "lib"));
                } else {
                    Log.e(TAG, "❌ bin/java missing after extraction for " + jreVersion);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to extract " + jreVersion, e);
            }
        }
        extractionComplete = true;
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) deleteRecursive(child);        }
        file.delete();
    }

    private void fixNativePermissions(File libDir) {
        if (!libDir.exists()) return;
        File[] files = libDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) fixNativePermissions(f);
            else if (f.getName().endsWith(".so")) f.setExecutable(true, true);
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
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
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
                // ✅ Wait for extraction (Increased to 60 seconds)
                int waitCount = 0;
                while (!extractionComplete && waitCount < 120) {
                    Thread.sleep(500);
                    waitCount++;
                }

                // Double check extraction
                String javaPath = findJavaRuntime();
                if (javaPath == null) {
                    // Try one last wait just in case
                    Thread.sleep(2000); 
                    javaPath = findJavaRuntime();
                }

                if (javaPath == null) {
                    Log.e(TAG, "Final Check: JRE not found in " + ctx.getFilesDir().getAbsolutePath());
                    listener.onLaunchError("❌ Java runtime not found.\n\nPlease check Logcat for 'Found inside components'.\nMake sure folders are named exactly: jre-8, jre-17, etc.");
                    return;
                }

                // First launch natives download
                if (!versionManager.isFirstLaunchComplete(versionId)) {
                    listener.onLog("📦 First launch: Downloading natives...");
                    versionManager.downloadFirstLaunchFiles(versionId, new VersionManager.FirstLaunchListener() {
                        @Override public void onProgress(int p, String s, long speed) {
                            listener.onLog("⬇️ " + s + " " + p + "%");
                        }
                        @Override public void onComplete() { listener.onLog("✅ Natives ready!"); }
                        @Override public void onError(String e) { listener.onLaunchError("❌ Natives failed: " + e); }
                    });
                }

                File baseDir = versionManager.getBaseDir();
                File versionDir = new File(baseDir, "versions/" + versionId);
                File gameJar = new File(versionDir, versionId + ".jar");
                File nativesDir = new File(baseDir, "natives/" + versionId);
                File assetsDir = new File(baseDir, "assets");
                File instanceDir = new File(baseDir, "instances/" + versionId);

                if (!gameJar.exists()) {
                    listener.onLaunchError("❌ game.jar missing.");
                    return;
                }

                listener.onLog("✅ Java: " + javaPath);
                listener.onLog("📂 Instance: " + instanceDir.getAbsolutePath());

                List<String> cmd = new ArrayList<>();
                cmd.add(javaPath);                cmd.add("-Xmx2G");
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
                    else listener.onLaunchError("Game exited: " + exitCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Launch failed", e);
                if (listener != null) listener.onLaunchError("❌ " + e.getMessage());
            }
        }).start();
    }

    public void stopGame() {
        if (gameProcess != null && gameProcess.isAlive()) gameProcess.destroy();    }

    private String findJavaRuntime() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String preferredJre = prefs.getString("java_version", "jre-17");
        
        // Check preferred
        File preferredBin = new File(ctx.getFilesDir(), preferredJre + "/bin/java");
        if (preferredBin.exists() && preferredBin.canExecute()) return preferredBin.getAbsolutePath();
        
        // Fallbacks
        String[] fallbacks = {"jre-17", "jre-21", "jre-8", "jre-25"};
        for (String folder : fallbacks) {
            File bin = new File(ctx.getFilesDir(), folder + "/bin/java");
            if (bin.exists() && bin.canExecute()) {
                Log.d(TAG, "Using fallback JRE: " + folder);
                return bin.getAbsolutePath();
            }
        }
        return null;
    }

    public boolean isLaunchReady(String versionId) {
        return versionManager.isVersionInstalled(versionId);
    }
}
