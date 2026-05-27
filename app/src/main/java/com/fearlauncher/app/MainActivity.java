package com.fearlauncher.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fearlauncher.app.manager.LaunchManager;
import com.fearlauncher.app.manager.VersionManager;
import com.fearlauncher.app.service.JreInstallService;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LaunchManager launchManager;
    private VersionManager versionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        versionManager = new VersionManager(this);
        launchManager = new LaunchManager(this);

        Button btnVersions = findViewById(R.id.btn_versions);
        Button btnPlay = findViewById(R.id.btn_play);

        // Handle Versions Button
        if (btnVersions != null) {
            btnVersions.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, VersionsActivity.class));
                } catch (Exception e) {
                    Toast.makeText(this, "Error opening versions", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Handle Play Button
        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> attemptLaunch());
        }
    }

    private void attemptLaunch() {
        List<String> installed = versionManager.getInstalledVersions();
        
        if (installed.isEmpty()) {
            Toast.makeText(this, "No version installed! Go to Versions.", Toast.LENGTH_LONG).show();
            return;
        }

        String lastVersion = installed.get(0); // Simple logic: take first found
        launchGame(lastVersion);
    }

    private void launchGame(String versionId) {
        try {
            String javaPath = launchManager.getJavaPath();
            
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
                .setMessage("Install Java Runtime to play?")
                .setPositiveButton("Yes", (d,w) -> startJreService())
                .setNegativeButton("No", null)
                .show();
        }
    }

    private void startJreService() {
        try {
            Intent i = new Intent(this, JreInstallService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            Toast.makeText(this, "Installing Java...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to start service", Toast.LENGTH_SHORT).show();
        }
    }
}
