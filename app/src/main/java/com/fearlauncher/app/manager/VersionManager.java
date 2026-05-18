package com.fearlauncher.app.manager;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
    private static final String TAG = "VersionManager";
    private final Context ctx;
    private final OkHttpClient http;
    private final File baseDir;

    public interface Listener {
        void progress(int percent);
        void done(File installDir);
        void error(String message);
    }

    public VersionManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.http = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        this.baseDir = new File(ctx.getFilesDir(), "minecraft");
    }

    public void downloadVersion(String versionId, String versionJsonUrl, Listener listener) {
        new Thread(() -> {
            try {
                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                File jsonFile = new File(versionDir, versionId + ".json");

                // 1. Download version.json
                downloadFile(versionJsonUrl, jsonFile, listener, 0, 20);

                // 2. Parse and download libraries
                JsonObject versionObj = new Gson().fromJson(
                    new java.io.FileReader(jsonFile), JsonObject.class);                
                File libsDir = new File(baseDir, "libraries");
                libsDir.mkdirs();
                
                var libs = versionObj.getAsJsonArray("libraries");
                int count = Math.min(libs.size(), 50); // Limit for speed
                
                for (int i = 0; i < count; i++) {
                    JsonObject lib = libs.get(i).getAsJsonObject();
                    if (!lib.has("downloads")) continue;
                    
                    JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                    String url = dl.get("url").getAsString();
                    String path = dl.get("path").getAsString();
                    
                    File dest = new File(libsDir, path);
                    dest.getParentFile().mkdirs();
                    
                    if (!dest.exists()) {
                        downloadFile(url, dest, listener, 20 + (i * 60 / count), 80);
                    }
                }

                // 3. Mark as installed
                new File(versionDir, ".installed").createNewFile();
                listener.done(versionDir);

            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e.getMessage(), e);
                listener.error(e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, File dest, Listener listener, int startProgress, int endProgress) throws Exception {
        Request request = new Request.Builder().url(url).build();
        Response response = http.newCall(request).execute();
        
        if (!response.isSuccessful()) {
            throw new Exception("HTTP " + response.code());
        }

        long total = response.body().contentLength();
        long downloaded = 0;
        int range = endProgress - startProgress;

        try (InputStream is = response.body().byteStream();
             FileOutputStream fos = new FileOutputStream(dest)) {
            
            byte[] buffer = new byte[8192];            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
                downloaded += len;
                
                if (listener != null && total > 0) {
                    int progress = startProgress + (int) ((downloaded * 1.0 / total) * range);
                    listener.progress(progress);
                }
            }
        }
    }

    public boolean isVersionInstalled(String versionId) {
        File marker = new File(baseDir, "versions/" + versionId + "/.installed");
        return marker.exists();
    }

    public List<String> getInstalledVersions() {
        List<String> versions = new ArrayList<>();
        File dir = new File(baseDir, "versions");
        if (dir.exists() && dir.isDirectory()) {
            File[] folders = dir.listFiles(File::isDirectory);
            if (folders != null) {
                for (File f : folders) {
                    if (new File(f, ".installed").exists()) {
                        versions.add(f.getName());
                    }
                }
            }
        }
        return versions;
    }

    public void deleteVersion(String versionId) {
        File dir = new File(baseDir, "versions/" + versionId);
        if (dir.exists()) {
            deleteRecursive(dir);
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
        }        file.delete();
    }
}
