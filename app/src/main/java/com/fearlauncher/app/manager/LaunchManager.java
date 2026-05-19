package com.fearlauncher.app.manager;

import android.content.Context;
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

    // ✅ LATEST WORKING LINK (PojavLauncher v3.10.1 Release Asset)
    // Ye link direct release page se liya gaya hai aur abhi active hai.
    private static final String JRE_URL = "https://github.com/PojavLauncherTeam/PojavLauncher/releases/download/v3.10.1/jre-android-arm64.zip";

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

    private String ensureJREExists(LaunchListener listener) throws Exception {
        File filesDir = ctx.getFilesDir();
        File jreDir = new File(filesDir, "jre-17");
        File javaBin = new File(jreDir, "bin/java");

        if (javaBin.exists() && javaBin.canExecute() && javaBin.length() > 1000) {
            Log.d(TAG, "✅ JRE already installed.");
            return javaBin.getAbsolutePath();
        }

        Log.d(TAG, "⬇️ JRE missing. Starting download...");
        listener.onLog("📦 Downloading Java Runtime (approx 150MB)...");

        File tempZip = new File(ctx.getCacheDir(), "jre_download_temp.zip");
        if (tempZip.exists()) tempZip.delete();
        try {
            downloadFileWithProgress(JRE_URL, tempZip, listener);
        } catch (Exception e) {
            throw new Exception("Download failed: " + e.getMessage());
        }

        if (!tempZip.exists() || tempZip.length() < 1000) {
            throw new Exception("Downloaded file is empty or corrupted.");
        }

        listener.onLog("📂 Extracting Java Runtime...");
        unzip(tempZip, jreDir);
        tempZip.delete();
        
        if (!javaBin.exists()) {
            throw new Exception("Extraction failed: bin/java not found.");
        }

        javaBin.setExecutable(true, true);
        fixNativePermissions(new File(jreDir, "lib"));

        Log.d(TAG, "✅ JRE Installed Successfully at: " + javaBin.getAbsolutePath());
        return javaBin.getAbsolutePath();
    }

    private void downloadFileWithProgress(String urlString, File dest, LaunchListener listener) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(20000); 
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP Error: " + responseCode + " (Link may be broken)");
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

    private void unzip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
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

    private void fixNativePermissions(File libDir) {
        if (!libDir.exists()) return;
        File[] files = libDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) fixNativePermissions(f);
            else if (f.getName().endsWith(".so")) f.setExecutable(true, true);
        }
    }

    public void launchGame(String versionId, String username, String uuid,
                           String accessToken, LaunchListener listener) {        new Thread(() -> {
            try {
                listener.onLog("🚀 Initializing FearLauncher...");

                String javaPath;
                try {
                    javaPath = ensureJREExists(listener);
                } catch (Exception e) {
                    listener.onLaunchError("❌ Failed to setup Java:\n" + e.getMessage());
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

            Log.d(TAG, "Starting Process...");
            
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
