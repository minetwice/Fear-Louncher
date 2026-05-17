package com.fearlauncher.app.manager;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VersionManager {

    private static final String TAG = "VersionManager";
    private static final String BASE_URL = "https://piston-meta.mojang.com";
    private static final String ASSETS_URL = "https://resources.download.minecraft.net";
    
    private final Context context;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    public interface DownloadListener {
        void onProgress(String version, int progress, long downloaded, long total);
        void onComplete(String version, File installPath);
        void onError(String version, String error);
    }

    public VersionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void downloadVersion(String versionId, String versionJsonUrl, DownloadListener listener) {
        new Thread(() -> {
            try {
                File versionsDir = new File(context.getFilesDir(), "minecraft/versions");
                if (!versionsDir.exists()) versionsDir.mkdirs();
                
                File versionDir = new File(versionsDir, versionId);
                if (!versionDir.exists()) versionDir.mkdirs();

                // 1. Download version.json
                File versionJsonFile = new File(versionDir, versionId + ".json");
                downloadFile(versionJsonUrl, versionJsonFile, listener, versionId, 0, 30);

                // 2. Parse and download libraries (simplified)                // In production: parse JSON, download each library with progress

                // 3. Download assets index (simplified)
                // In production: download assets from resources.download.minecraft.net

                // 4. Mark as installed
                new File(versionDir, ".installed").createNewFile();

                listener.onComplete(versionId, versionDir);

            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e.getMessage());
                listener.onError(versionId, e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, File dest, DownloadListener listener, 
                             String versionId, int startProgress, int endProgress) throws Exception {
        Request request = new Request.Builder().url(url).build();
        Response response = httpClient.newCall(request).execute();
        
        if (!response.isSuccessful()) {
            throw new Exception("HTTP " + response.code());
        }

        long total = response.body().contentLength();
        long downloaded = 0;
        int range = endProgress - startProgress;

        try (InputStream is = response.body().byteStream();
             FileOutputStream fos = new FileOutputStream(dest)) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
                downloaded += len;
                
                if (listener != null && total > 0) {
                    int progress = startProgress + (int) ((downloaded * 1.0 / total) * range);
                    listener.onProgress(versionId, progress, downloaded, total);
                }
            }
        }
    }

    public boolean isVersionInstalled(String versionId) {
        File file = new File(context.getFilesDir(), 
            "minecraft/versions/" + versionId + "/.installed");        return file.exists();
    }

    public List<String> getInstalledVersions() {
        List<String> versions = new ArrayList<>();
        File dir = new File(context.getFilesDir(), "minecraft/versions");
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (f.isDirectory() && new File(f, ".installed").exists()) {
                    versions.add(f.getName());
                }
            }
        }
        return versions;
    }

    public void deleteVersion(String versionId) {
        File dir = new File(context.getFilesDir(), "minecraft/versions/" + versionId);
        if (dir.exists()) {
            deleteRecursive(dir);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}
