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
    private static final String TAG = "FearLauncher_VM";
    private final Context ctx;
    private final OkHttpClient http;
    private File baseDir;

    public interface Listener {
        void onStatus(String msg);
        void onProgress(int percent, String status);
        void onComplete(File instanceDir);
        void onError(String e);
    }

    public VersionManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.http = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File external = ctx.getExternalFilesDir(null);
            baseDir = external != null ? external : ctx.getFilesDir();
        } else {
            File root = Environment.getExternalStorageDirectory();
            File legacy = new File(root, "FearLauncher");
            baseDir = legacy.canWrite() ? legacy : ctx.getFilesDir();
        }
        if (!baseDir.exists()) baseDir.mkdirs();
        Log.d(TAG, "Storage: " + baseDir.getAbsolutePath());
    }
    public void downloadVersion(String versionId, String jsonUrl, Listener listener) {
        new Thread(() -> {
            try {
                listener.onStatus("📦 Preparing instance...");
                
                // 1. Create instance directory structure
                File instanceDir = new File(baseDir, "instances/" + versionId);
                String[] subDirs = {"mods", "resourcepacks", "config", "saves", "screenshots", "shaderpacks"};
                for (String dir : subDirs) new File(instanceDir, dir).mkdirs();

                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                
                File librariesDir = new File(baseDir, "libraries");
                if (!librariesDir.exists()) librariesDir.mkdirs();

                File assetsDir = new File(baseDir, "assets/indexes");
                if (!assetsDir.exists()) assetsDir.mkdirs();

                // 2. Download version.json
                listener.onStatus("📜 Downloading manifest...");
                File jsonFile = new File(versionDir, versionId + ".json");
                downloadFile(jsonUrl, jsonFile, listener, 0, 5, "manifest");

                // 3. Parse & download client.jar (THIS FIXES "game.jar missing")
                Gson gson = new Gson();
                JsonObject vJson = gson.fromJson(new java.io.FileReader(jsonFile), JsonObject.class);
                JsonObject downloads = vJson.getAsJsonObject("downloads");
                if (downloads != null && downloads.has("client")) {
                    String clientUrl = downloads.getAsJsonObject("client").get("url").getAsString();
                    File gameJar = new File(versionDir, versionId + ".jar");
                    listener.onStatus("⬇️ Downloading game client...");
                    downloadFile(clientUrl, gameJar, listener, 5, 25, "client.jar");
                }

                // 4. Download libraries
                JsonArray libs = vJson.getAsJsonArray("libraries");
                if (libs != null) {
                    int libCount = libs.size();
                    for (int i = 0; i < libCount; i++) {
                        JsonObject lib = libs.get(i).getAsJsonObject();
                        if (!lib.has("downloads")) continue;
                        JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                        String url = dl.get("url").getAsString();
                        String path = dl.get("path").getAsString();
                        File dest = new File(librariesDir, path);
                        if (!dest.exists()) {
                            dest.getParentFile().mkdirs();
                            int p = 25 + (int)((float)i / libCount * 40);
                            listener.onStatus("📚 Lib: " + (i+1) + "/" + libCount);                            downloadFile(url, dest, listener, p, p+2, "lib");
                        }
                    }
                }

                // 5. Download asset index
                JsonObject assetsObj = vJson.getAsJsonObject("assetIndex");
                if (assetsObj != null) {
                    String assetId = assetsObj.get("id").getAsString();
                    String assetUrl = assetsObj.get("url").getAsString();
                    File assetIndex = new File(assetsDir, assetId + ".json");
                    listener.onStatus("🖼️ Fetching assets...");
                    downloadFile(assetUrl, assetIndex, listener, 70, 85, "assets");
                }

                // 6. Mark complete
                new File(versionDir, ".installed").createNewFile();
                new File(instanceDir, ".instance").createNewFile();
                listener.onStatus("✅ Installation complete!");
                listener.onComplete(instanceDir);

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                listener.onError("❌ " + e.getMessage());
            }
        }).start();
    }

    private void downloadFile(String url, File dest, Listener listener, int startP, int endP, String type) throws Exception {
        if (dest.exists() && dest.length() > 0) return;
        Request req = new Request.Builder().url(url).addHeader("User-Agent", "FearLauncher/2.0").build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        long total = res.body().contentLength();
        long downloaded = 0;
        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384]; int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
                downloaded += len;
                if (total > 0 && listener != null) {
                    int p = startP + (int)((float)downloaded / total * (endP - startP));
                    listener.onProgress(p, type + ": " + (downloaded/1024) + "KB");
                }
            }
        }
    }

    public boolean isVersionInstalled(String versionId) {
        return new File(baseDir, "instances/" + versionId + "/.instance").exists();    }

    public File getInstanceDir(String versionId) {
        return new File(baseDir, "instances/" + versionId);
    }

    public File getBaseDir() { return baseDir; }
}
