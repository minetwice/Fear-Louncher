package com.fearlauncher.app.api;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MojangAPI {
    private static final String VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    public static class Manifest {
        public Latest latest;
        public List<Version> versions;
        public static class Latest {
            public String release;
            public String snapshot;
        }
        public static class Version {
            public String id;
            public String type;
            public String url;
            public String time;
            public String releaseTime;
        }
    }

    public interface Callback {
        void onSuccess(Manifest manifest);
        void onError(String error);
    }

    public static void fetchVersions(Callback cb) {
        new Thread(() -> {
            try {
                Request req = new Request.Builder().url(VERSION_MANIFEST).build();
                Response res = client.newCall(req).execute();
                if (!res.isSuccessful()) { cb.onError("HTTP " + res.code()); return; }
                Manifest manifest = new Gson().fromJson(res.body().string(), Manifest.class);
                cb.onSuccess(manifest);
            } catch (Exception e) { cb.onError(e.getMessage()); }
        }).start();
    }
}
