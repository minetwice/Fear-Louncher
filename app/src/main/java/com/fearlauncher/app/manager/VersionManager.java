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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class VersionManager {
    private static final String TAG = "FearLauncher_VM";
    private final Context ctx;
    private final OkHttpClient http;
    private File baseDir;
    
    // ✅ Thread Pool for Parallel Downloads (Faster Speed)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File external = ctx.getExternalFilesDir(null);
            baseDir = external != null ? external : ctx.getFilesDir();
        } else {
            File root = Environment.getExternalStorageDirectory();
            File legacy = new File(root, "FearLauncher");
            baseDir = legacy.canWrite() ? legacy : ctx.getFilesDir();
        }
        if (!baseDir.exists()) baseDir.mkdirs();
        
        // Ensure Global Asset Folder Exists
        new File(baseDir, "assets/objects").mkdirs();
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

                // 1. Download Manifest
                listener.onStatus("📜 Manifest...");
                File jsonFile = new File(versionDir, versionId + ".json");
                downloadFile(jsonUrl, jsonFile, listener, 0, 5, "manifest");

                Gson gson = new Gson();
                JsonObject vJson = gson.fromJson(new java.io.FileReader(jsonFile), JsonObject.class);
                
                // 2. Download Client Jar
                JsonObject downloads = vJson.getAsJsonObject("downloads");
                if (downloads != null && downloads.has("client")) {
                    String clientUrl = downloads.getAsJsonObject("client").get("url").getAsString();
                    File gameJar = new File(versionDir, versionId + ".jar");
                    listener.onStatus("⬇️ Client.jar...");
                    downloadFile(clientUrl, gameJar, listener, 5, 15, "client.jar");
                }

                // 3. Download Libraries
                JsonArray libs = vJson.getAsJsonArray("libraries");
                if (libs != null) {
                    int libCount = libs.size();                    for (int i = 0; i < libCount; i++) {
                        JsonObject lib = libs.get(i).getAsJsonObject();
                        if (!lib.has("downloads")) continue;
                        JsonObject dl = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
                        String url = dl.get("url").getAsString();
                        String path = dl.get("path").getAsString();
                        File dest = new File(librariesDir, path);
                        if (!dest.exists()) {
                            dest.getParentFile().mkdirs();
                            int p = 15 + (int)((float)i / libCount * 35);
                            downloadFile(url, dest, listener, p, p+2, "lib");
                        }
                    }
                }

                // 4. ✅ OPTIMIZED ASSET DOWNLOAD (Parallel + Global Cache)
                JsonObject assetsObj = vJson.getAsJsonObject("assetIndex");
                if (assetsObj != null) {
                    String assetId = assetsObj.get("id").getAsString();
                    String assetUrl = assetsObj.get("url").getAsString();
                    
                    // Download Index Map first
                    File assetsDir = new File(baseDir, "assets/indexes");
                    if (!assetsDir.exists()) assetsDir.mkdirs();
                    File indexFile = new File(assetsDir, assetId + ".json");
                    downloadFile(assetUrl, indexFile, listener, 55, 60, "index");

                    // Download Actual Assets in Parallel
                    downloadAssetsParallel(indexFile, listener);
                }

                // 5. Mark Complete
                new File(versionDir, ".installed").createNewFile();
                new File(instanceDir, ".instance").createNewFile();
                new File(instanceDir, ".first_launch_complete").delete();
                
                listener.onStatus("✅ Installed!");
                listener.onComplete(instanceDir);

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                listener.onError("❌ " + e.getMessage());
            }
        }).start();
    }

    /**
     * ✅ PROFESSIONAL METHOD: Parallel Asset Download with Global Cache
     */
    private void downloadAssetsParallel(File indexFile, Listener listener) throws Exception {        if (!indexFile.exists()) return;
        
        Gson gson = new Gson();
        JsonObject index = gson.fromJson(new java.io.FileReader(indexFile), JsonObject.class);
        JsonObject objects = index.getAsJsonObject("objects");
        if (objects == null) return;

        File objectsDir = new File(baseDir, "assets/objects");
        int total = objects.size();
        AtomicInteger downloaded = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        
        // Use CountDownLatch to wait for all parallel tasks
        CountDownLatch latch = new CountDownLatch(total);
        AtomicInteger errors = new AtomicInteger(0);

        listener.onStatus("📥 Assets: Checking cache...");

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            
            File assetFile = new File(objectsDir, prefix + "/" + hash);
            
            // ✅ SMART CHECK: If exists and valid size, SKIP download
            if (assetFile.exists() && assetFile.length() > 0) {
                skipped.incrementAndGet();
                latch.countDown();
                continue;
            }

            // Submit download task to thread pool
            final String fHash = hash;
            final String fPrefix = prefix;
            downloadExecutor.submit(() -> {
                try {
                    String assetUrl = "https://resources.download.minecraft.net/" + fPrefix + "/" + fHash;
                    File dest = new File(objectsDir, fPrefix + "/" + fHash);
                    dest.getParentFile().mkdirs();
                    
                    // Silent download (no progress spam for each file)
                    downloadFileQuiet(assetUrl, dest);
                    downloaded.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                    Log.w(TAG, "Asset fail: " + fHash);
                } finally {
                    latch.countDown();
                }            });
        }

        // Wait for all downloads to finish (with timeout)
        boolean finished = latch.await(10, TimeUnit.MINUTES);
        
        int done = downloaded.get() + skipped.get();
        listener.onStatus("📥 Assets: " + done + "/" + total + (errors.get() > 0 ? " (" + errors.get() + " failed)" : ""));
        
        if (!finished) {
            Log.e(TAG, "Asset download timed out!");
        }
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

    private void downloadFileQuiet(String url, File dest) throws Exception {
        if (dest.exists() && dest.length() > 0) return;
        Request req = new Request.Builder().url(url).addHeader("User-Agent", "FearLauncher/2.0").build();
        Response res = http.newCall(req).execute();
        if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
        try (InputStream is = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384]; int len;
            while ((len = is.read(buf)) != -1) fos.write(buffer, 0, len);
        }
    }

    private void createInstanceFolders(File instanceDir) {
        String[] folders = {"mods", "resourcepacks", "config", "saves", "screenshots", "shaderpacks", "logs"};
        for (String f : folders) new File(instanceDir, f).mkdirs();
    }
    // ... [Rest of the methods: downloadFirstLaunchFiles, etc. remain same as previous code] ...
    
    public boolean isVersionInstalled(String versionId) {
        return new File(baseDir, "instances/" + versionId + "/.instance").exists();
    }

    public boolean isFirstLaunchComplete(String versionId) {
        return new File(baseDir, "instances/" + versionId + "/.first_launch_complete").exists();
    }

    public File getInstanceDir(String versionId) { return new File(baseDir, "instances/" + versionId); }
    public File getBaseDir() { return baseDir; }
}
