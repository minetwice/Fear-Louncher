package com.fearlauncher.app.manager;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;
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

    public interface FirstLaunchListener {
        void onProgress(int percent, String status, long speed);
        void onComplete();
        void onError(String e);
    }

    public VersionManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.http = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();
        setupStorage();
    }

    private void setupStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {            File external = ctx.getExternalFilesDir(null);
            baseDir = external != null ? external : ctx.getFilesDir();
        } else {
            File root = Environment.getExternalStorageDirectory();
            File legacy = new File(root, "FearLauncher");
            baseDir = legacy.canWrite() ? legacy : ctx.getFilesDir();
        }
        if (!baseDir.exists()) baseDir.mkdirs();
    }

    public void downloadVersion(String versionId, String jsonUrl, Listener listener) {
        new Thread(() -> {
            try {
                listener.onStatus("📦 Creating instance...");
                File instanceDir = new File(baseDir, "instances/" + versionId);
                createInstanceFolders(instanceDir);

                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                
                File librariesDir = new File(baseDir, "libraries");
                if (!librariesDir.exists()) librariesDir.mkdirs();

                File assetsDir = new File(baseDir, "assets/indexes");
                if (!assetsDir.exists()) assetsDir.mkdirs();

                listener.onStatus("📜 Downloading manifest...");
                File jsonFile = new File(versionDir, versionId + ".json");
                downloadFile(jsonUrl, jsonFile, listener, 0, 5, "manifest");

                Gson gson = new Gson();
                JsonObject vJson = gson.fromJson(new java.io.FileReader(jsonFile), JsonObject.class);
                
                JsonObject downloads = vJson.getAsJsonObject("downloads");
                if (downloads != null && downloads.has("client")) {
                    String clientUrl = downloads.getAsJsonObject("client").get("url").getAsString();
                    File gameJar = new File(versionDir, versionId + ".jar");
                    listener.onStatus("⬇️ Downloading client...");
                    downloadFile(clientUrl, gameJar, listener, 5, 25, "client.jar");
                }

                // ✅ FIXED: Properly scoped libs variable
                JsonArray libs = vJson.getAsJsonArray("libraries");
                if (libs != null) {
                    int libCount = libs.size();
                    for (int i = 0; i < libCount; i++) {
                        JsonObject lib = libs.get(i).getAsJsonObject();
                        if (!lib.has("downloads")) continue;
                        JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                        String url = dl.get("url").getAsString();                        String path = dl.get("path").getAsString();
                        File dest = new File(librariesDir, path);
                        if (!dest.exists()) {
                            dest.getParentFile().mkdirs();
                            int p = 25 + (int)((float)i / libCount * 35);
                            listener.onStatus("📚 Lib: " + (i+1) + "/" + libCount);
                            downloadFile(url, dest, listener, p, p+2, "lib");
                        }
                    }
                }

                JsonObject assetsObj = vJson.getAsJsonObject("assetIndex");
                if (assetsObj != null) {
                    String assetId = assetsObj.get("id").getAsString();
                    String assetUrl = assetsObj.get("url").getAsString();
                    File assetIndexFile = new File(assetsDir, assetId + ".json");
                    listener.onStatus("🖼️ Fetching assets...");
                    downloadFile(assetUrl, assetIndexFile, listener, 65, 70, "index");
                    downloadAssets(assetIndexFile, listener);
                }

                new File(versionDir, ".installed").createNewFile();
                new File(instanceDir, ".instance").createNewFile();
                new File(instanceDir, ".first_launch_complete").delete();
                
                listener.onStatus("✅ Installed! First launch will fetch natives.");
                listener.onComplete(instanceDir);

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                listener.onError("❌ " + e.getMessage());
            }
        }).start();
    }

    private void createInstanceFolders(File instanceDir) {
        String[] folders = {"mods", "resourcepacks", "config", "saves", "screenshots", "shaderpacks", "logs", "crash-reports"};
        for (String f : folders) new File(instanceDir, f).mkdirs();
        try { new File(instanceDir, "options.txt").createNewFile(); } catch (Exception ignored) {}
    }

    public void downloadFirstLaunchFiles(String versionId, FirstLaunchListener listener) {
        new Thread(() -> {
            try {
                File baseDir = getBaseDir();
                File versionDir = new File(baseDir, "versions/" + versionId);
                File jsonFile = new File(versionDir, versionId + ".json");
                
                if (!jsonFile.exists()) { listener.onError("version.json missing"); return; }
                Gson gson = new Gson();
                JsonObject vJson = gson.fromJson(new java.io.FileReader(jsonFile), JsonObject.class);
                
                // ✅ FIXED: Declare libs in THIS method's scope
                JsonArray libs = vJson.getAsJsonArray("libraries");
                
                if (libs != null) {
                    int totalNatives = 0;
                    for (int i = 0; i < libs.size(); i++) {
                        if (libs.get(i).getAsJsonObject().has("natives")) totalNatives++;
                    }

                    int downloaded = 0;
                    File nativesDir = new File(baseDir, "natives/" + versionId);
                    if (!nativesDir.exists()) nativesDir.mkdirs();

                    for (int i = 0; i < libs.size(); i++) {
                        JsonObject lib = libs.get(i).getAsJsonObject();
                        if (!lib.has("natives")) continue;

                        JsonObject downloads = lib.getAsJsonObject("downloads");
                        if (downloads == null) continue;

                        JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                        if (classifiers == null) continue;

                        String target = classifiers.has("natives-linux") ? "natives-linux" : 
                                        classifiers.has("natives-windows") ? "natives-windows" : null;
                        
                        if (target != null && classifiers.has(target)) {
                            JsonObject nativeDl = classifiers.getAsJsonObject(target);
                            String url = nativeDl.get("url").getAsString();
                            String path = nativeDl.get("path").getAsString();
                            File dest = new File(nativesDir, new File(path).getName());
                            
                            if (!dest.exists()) {
                                long start = System.currentTimeMillis();
                                downloadFileWithSpeed(url, dest, listener, 
                                    downloaded * 100 / Math.max(1, totalNatives), 
                                    90 + (downloaded * 10 / Math.max(1, totalNatives)), 
                                    "Native: " + (downloaded+1) + "/" + totalNatives, start);
                            }
                            downloaded++;
                        }
                    }
                }

                new File(baseDir, "instances/" + versionId + "/.first_launch_complete").createNewFile();
                listener.onComplete();
            } catch (Exception e) {
                Log.e(TAG, "First launch failed", e);
                listener.onError(e.getMessage());
            }
        }).start();
    }

    private void downloadFileWithSpeed(String url, File dest, FirstLaunchListener listener, 
                                       int startP, int endP, String status, long startTime) throws Exception {
        if (dest.exists() && dest.length() > 0) return;
        
        Request req = new Request.Builder().url(url).addHeader("User-Agent", "FearLauncher/2.0").build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        
        long total = res.body().contentLength();
        long downloaded = 0;
        long lastUpdate = System.currentTimeMillis();
        long lastDownloaded = 0;

        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384]; int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
                downloaded += len;
                
                long now = System.currentTimeMillis();
                if (now - lastUpdate >= 1000 && listener != null) { 
                    long deltaTime = (now - lastUpdate) / 1000;
                    if (deltaTime <= 0) deltaTime = 1;
                    
                    long speed = (downloaded - lastDownloaded) / deltaTime;
                    int progress = startP + (int)((float)downloaded / total * (endP - startP));
                    listener.onProgress(progress, status, speed);
                    
                    lastUpdate = now;
                    lastDownloaded = downloaded;
                }
            }
        }
    }

    private void downloadFile(String url, File dest, Listener listener, int startP, int endP, String type) throws Exception {
        if (dest.exists() && dest.length() > 0) return;
        Request req = new Request.Builder().url(url).addHeader("User-Agent", "FearLauncher/2.0").build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        long total = res.body().contentLength();
        long downloaded = 0;
        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {            byte[] buf = new byte[16384]; int len;
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

    private void downloadAssets(File assetIndexFile, Listener listener) throws Exception {
        if (!assetIndexFile.exists()) return;
        Gson gson = new Gson();
        JsonObject index = gson.fromJson(new java.io.FileReader(assetIndexFile), JsonObject.class);
        JsonObject objects = index.getAsJsonObject("objects");
        if (objects == null) return;

        File objectsDir = new File(baseDir, "assets/objects");
        if (!objectsDir.exists()) objectsDir.mkdirs();

        int total = objects.size();
        int downloaded = 0, skipped = 0;

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            File assetFile = new File(objectsDir, prefix + "/" + hash);
            
            if (assetFile.exists() && assetFile.length() > 0) { skipped++; continue; }

            String assetUrl = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
            try {
                downloadFileQuiet(assetUrl, assetFile);
                downloaded++;
                int progress = 70 + (int)((float)(downloaded + skipped) / total * 30);
                listener.onProgress(progress, "Assets: " + (downloaded + skipped) + "/" + total);
            } catch (Exception e) { Log.w(TAG, "Asset fail: " + hash, e); }
        }
    }

    private void downloadFileQuiet(String url, File dest) throws Exception {
        if (dest.exists() && dest.length() > 0) return;
        dest.getParentFile().mkdirs();
        Request req = new Request.Builder().url(url).addHeader("User-Agent", "FearLauncher/2.0").build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {            byte[] buf = new byte[16384]; int len;
            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
        }
    }

    public boolean isVersionInstalled(String versionId) {
        return new File(baseDir, "instances/" + versionId + "/.instance").exists();
    }

    public boolean isFirstLaunchComplete(String versionId) {
        return new File(baseDir, "instances/" + versionId + "/.first_launch_complete").exists();
    }

    public File getInstanceDir(String versionId) { return new File(baseDir, "instances/" + versionId); }
    public File getBaseDir() { return baseDir; }
}
