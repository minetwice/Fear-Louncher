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
    private final File baseDir;
    private final OkHttpClient http;

    public interface Listener {
        void onStatus(String msg);
        void onProgress(int percent, String status);
        void onComplete(File dir);
        void onError(String e);
    }

    public VersionManager(Context context) {
        this.ctx = context.getApplicationContext();
        
        // ✅ Longer timeouts for Mojang API
        this.http = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

        // ✅ Android 10+ Compatible Storage Path
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped Storage: Use app-specific external directory
            File external = ctx.getExternalFilesDir(null);
            this.baseDir = external != null ? external : ctx.getFilesDir();
        } else {
            // Legacy: Try external storage first
            File root = Environment.getExternalStorageDirectory();
            this.baseDir = new File(root, "FearLauncher");
            if (!baseDir.canWrite()) {                this.baseDir = ctx.getFilesDir(); // Fallback
            }
        }
        if (!baseDir.exists()) baseDir.mkdirs();
        
        Log.d(TAG, "Storage path: " + baseDir.getAbsolutePath());
    }

    public void downloadVersion(String versionId, String jsonUrl, Listener listener) {
        new Thread(() -> {
            try {
                listener.onStatus("Preparing folders...");
                
                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                
                File librariesDir = new File(baseDir, "libraries");
                librariesDir.mkdirs();

                File assetsDir = new File(baseDir, "assets/indexes");
                assetsDir.mkdirs();

                // 1. Download version.json
                listener.onStatus("Downloading version manifest...");
                File jsonFile = new File(versionDir, versionId + ".json");
                downloadFile(jsonUrl, jsonFile, listener, 0, 10, "manifest");

                // 2. Parse JSON
                Gson gson = new Gson();
                JsonObject vJson = gson.fromJson(new java.io.FileReader(jsonFile), JsonObject.class);
                JsonArray libs = vJson.getAsJsonArray("libraries");
                JsonObject assetsObj = vJson.getAsJsonObject("assetIndex");

                // 3. Download Libraries
                if (libs != null) {
                    int libCount = libs.size();
                    listener.onStatus("Downloading libraries (1/" + libCount + ")...");
                    
                    for (int i = 0; i < libCount; i++) {
                        JsonObject lib = libs.get(i).getAsJsonObject();
                        if (!lib.has("downloads")) continue;
                        
                        JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                        String url = dl.get("url").getAsString();
                        String path = dl.get("path").getAsString();
                        
                        File dest = new File(baseDir, "libraries/" + path);
                        if (!dest.exists()) {
                            dest.getParentFile().mkdirs();
                            int progress = 10 + (int)((float)i / libCount * 60);                            listener.onStatus("Lib: " + (i+1) + "/" + libCount);
                            downloadFile(url, dest, listener, progress, progress + 3, "lib");
                        }
                    }
                }

                // 4. Download Asset Index (lightweight)
                if (assetsObj != null) {
                    String assetId = assetsObj.get("id").getAsString();
                    String assetUrl = assetsObj.get("url").getAsString();
                    File assetIndexFile = new File(assetsDir, assetId + ".json");
                    
                    listener.onStatus("Fetching assets index...");
                    downloadFile(assetUrl, assetIndexFile, listener, 80, 90, "assets");
                }

                // 5. Mark Complete
                new File(versionDir, ".installed").createNewFile();
                listener.onStatus("✅ Installation complete!");
                listener.onComplete(baseDir);

            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e.getMessage(), e);
                listener.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, File dest, Listener listener, 
                             int startP, int endP, String type) throws Exception {
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
            
            byte[] buf = new byte[16384]; // Larger buffer for speed
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
                downloaded += len;
                
                if (total > 0 && listener != null) {
                    int p = startP + (int)((float)downloaded / total * (endP - startP));
                    listener.onProgress(p, type + ": " + (downloaded/1024) + "KB");
                }
            }
        }
        
        Log.d(TAG, "Downloaded: " + dest.getName() + " (" + downloaded + " bytes)");
    }

    public boolean isVersionInstalled(String versionId) {
        File marker = new File(baseDir, "versions/" + versionId + "/.installed");
        return marker.exists();
    }

    public File getBaseDir() { 
        Log.d(TAG, "Base dir: " + baseDir.getAbsolutePath());
        return baseDir; 
    }
    
    public String getStoragePath() {
        return baseDir.getAbsolutePath();
    }
}
