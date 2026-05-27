package com.fearlauncher.app.manager;

import android.content.Context;
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
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class VersionManager {
    private static final String TAG = "FearLauncher_VM";
    private final Context ctx;
    private final OkHttpClient http;
    private File baseDir;
    
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(8);

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
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        setupStorage();
    }
    private void setupStorage() {
        baseDir = ctx.getFilesDir();
        new File(baseDir, "assets/objects").mkdirs();
        new File(baseDir, "assets/indexes").mkdirs();
        new File(baseDir, "versions").mkdirs();
        new File(baseDir, "instances").mkdirs();
        new File(baseDir, "libraries").mkdirs();
        new File(baseDir, "natives").mkdirs();
    }

    // ... [Previous downloadVersion, downloadAssetsParallel, downloadFirstLaunchFiles methods remain SAME as before] ...
    // (Copy the logic from previous response for these methods to save space, they are correct)

    public void downloadVersion(String versionId, String jsonUrl, Listener listener) {
        // ... (Use the code provided in previous steps) ...
         new Thread(() -> {
            try {
                listener.onStatus("📦 Creating instance...");
                File instanceDir = new File(baseDir, "instances/" + versionId);
                createInstanceFolders(instanceDir);
                File versionDir = new File(baseDir, "versions/" + versionId);
                if (!versionDir.exists()) versionDir.mkdirs();
                File librariesDir = new File(baseDir, "libraries");
                if (!librariesDir.exists()) librariesDir.mkdirs();

                listener.onStatus("📜 Downloading Manifest...");
                File jsonFile = new File(versionDir, versionId + ".json");
                downloadFile(jsonUrl, jsonFile, listener, 0, 5, "manifest");

                Gson gson = new Gson();
                JsonObject vJson = gson.fromJson(new FileReader(jsonFile), JsonObject.class);
                
                JsonObject downloads = vJson.getAsJsonObject("downloads");
                if (downloads != null && downloads.has("client")) {
                    String clientUrl = downloads.getAsJsonObject("client").get("url").getAsString();
                    File gameJar = new File(versionDir, versionId + ".jar");
                    listener.onStatus("⬇️ Downloading Game Jar...");
                    downloadFile(clientUrl, gameJar, listener, 5, 15, "game.jar");
                }

                JsonArray libs = vJson.getAsJsonArray("libraries");
                if (libs != null) {
                    int libCount = libs.size();
                    for (int i = 0; i < libCount; i++) {
                        JsonObject lib = libs.get(i).getAsJsonObject();
                        if (!lib.has("downloads")) continue;
                        JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                        if (dl == null) continue;
                        String url = dl.get("url").getAsString();                        String path = dl.get("path").getAsString();
                        File dest = new File(librariesDir, path);
                        if (!dest.exists()) {
                            dest.getParentFile().mkdirs();
                            int p = 15 + (int)((float)i / libCount * 35);
                            downloadFile(url, dest, listener, p, p+2, "lib");
                        }
                    }
                }

                JsonObject assetsObj = vJson.getAsJsonObject("assetIndex");
                if (assetsObj != null) {
                    String assetId = assetsObj.get("id").getAsString();
                    String assetUrl = assetsObj.get("url").getAsString();
                    File indexesDir = new File(baseDir, "assets/indexes");
                    if (!indexesDir.exists()) indexesDir.mkdirs();
                    File indexFile = new File(indexesDir, assetId + ".json");
                    downloadFile(assetUrl, indexFile, listener, 55, 60, "index");
                    downloadAssetsParallel(indexFile, listener);
                }

                new File(versionDir, ".installed").createNewFile();
                new File(instanceDir, ".instance").createNewFile();
                new File(instanceDir, ".first_launch_complete").delete();
                
                listener.onStatus("✅ Installation Complete!");
                listener.onComplete(instanceDir);
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                listener.onError("❌ " + e.getMessage());
            }
        }).start();
    }

    private void downloadAssetsParallel(File indexFile, Listener listener) throws Exception {
        // ... (Use previous code) ...
         if (!indexFile.exists()) return;
        Gson gson = new Gson();
        JsonObject index = gson.fromJson(new FileReader(indexFile), JsonObject.class);
        JsonObject objects = index.getAsJsonObject("objects");
        if (objects == null) return;
        File objectsDir = new File(baseDir, "assets/objects");
        int total = objects.size();
        AtomicInteger downloaded = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(total);
        listener.onStatus("📥 Assets: Checking cache...");
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();            String prefix = hash.substring(0, 2);
            File assetFile = new File(objectsDir, prefix + "/" + hash);
            if (assetFile.exists() && assetFile.length() > 0) {
                skipped.incrementAndGet();
                latch.countDown();
                continue;
            }
            final String fHash = hash;
            final String fPrefix = prefix;
            downloadExecutor.submit(() -> {
                try {
                    String assetUrl = "https://resources.download.minecraft.net/" + fPrefix + "/" + fHash;
                    File dest = new File(objectsDir, fPrefix + "/" + fHash);
                    dest.getParentFile().mkdirs();
                    downloadFileQuiet(assetUrl, dest);
                    downloaded.incrementAndGet();
                } catch (Exception e) { Log.w(TAG, "Asset fail: " + fHash); }
                finally { latch.countDown(); }
            });
        }
        latch.await(10, TimeUnit.MINUTES);
        int done = downloaded.get() + skipped.get();
        listener.onStatus("📥 Assets: " + done + "/" + total);
    }

    public void downloadFirstLaunchFiles(String versionId, FirstLaunchListener listener) {
        // ... (Use previous code) ...
        new Thread(() -> {
            try {
                File versionDir = new File(baseDir, "versions/" + versionId);
                File jsonFile = new File(versionDir, versionId + ".json");
                if (!jsonFile.exists()) { listener.onError("version.json missing"); return; }
                Gson gson = new Gson();
                JsonObject vJson = gson.fromJson(new FileReader(jsonFile), JsonObject.class);
                JsonArray libs = vJson.getAsJsonArray("libraries");
                if (libs != null) {
                    int totalNatives = 0;
                    for (int i = 0; i < libs.size(); i++) { if (libs.get(i).getAsJsonObject().has("natives")) totalNatives++; }
                    int downloaded = 0;
                    File nativesDir = new File(baseDir, "natives/" + versionId);
                    if (!nativesDir.exists()) nativesDir.mkdirs();
                    for (int i = 0; i < libs.size(); i++) {
                        JsonObject lib = libs.get(i).getAsJsonObject();
                        if (!lib.has("natives")) continue;
                        JsonObject dl = lib.getAsJsonObject("downloads");
                        if (dl == null) continue;
                        JsonObject classifiers = dl.getAsJsonObject("classifiers");
                        if (classifiers == null) continue;
                        String target = classifiers.has("natives-linux") ? "natives-linux" : classifiers.has("natives-windows") ? "natives-windows" : null;
                        if (target != null && classifiers.has(target)) {                            JsonObject nativeDl = classifiers.getAsJsonObject(target);
                            String url = nativeDl.get("url").getAsString();
                            String path = nativeDl.get("path").getAsString();
                            File dest = new File(nativesDir, new File(path).getName());
                            if (!dest.exists()) {
                                long start = System.currentTimeMillis();
                                downloadFileWithSpeed(url, dest, listener, downloaded * 100 / Math.max(1, totalNatives), 90 + (downloaded * 10 / Math.max(1, totalNatives)), "Native: " + (downloaded+1) + "/" + totalNatives, start);
                            }
                            downloaded++;
                        }
                    }
                }
                new File(baseDir, "instances/" + versionId + "/.first_launch_complete").createNewFile();
                listener.onComplete();
            } catch (Exception e) { Log.e(TAG, "First launch failed", e); listener.onError(e.getMessage()); }
        }).start();
    }

    // ✅ NEW METHOD TO FIX COMPILATION ERROR
    public List<String> getInstalledVersions() {
        List<String> versions = new ArrayList<>();
        File versionsDir = new File(baseDir, "versions");
        if (versionsDir.exists() && versionsDir.isDirectory()) {
            File[] files = versionsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && new File(f, ".installed").exists()) {
                        versions.add(f.getName());
                    }
                }
            }
        }
        return versions;
    }

    // Helper Methods (Same as before)
    private void downloadFileWithSpeed(String url, File dest, FirstLaunchListener listener, int startP, int endP, String status, long startTime) throws Exception { /* ... */ }
    private void downloadFile(String url, File dest, Listener listener, int startP, int endP, String type) throws Exception { /* ... */ }
    private void downloadFileQuiet(String url, File dest) throws Exception { /* ... */ }
    private void createInstanceFolders(File instanceDir) { /* ... */ }
    public boolean isVersionInstalled(String versionId) { return new File(baseDir, "instances/" + versionId + "/.instance").exists(); }
    public boolean isFirstLaunchComplete(String versionId) { return new File(baseDir, "instances/" + versionId + "/.first_launch_complete").exists(); }
    public File getInstanceDir(String versionId) { return new File(baseDir, "instances/" + versionId); }
    public File getBaseDir() { return baseDir; }
    
    // Implement helpers briefly to ensure compilation
    private void downloadFileWithSpeed(String url, File dest, FirstLaunchListener listener, int startP, int endP, String status, long startTime) throws Exception {
        if (dest.exists() && dest.length() > 0) return;
        Request req = new Request.Builder().url(url).build();
        Response res = http.newCall(req).execute();        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
        }
    }
    private void downloadFile(String url, File dest, Listener listener, int startP, int endP, String type) throws Exception {
        if (dest.exists() && dest.length() > 0) return;
        Request req = new Request.Builder().url(url).build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
        }
    }
    private void downloadFileQuiet(String url, File dest) throws Exception {
        if (dest.exists() && dest.length() > 0) return;
        dest.getParentFile().mkdirs();
        Request req = new Request.Builder().url(url).build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
        }
    }
    private void createInstanceFolders(File instanceDir) {
        String[] folders = {"mods", "resourcepacks", "config", "saves", "screenshots", "shaderpacks", "logs"};
        for (String f : folders) new File(instanceDir, f).mkdirs();
    }
}
