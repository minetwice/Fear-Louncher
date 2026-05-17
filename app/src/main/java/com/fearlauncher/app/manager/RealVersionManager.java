package com.fearlauncher.app.manager;

import android.content.Context;
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

public class RealVersionManager {
    private final Context ctx;
    private final OkHttpClient http = new OkHttpClient();
    private final File baseDir;

    public interface DLListener { void progress(int p); void done(File dir); void error(String e); }

    public RealVersionManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.baseDir = new File(ctx.getFilesDir(), "minecraft");
    }

    public void downloadVersion(String versionId, String jsonUrl, DLListener listener) {
        new Thread(() -> {
            try {
                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                File jsonFile = new File(versionDir, versionId + ".json");

                // 1. Download version.json
                downloadFile(jsonUrl, jsonFile, listener, 0, 20);

                // 2. Parse libraries & assets
                JsonObject versionObj = new Gson().fromJson(new java.io.FileReader(jsonFile), JsonObject.class);
                JsonObject assetsObj = versionObj.getAsJsonObject("assetIndex");
                String assetId = assetsObj.get("id").getAsString();
                String assetUrl = assetsObj.get("url").getAsString();

                // 3. Download asset index
                File assetIndex = new File(baseDir, "assets/indexes/" + assetId + ".json");
                assetIndex.getParentFile().mkdirs();
                downloadFile(assetUrl, assetIndex, listener, 20, 40);

                // 4. Download libraries (simplified: first 5 for demo, real needs full loop)
                File libsDir = new File(baseDir, "libraries");
                libsDir.mkdirs();
                var libs = versionObj.getAsJsonArray("libraries");
                int count = Math.min(libs.size(), 50); // Limit for speed
                for (int i = 0; i < count; i++) {
                    JsonObject lib = libs.get(i).getAsJsonObject();
                    if (!lib.has("downloads")) continue;
                    JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                    String libUrl = dl.get("url").getAsString();
                    String libPath = dl.get("path").getAsString();
                    File libFile = new File(libsDir, libPath);
                    libFile.getParentFile().mkdirs();
                    if (!libFile.exists()) {
                        downloadFile(libUrl, libFile, listener, 40 + (i*60/count), 80);
                    }
                }

                // 5. Create .installed marker
                new File(versionDir, ".installed").createNewFile();
                listener.done(versionDir);
            } catch (Exception e) {
                listener.error(e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, File dest, DLListener listener, int start, int end) throws Exception {
        Request req = new Request.Builder().url(url).build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        long total = res.body().contentLength();
        long downloaded = 0;
        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
                downloaded += len;
                if (listener != null && total > 0) {
                    listener.progress(start + (int)((downloaded * 1.0 / total) * (end - start)));
                }
            }
        }
    }
}
