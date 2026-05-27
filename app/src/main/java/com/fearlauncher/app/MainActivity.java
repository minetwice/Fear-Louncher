package com.fearlauncher.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fearlauncher.app.manager.LaunchManager;
import com.fearlauncher.app.manager.VersionManager;
import com.fearlauncher.app.service.JreInstallService;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LaunchManager launchManager;
    private VersionManager versionManager;
    private TextView tvVersionName, tvStatus;
    private ImageButton navHome, navVersions, navSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        versionManager = new VersionManager(this);
        launchManager = new LaunchManager(this);

        // Bind Views
        navHome = findViewById(R.id.nav_home);
        navVersions = findViewById(R.id.nav_versions);
        navSettings = findViewById(R.id.nav_settings);
        Button btnPlay = findViewById(R.id.btn_play_big);
        tvVersionName = findViewById(R.id.tv_version_name);
        tvStatus = findViewById(R.id.tv_status);

        // Setup Sidebar
        setupSidebar();
        updateVersionDisplay();

        // Play Button Logic
        btnPlay.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
             .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start());
            attemptLaunch();
        });
    }

    private void setupSidebar() {        navHome.setSelected(true);
        navHome.setColorFilter(getResources().getColor(android.R.color.white));

        navVersions.setOnClickListener(v -> {
            resetSidebarSelection();
            navVersions.setSelected(true);
            navVersions.setColorFilter(getResources().getColor(android.R.color.white));
            try {
                startActivity(new Intent(this, VersionsActivity.class));
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        navSettings.setOnClickListener(v -> {
            resetSidebarSelection();
            navSettings.setSelected(true);
            navSettings.setColorFilter(getResources().getColor(android.R.color.white));
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void resetSidebarSelection() {
        navHome.setSelected(false);
        navVersions.setSelected(false);
        navSettings.setSelected(false);
        navHome.setColorFilter(getResources().getColor(android.R.color.darker_gray));
        navVersions.setColorFilter(getResources().getColor(android.R.color.darker_gray));
        navSettings.setColorFilter(getResources().getColor(android.R.color.darker_gray));
    }

    private void updateVersionDisplay() {
        List<String> installed = versionManager.getInstalledVersions();
        if (!installed.isEmpty()) {
            tvVersionName.setText(installed.get(0));
            tvStatus.setText("Ready to play");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            tvVersionName.setText("No version installed");
            tvStatus.setText("Please install a version");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        }
    }

    private void attemptLaunch() {
        List<String> installed = versionManager.getInstalledVersions();
        if (installed.isEmpty()) {
            Toast.makeText(this, "Please install a version first!", Toast.LENGTH_LONG).show();
            return;
        }        launchGame(installed.get(0));
    }

    private void launchGame(String versionId) {
        try {
            String javaPath = launchManager.getJavaPath();
            launchManager.launchGame(versionId, "Player", "0", "0", new LaunchManager.LaunchListener() {
                public void onLog(String l) {}
                public void onJREProgress(int p) {}
                public void onLaunchSuccess() { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Launched!", Toast.LENGTH_SHORT).show()); }
                public void onLaunchError(String m) { runOnUiThread(() -> new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this).setTitle("Error").setMessage(m).setPositiveButton("OK", null).show()); }
                public void onExit(int c) {}
            });
        } catch (Exception e) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Java Missing")
                .setMessage("Install Java Runtime?")
                .setPositiveButton("Yes", (d,w) -> startJreService())
                .setNegativeButton("No", null).show();
        }
    }

    private void startJreService() {
        try {
            Intent i = new Intent(this, JreInstallService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
            Toast.makeText(this, "Installing Java...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Service Error", Toast.LENGTH_SHORT).show();
        }
    }
}
