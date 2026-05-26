package com.fearlauncher.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.app_name);

        // Setup Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(navListener);

        // Setup Buttons
        Button playButton = findViewById(R.id.play_button);
        Button versionsButton = findViewById(R.id.versions_button);
        Button settingsButton = findViewById(R.id.settings_button);
        Button installJreButton = findViewById(R.id.install_jre_button);

        playButton.setOnClickListener(v -> launchGame());
        versionsButton.setOnClickListener(v -> startActivity(new Intent(this, VersionSelectorActivity.class)));
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        installJreButton.setOnClickListener(v -> installJRE());
    }

    private BottomNavigationView.OnNavigationItemSelectedListener navListener =
        new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.nav_home:
                        // Already in home
                        return true;
                    case R.id.nav_versions:
                        startActivity(new Intent(MainActivity.this, VersionSelectorActivity.class));
                        return true;
                    case R.id.nav_settings:
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        return true;
                }
                return false;
            }
        };

    private void launchGame() {
        // Check if JRE is installed
        try {
            new LaunchManager(this).getJavaPath();
            // Check if version is installed
            if (new VersionManager(this).isVersionInstalled("1.21.11")) {
                Toast.makeText(this, R.string.launching, Toast.LENGTH_SHORT).show();
                new LaunchManager(this).launchGame(
                    "1.21.11",
                    "PlayerName", // Replace with actual username
                    "uuid",       // Replace with actual UUID
                    "token",      // Replace with actual token
                    new LaunchManager.LaunchListener() {
                        @Override
                        public void onLog(String line) {
                            // Handle logs
                        }

                        @Override
                        public void onLaunchSuccess() {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Game Launched!", Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void onLaunchError(String message) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + message, Toast.LENGTH_LONG).show());
                        }

                        @Override
                        public void onExit(int exitCode) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Game Exited", Toast.LENGTH_SHORT).show());
                        }

                        @Override
                        public void onJREProgress(int percent) {
                            // Handle progress
                        }
                    }
                );
            } else {
                Toast.makeText(this, "Please install a version first", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "JRE not installed. Please install JRE first.", Toast.LENGTH_SHORT).show();
        }
    }

    private void installJRE() {
        Intent intent = new Intent(this, JreInstallService.class);
        startService(intent);
        Toast.makeText(this, "Installing JRE...", Toast.LENGTH_SHORT).show();
    }
}
