package com.fearlauncher.app.manager;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class VersionManager {
    private static final String TAG = "FearLauncher";
    private final Context ctx;
    private final OkHttpClient http;
    
    // ✅ NO 'final' - allows safe conditional assignment
    private File baseDir;

    public interface Listener {
        void onStatus(String msg);
        void onProgress(int percent, String status);
        void onComplete(File dir);
        void onError(String e);
    }

    public VersionManager(Context context) {
        this.ctx = context.getApplicationContext();
        
        // ✅ Longer timeouts for Mojang API (servers can be slow)
        this.http = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

        // ✅ Determine storage path safely (Android 10+ compatible)
        File storagePath;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped Storage: Use app-specific external directory
            // Path: /storage/emulated/0/Android/data/com.fearlauncher.app/files/
            File external = ctx.getExternalFilesDir(null);
            storagePath = external != null ? external : ctx.getFilesDir();        } else {
            // Legacy Android: Try public external storage first
            File root = Environment.getExternalStorageDirectory();
            File legacyPath = new File(root, "FearLauncher");
            
            if (legacyPath.canWrite() && legacyPath.mkdirs()) {
                storagePath = legacyPath;
            } else {
                // Fallback to app-private internal storage
                storagePath = ctx.getFilesDir();
            }
        }
        
        // ✅ Single assignment to baseDir
        this.baseDir = storagePath;
        if (!this.baseDir.exists()) {
            this.baseDir.mkdirs();
        }
        
        Log.d(TAG, "Storage initialized: " + this.baseDir.getAbsolutePath());
    }

    public void downloadVersion(String versionId, String jsonUrl, Listener listener) {
        new Thread(() -> {
            try {
                listener.onStatus("Preparing folders...");
                
                // Create required directories
                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                
                File librariesDir = new File(baseDir, "libraries");
                if (!librariesDir.exists()) librariesDir.mkdirs();

                File assetsDir = new File(baseDir, "assets/indexes");
                if (!assetsDir.exists()) assetsDir.mkdirs();

                // 1. Download version.json (manifest)
                listener.onStatus("Downloading version manifest...");
                File jsonFile = new File(versionDir, versionId + ".json");
                downloadFile(jsonUrl, jsonFile, listener, 0, 10, "manifest");

                // 2. Parse version JSON
                Gson gson = new Gson();
                JsonObject vJson = gson.fromJson(
                    new java.io.FileReader(jsonFile), JsonObject.class);
                
                JsonArray libs = vJson.getAsJsonArray("libraries");
                JsonObject assetsObj = vJson.getAsJsonObject("assetIndex");
                // 3. Download Libraries (loop through all)
                if (libs != null) {
                    int libCount = libs.size();
                    if (libCount > 0) {
                        listener.onStatus("Downloading libraries (1/" + libCount + ")...");
                    }
                    
                    for (int i = 0; i < libCount; i++) {
                        JsonObject lib = libs.get(i).getAsJsonObject();
                        if (!lib.has("downloads")) continue;
                        
                        JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                        String url = dl.get("url").getAsString();
                        String path = dl.get("path").getAsString();
                        
                        File dest = new File(baseDir, "libraries/" + path);
                        if (!dest.exists()) {
                            File parent = dest.getParentFile();
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs();
                            }
                            int progress = 10 + (int)((float)i / libCount * 60);
                            listener.onStatus("Lib: " + (i+1) + "/" + libCount);
                            downloadFile(url, dest, listener, progress, progress + 3, "lib");
                        }
                    }
                }

                // 4. Download Asset Index (lightweight JSON)
                if (assetsObj != null) {
                    String assetId = assetsObj.get("id").getAsString();
                    String assetUrl = assetsObj.get("url").getAsString();
                    File assetIndexFile = new File(assetsDir, assetId + ".json");
                    
                    listener.onStatus("Fetching assets index...");
                    downloadFile(assetUrl, assetIndexFile, listener, 80, 90, "assets");
                }

                // 5. Mark installation complete
                File marker = new File(versionDir, ".installed");
                if (!marker.exists()) {
                    marker.createNewFile();
                }
                
                listener.onStatus("✅ Installation complete!");
                listener.onComplete(baseDir);

            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e.getMessage(), e);
                listener.onError("Error: " + e.getMessage());            }
        }).start();
    }

    private void downloadFile(String url, File dest, Listener listener, 
                             int startP, int endP, String type) throws Exception {
        // Skip if already downloaded and valid
        if (dest.exists() && dest.length() > 0) {
            Log.d(TAG, "Already exists: " + dest.getName());
            return;
        }

        Log.d(TAG, "Downloading " + type + ": " + url);
        
        Request req = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "FearLauncher/2.0")
            .build();
            
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) {
            throw new Exception("HTTP " + res.code() + " for " + url);
        }

        long total = res.body().contentLength();
        long downloaded = 0;

        try (InputStream is = res.body().byteStream(); 
             FileOutputStream fos = new FileOutputStream(dest)) {
            
            byte[] buf = new byte[16384]; // 16KB buffer for faster downloads
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
                downloaded += len;
                
                // Report progress if listener provided
                if (total > 0 && listener != null) {
                    int p = startP + (int)((float)downloaded / total * (endP - startP));
                    String status = type + ": " + (downloaded/1024) + "KB";
                    listener.onProgress(p, status);
                }
            }
        }
        
        Log.d(TAG, "Downloaded: " + dest.getName() + " (" + downloaded + " bytes)");
    }

    // ✅ Check if version is already installed
    public boolean isVersionInstalled(String versionId) {        File marker = new File(baseDir, "versions/" + versionId + "/.installed");
        return marker.exists();
    }

    // ✅ Get base directory for file operations
    public File getBaseDir() { 
        return baseDir; 
    }
    
    // ✅ Get human-readable storage path
    public String getStoragePath() {
        return baseDir != null ? baseDir.getAbsolutePath() : "Unknown";
    }
    
    // ✅ Clean up a version (optional feature)
    public void deleteVersion(String versionId) {
        File versionDir = new File(baseDir, "versions/" + versionId);
        if (versionDir.exists()) {
            deleteRecursive(versionDir);
            Log.d(TAG, "Deleted version: " + versionId);
        }
    }
    
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
