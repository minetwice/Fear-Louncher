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
    private final File baseDir; 
    private final OkHttpClient http;

    // ✅ Listener interface with exact method signatures
    public interface Listener {
        void onStatus(String msg);
        void onProgress(int percent, String status);
        void onComplete(File dir);
        void onError(String e);
    }

    // ✅ Constructor accepts Context
    public VersionManager(Context context) {
        this.http = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();
        
        // ✅ External storage path: /storage/emulated/0/FearLauncher/
        File root = Environment.getExternalStorageDirectory();
        this.baseDir = new File(root, "FearLauncher");
        if (!baseDir.exists()) baseDir.mkdirs();
    }

    public void downloadVersion(String versionId, String jsonUrl, Listener listener) {
        new Thread(() -> {
            try {
                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                                File librariesDir = new File(baseDir, "libraries");
                librariesDir.mkdirs();

                File assetsDir = new File(baseDir, "assets");
                assetsDir.mkdirs();

                // 1. Download version.json
                listener.onStatus("Downloading version info...");
                File jsonFile = new File(versionDir, versionId + ".json");
                downloadFile(jsonUrl, jsonFile, listener, 0, 10);

                // 2. Parse JSON
                JsonObject vJson = new Gson().fromJson(
                    new java.io.FileReader(jsonFile), JsonObject.class);
                
                JsonArray libs = vJson.getAsJsonArray("libraries");
                JsonObject assetsObj = vJson.getAsJsonObject("assetIndex");

                // 3. Download Libraries
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
                            int progress = 10 + (int)((float)i / libCount * 60);
                            downloadFile(url, dest, listener, progress, progress + 5); 
                        }
                    }
                }

                // 4. Download Asset Index
                if (assetsObj != null) {
                    String assetId = assetsObj.get("id").getAsString();
                    String assetUrl = assetsObj.get("url").getAsString();
                    File assetIndexFile = new File(assetsDir, "indexes/" + assetId + ".json");
                    assetIndexFile.getParentFile().mkdirs();
                    
                    listener.onStatus("Downloading assets index...");
                    downloadFile(assetUrl, assetIndexFile, listener, 80, 90);
                }

                // 5. Mark Complete
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

        try (InputStream is = res.body().byteStream(); 
             FileOutputStream fos = new FileOutputStream(dest)) {
            
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

    // ✅ Method that VersionsActivity calls
    public boolean isVersionInstalled(String versionId) {
        File marker = new File(baseDir, "versions/" + versionId + "/.installed");
        return marker.exists();
    }

    public File getBaseDir() { return baseDir; }
}
