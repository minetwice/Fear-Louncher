package com.fearlauncher.app.api;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MojangAPI {

    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    public static class VersionManifest {
        public Latest latest;
        public List<VersionInfo> versions;

        public static class Latest {
            public String release;
            public String snapshot;
        }

        public static class VersionInfo {
            public String id;
            public String type; // "release", "snapshot", "old_beta", etc.
            public String url;
            public String time;
            public String releaseTime;
        }
    }

    public interface Callback {
        void onSuccess(VersionManifest manifest);
        void onError(String error);
    }

    public static void fetchVersions(Callback callback) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder().url(VERSION_MANIFEST_URL).build();
                Response response = client.newCall(request).execute();
                
                if (!response.isSuccessful()) {
                    callback.onError("HTTP " + response.code());
                    return;
                }

                String json = response.body().string();
                VersionManifest manifest = new Gson().fromJson(json, VersionManifest.class);
                callback.onSuccess(manifest);
                
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public static void fetchVersionDetails(String versionUrl, Callback callback) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder().url(versionUrl).build();
                Response response = client.newCall(request).execute();
                
                if (!response.isSuccessful()) {
                    callback.onError("HTTP " + response.code());
                    return;
                }

                String json = response.body().string();
                // Return raw JSON for now (can parse further if needed)
                callback.onSuccess(new VersionManifest() {{
                    versions = java.util.Collections.singletonList(new VersionInfo() {{
                        id = "details_loaded";
                    }});
                }});
                
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
