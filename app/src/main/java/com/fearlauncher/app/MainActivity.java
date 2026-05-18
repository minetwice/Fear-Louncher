package com.fearlauncher.app.manager;

import android.content.Context;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VersionManager {
    private static final String TAG = "FearLauncher_DL";
    // ✅ CHANGE: External Storage (Root Directory) instead of hidden internal dir
    private final File baseDir; 
    private final OkHttpClient http;

    public interface Listener {
        void onStatus(String msg);
        void onProgress(int percent, String status);
        void onComplete(File dir);
        void onError(String e);
    }

    public VersionManager() {
        this.http = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();
        
        // ✅ Path: /storage/emulated/0/FearLauncher
        File root = Environment.getExternalStorageDirectory();
        this.baseDir = new File(root, "FearLauncher");
        if (!baseDir.exists()) baseDir.mkdirs();
    }

    public void downloadVersion(String versionId, String jsonUrl, Listener listener) {
        new Thread(() -> {
            try {
                // 1. Setup Folders
                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                                File librariesDir = new File(baseDir, "libraries");
                librariesDir.mkdirs();

                File assetsDir = new File(baseDir, "assets");
                assetsDir.mkdirs();

                // 2. Download version.json
                listener.onStatus("Downloading version info...");
                File jsonFile = new File(versionDir, versionId + ".json");
                downloadFile(jsonUrl, jsonFile, listener, 0, 10);

                // 3. Parse JSON
                JsonObject vJson = new Gson().fromJson(new java.io.FileReader(jsonFile), JsonObject.class);
                JsonArray libs = vJson.getAsJsonArray("libraries");
                JsonObject assetsObj = vJson.getAsJsonObject("assetIndex");

                // 4. Download Libraries (Loop)
                int libCount = libs.size();
                for (int i = 0; i < libCount; i++) {
                    JsonObject lib = libs.get(i).getAsJsonObject();
                    if (lib.has("downloads")) {
                        JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                        String url = dl.get("url").getAsString();
                        String path = dl.get("path").getAsString();
                        
                        File dest = new File(librariesDir, path);
                        if (!dest.exists()) {
                            dest.getParentFile().mkdirs();
                            listener.onStatus("Lib: " + (i+1) + "/" + libCount);
                            // Progress: 10% to 70%
                            int progress = 10 + (int)((float)i / libCount * 60);
                            downloadFile(url, dest, listener, progress, progress + 5); 
                        }
                    }
                }

                // 5. Download Asset Index (Optional for basic run, but needed for full assets)
                // Just downloading the index file for now to save bandwidth/time on first test
                if (assetsObj != null) {
                    String assetId = assetsObj.get("id").getAsString();
                    String assetUrl = assetsObj.get("url").getAsString();
                    File assetIndexFile = new File(assetsDir, "indexes/" + assetId + ".json");
                    assetIndexFile.getParentFile().mkdirs();
                    
                    listener.onStatus("Downloading assets index...");
                    downloadFile(assetUrl, assetIndexFile, listener, 80, 90);
                }

                // 6. Mark Complete
                new File(versionDir, ".installed").createNewFile();                listener.onComplete(baseDir);

            } catch (Exception e) {
                Log.e(TAG, "Failed", e);
                listener.onError(e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, File dest, Listener listener, int startP, int endP) throws Exception {
        if (dest.exists()) return;
        
        Request req = new Request.Builder().url(url).build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());

        long total = res.body().contentLength();
        long downloaded = 0;

        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
                downloaded += len;
                if (total > 0) {
                    int p = startP + (int)((float)downloaded / total * (endP - startP));
                    listener.onProgress(p, "Downloading...");
                }
            }
        }
    }

    public File getBaseDir() { return baseDir; }
}
