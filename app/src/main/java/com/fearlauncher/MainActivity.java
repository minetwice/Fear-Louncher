package com.fearlauncher.launcher;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Spinner versionSpinner;
    private Button playButton;
    private MojangAPI mojangAPI;
    private VersionParser versionParser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Link XML to Java
        versionSpinner = findViewById(R.id.versionSpinner);
        playButton = findViewById(R.id.playButton);
        
        mojangAPI = new MojangAPI();
        versionParser = new VersionParser();

        // 2. Add temporary loading text to Spinner
        String[] loadingText = {"Loading versions..."};
        ArrayAdapter<String> loadingAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, loadingText);
        versionSpinner.setAdapter(loadingAdapter);

        // 3. Fetch data from Mojang
        fetchMinecraftVersions();

        // 4. Set Play Button Click Action
        playButton.setOnClickListener(v -> {
            String selectedVersion = versionSpinner.getSelectedItem().toString();
            Toast.makeText(MainActivity.this, "Preparing to download: " + selectedVersion, Toast.LENGTH_LONG).show();
            // Future Stage: Trigger the asset downloader here!
        });
    }

    private void fetchMinecraftVersions() {
        mojangAPI.fetchManifest(new MojangAPI.OnVersionDownloadListener() {
            @Override
            public void onSuccess(String jsonData) {
                // Parse the downloaded data
                versionParser.parseVersions(jsonData);

                // Update the UI on the Main Thread (CRITICAL)
                runOnUiThread(() -> {
                    // Load the 'releases' list into the Spinner dropdown
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            MainActivity.this, 
                            android.R.layout.simple_spinner_dropdown_item, 
                            versionParser.releases
                    );
                    versionSpinner.setAdapter(adapter);
                    Toast.makeText(MainActivity.this, "Versions Loaded!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> 
                    Toast.makeText(MainActivity.this, "Failed to connect: " + error, Toast.LENGTH_LONG).show()
                );
            }
        });
    }
}
