package com.fearlauncher.app.manager;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LaunchManager {
    private static final String TAG = "FearLauncher_Launch";
    private final Context ctx;
    private final VersionManager versionManager;
    private Process gameProcess;

    // ✅ Fallback URL (Only used if Assets ZIP is missing or corrupted)
    private static final String JRE_BACKUP_URL = "https://github.com/PojavLauncherTeam/PojavLauncher/releases/download/v3.10.1/jre-android-arm64.zip";

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
     * PROFESSIONAL METHOD: 3-Layer JRE Acquisition (ZIP Based)
     * 1. Check Internal Storage (Fastest)
     * 2. Extract ZIP from APK Assets (Offline & Reliable)
     * 3. Download ZIP from Internet (Last Resort)
     */
    private String ensureJREExists(LaunchListener listener) throws Exception {
        File filesDir = ctx.getFilesDir();
        File jreDir = new File(filesDir, "jre-17");
        File javaBin = new File(jreDir, "bin/java");

        // --- LAYER 1: Check Internal Storage ---
        if (javaBin.exists() && javaBin.canExecute() && javaBin.length() > 1000) {
            Log.d(TAG, "✅ Layer 1: JRE found in internal storage.");
            return javaBin.getAbsolutePath();        }

        // --- LAYER 2: Extract ZIP from APK Assets ---
        Log.d(TAG, "⬇️ Layer 2: Checking APK Assets for jre-17.zip...");
        InputStream inputStream = null;
        try {
            AssetManager assets = ctx.getAssets();
            
            // Directly try to open the ZIP file from assets
            try {
                inputStream = assets.open("components/jre-17.zip");
                Log.d(TAG, "📦 Found jre-17.zip in Assets. Extracting...");
                listener.onLog("📦 Installing Java Runtime (from App)...");
                
                // Clean old extraction if corrupted
                if (jreDir.exists()) deleteRecursive(jreDir);
                jreDir.mkdirs();

                // Extract ZIP directly from InputStream
                unzip(inputStream, jreDir);
                
                // Verify extraction
                if (javaBin.exists()) {
                    javaBin.setExecutable(true, true);
                    fixNativePermissions(new File(jreDir, "lib"));
                    Log.d(TAG, "✅ Layer 2: JRE extracted successfully from ZIP.");
                    return javaBin.getAbsolutePath();
                } else {
                    throw new Exception("Extraction failed: bin/java missing after unzip.");
                }
            } catch (FileNotFoundException e) {
                // If file not found in assets, proceed to Layer 3
                Log.w(TAG, "⚠️ jre-17.zip not found in Assets. Proceeding to Layer 3.");
            } finally {
                if (inputStream != null) inputStream.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Layer 2 Failed", e);
            // Continue to Layer 3 if Layer 2 fails unexpectedly
        }

        // --- LAYER 3: Download ZIP from Internet (Fallback) ---
        Log.d(TAG, "⬇️ Layer 3: Downloading ZIP from Internet...");
        listener.onLog("🌐 Assets missing. Downloading Java Runtime...");
        
        File tempZip = new File(ctx.getCacheDir(), "jre_download_temp.zip");
        if (tempZip.exists()) tempZip.delete();

        try {            downloadFileWithProgress(JRE_BACKUP_URL, tempZip, listener);
            
            if (tempZip.length() < 1000) {
                throw new Exception("Downloaded file is too small/corrupted.");
            }
            
            listener.onLog("📂 Extracting downloaded Java ZIP...");
            if (jreDir.exists()) deleteRecursive(jreDir);
            jreDir.mkdirs();
            
            unzip(new FileInputStream(tempZip), jreDir);
            tempZip.delete();
            
            if (!javaBin.exists()) {
                throw new Exception("Extraction failed after download.");
            }
            
            javaBin.setExecutable(true, true);
            fixNativePermissions(new File(jreDir, "lib"));
            
            Log.d(TAG, "✅ Layer 3: JRE downloaded and installed.");
            return javaBin.getAbsolutePath();

        } catch (Exception e) {
            throw new Exception("All methods failed.\n1. Not in Storage\n2. Not in Assets (jre-17.zip)\n3. Download Failed: " + e.getMessage());
        }
    }

    // --- Helper: Delete Folder Recursively ---
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    // --- Helper: Unzip InputStream to Directory ---
    private void unzip(InputStream inputStream, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // --- Helper: Download File with Progress ---
    private void downloadFileWithProgress(String urlString, File dest, LaunchListener listener) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(20000); 
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP Error: " + responseCode);
        }

        int fileLength = conn.getContentLength();
        
        try (InputStream input = conn.getInputStream();
             FileOutputStream output = new FileOutputStream(dest)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                if (fileLength > 0) {
                    int progress = (int) (totalRead * 100 / fileLength);
                    if (progress % 5 == 0) {
                        listener.onJREProgress(progress);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }
    // --- Helper: Set Permissions for Native Libraries ---
    private void fixNativePermissions(File libDir) {
        if (!libDir.exists()) return;
        File[] files = libDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) fixNativePermissions(f);
            else if (f.getName().endsWith(".so")) f.setExecutable(true, true);
        }
    }

    // --- Main Launch Logic ---
    public void launchGame(String versionId, String username, String uuid,
                           String accessToken, LaunchListener listener) {
        new Thread(() -> {
            try {
                listener.onLog("🚀 Initializing FearLauncher...");

                String javaPath;
                try {
                    javaPath = ensureJREExists(listener);
                } catch (Exception e) {
                    listener.onLaunchError("❌ Java Setup Failed:\n" + e.getMessage() + "\n\nFix: Ensure 'jre-17.zip' exists in app/src/main/assets/components/");
                    return;
                }

                if (!versionManager.isVersionInstalled(versionId)) {
                    listener.onLaunchError("❌ Minecraft version not installed.");
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

    // --- Start Minecraft Process ---
    private void startMinecraftProcess(String javaPath, String versionId, String username, 
                                       String uuid, String accessToken, LaunchListener listener) {
        try {
            File baseDir = versionManager.getBaseDir();
            File versionDir = new File(baseDir, "versions/" + versionId);
            File gameJar = new File(versionDir, versionId + ".jar");
            File nativesDir = new File(baseDir, "natives/" + versionId);
            File assetsDir = new File(baseDir, "assets");
            File instanceDir = new File(baseDir, "instances/" + versionId);

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

            Log.d(TAG, "Starting Process...");
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(instanceDir);
            pb.redirectErrorStream(true);
            gameProcess = pb.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(gameProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {                        if (listener != null) listener.onLog(line);
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
        }
    }

    public void stopGame() {
        if (gameProcess != null && gameProcess.isAlive()) gameProcess.destroy();
    }

    public boolean isLaunchReady(String versionId) {
        return versionManager.isVersionInstalled(versionId);
    }
}
