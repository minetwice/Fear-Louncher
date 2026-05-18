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

public class VersionManager {
    private static final String TAG = "VersionManager";
    private final Context ctx;
    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build();
    private final File baseDir;

    public interface Listener {
        void progress(int p);
        void done(File dir);
        void error(String e);
    }

    public VersionManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.baseDir = new File(ctx.getFilesDir(), "minecraft");
    }

    public void download(String versionId, String jsonUrl, Listener listener) {
        new Thread(() -> {
            try {
                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                File jsonFile = new File(versionDir, versionId + ".json");

                // Download version.json
                downloadFile(jsonUrl, jsonFile, listener, 0, 20);

                // Parse & download libraries (simplified)
                JsonObject versionObj = new Gson().fromJson(
                    new java.io.FileReader(jsonFile), JsonObject.class);
                File libsDir = new File(baseDir, "libraries");
                libsDir.mkdirs();                
                var libs = versionObj.getAsJsonArray("libraries");
                int count = Math.min(libs.size(), 50);
                for (int i = 0; i < count; i++) {
                    JsonObject lib = libs.get(i).getAsJsonObject();
                    if (!lib.has("downloads")) continue;
                    JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                    String url = dl.get("url").getAsString();
                    String path = dl.get("path").getAsString();
                    File dest = new File(libsDir, path);
                    dest.getParentFile().mkdirs();
                    if (!dest.exists()) downloadFile(url, dest, listener, 20 + (i*60/count), 80);
                }

                // Mark installed
                new File(versionDir, ".installed").createNewFile();
                listener.done(versionDir);
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                listener.error(e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, File dest, Listener listener, int start, int end) throws Exception {
        Request req = new Request.Builder().url(url).build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        
        long total = res.body().contentLength();
        long downloaded = 0;
        try (InputStream is = res.body().byteStream();
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
                downloaded += len;
                if (listener != null && total > 0) {
                    int p = start + (int)((downloaded * 1.0 / total) * (end - start));
                    listener.progress(p);
                }
            }
        }
    }

    public boolean isInstalled(String id) {
        return new File(baseDir, "versions/" + id + "/.installed").exists();
    }

    public List<String> getInstalled() {        List<String> list = new ArrayList<>();
        File dir = new File(baseDir, "versions");
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                if (f.isDirectory() && new File(f, ".installed").exists()) list.add(f.getName());
            }
        }
        return list;
    }
}
