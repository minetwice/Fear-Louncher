package com.fearlauncher.launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MojangAPI {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    public interface OnVersionDownloadListener {
        void onSuccess(String jsonData);
        void onError(String error);
    }

    public void fetchManifest(OnVersionDownloadListener listener) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                URL url = new URL(MANIFEST_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder jsonResponse = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    jsonResponse.append(line);
                }
                reader.close();
                connection.disconnect();

                listener.onSuccess(jsonResponse.toString());
            } catch (Exception e) {
                listener.onError(e.getMessage());
            }
        });
    }
}
