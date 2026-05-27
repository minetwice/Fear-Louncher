package com.fearlauncher.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fearlauncher.app.manager.LaunchManager;
import com.fearlauncher.app.manager.VersionManager;
import com.fearlauncher.app.service.JreInstallService; // Ensure this import exists

public class MainActivity extends AppCompatActivity {

    private LaunchManager launchManager;
    private VersionManager versionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        versionManager = new VersionManager(this);
        launchManager = new LaunchManager(this);

        Button versionsButton = findViewById(R.id.versions_button);
        Button playButton = findViewById(R.id.play_button);

        // Go to Versions Screen
        if (versionsButton != null) {
            versionsButton.setOnClickListener(v -> {
                startActivity(new Intent(this, VersionsActivity.class));
            });
        }

        // Play Game Logic
        if (playButton != null) {
            playButton.setOnClickListener(v -> attemptLaunch());
        }
    }

    private void attemptLaunch() {
        String installedVersion = getFirstInstalledVersion();
        
        if (installedVersion == null) {
            Toast.makeText(this, "No version installed! Go to Versions.", Toast.LENGTH_SHORT).show();
            return;
        }

        launchGame(installedVersion);
    }

    private String getFirstInstalledVersion() {
        java.util.List<String> versions = versionManager.getInstalledVersions();
        if (!versions.isEmpty()) {
            return versions.get(0); // Return first found version
        }
        return null;
    }

    private void launchGame(String versionId) {
        try {
            String javaPath = launchManager.getJavaPath();
            
            // Start Game
            launchManager.launchGame(versionId, "Player", "0", "0", new LaunchManager.LaunchListener() {
                public void onLog(String l) {}
                public void onJREProgress(int p) {}
                public void onLaunchSuccess() { 
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Launched!", Toast.LENGTH_SHORT).show()); 
                }
                public void onLaunchError(String m) { 
                    runOnUiThread(() -> new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Error").setMessage(m).setPositiveButton("OK", null).show()); 
                }
                public void onExit(int c) {}
            });

        } catch (Exception e) {
            // JRE Missing? Start Service
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Java Missing")
                .setMessage("Install Java Runtime?")
                .setPositiveButton("Yes", (d,w) -> startJreService())
                .setNegativeButton("No", null)
                .show();
        }
    }

    private void startJreService() {
        Intent i = new Intent(this, JreInstallService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        Toast.makeText(this, "Installing Java...", Toast.LENGTH_SHORT).show();
    }
}
