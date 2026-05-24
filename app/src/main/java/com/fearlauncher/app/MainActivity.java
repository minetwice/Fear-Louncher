package com.fearlauncher.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.fearlauncher.app.manager.LaunchManager;
import com.fearlauncher.app.manager.VersionManager;
import com.fearlauncher.app.service.JreInstallService;
import com.fearlauncher.app.view.AnimatedBackgroundView;
import com.fearlauncher.app.view.GlassButton;
import java.io.File;

public class MainActivity extends AppCompatActivity {
    private LaunchManager launchManager;
    private VersionManager versionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        versionManager = new VersionManager(this);
        launchManager = new LaunchManager(this);

        // ✅ ONLY REQUEST NOTIFICATION PERMISSION (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
            }
        }

        setupUI();
    }

    private void setupUI() {
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        GlassButton btnPlay = findViewById(R.id.btnPlay);
        GlassButton btnVersions = findViewById(R.id.btnVersions);
        
        if (btnPlay != null) btnPlay.setOnClickListener(v -> attemptLaunch());        if (btnVersions != null) btnVersions.setOnClickListener(v -> startActivity(new Intent(this, VersionsActivity.class)));
        if (btnMenu != null) btnMenu.setOnClickListener(v -> Toast.makeText(this, "Menu clicked", Toast.LENGTH_SHORT).show());
    }

    private void attemptLaunch() {
        String ver = getFirstInstalledVersion();
        if (ver == null) {
            new AlertDialog.Builder(this).setTitle("No Version")
                .setMessage("Download a version first.").setPositiveButton("OK", null).show();
            return;
        }
        launchGame(ver);
    }

    private String getFirstInstalledVersion() {
        File[] dirs = new File(versionManager.getBaseDir(), "versions").listFiles(File::isDirectory);
        if (dirs != null) for (File f : dirs) if (new File(f, ".installed").exists()) return f.getName();
        return null;
    }

    private void launchGame(String versionId) {
        File jreBin = new File(getFilesDir(), "jre-17/bin/java");
        
        // ✅ CHECK IF JRE EXISTS. IF NOT, START SERVICE.
        if (!jreBin.exists() || !jreBin.canExecute()) {
            new AlertDialog.Builder(this)
                .setTitle("Java Missing")
                .setMessage("Java Runtime needs to be installed. Check notifications.")
                .setPositiveButton("Install", (d,w) -> startJreService())
                .setNegativeButton("Cancel", null).show();
            return;
        }

        // JRE Exists, Launch Game
        launchManager.launchGame(versionId, "Player", "0", "0", new LaunchManager.LaunchListener() {
            public void onLog(String l) {}
            public void onJREProgress(int p) {}
            public void onLaunchSuccess() { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Launched!", Toast.LENGTH_SHORT).show()); }
            public void onLaunchError(String m) { runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("Error").setMessage(m).setPositiveButton("OK", null).show()); }
            public void onExit(int c) {}
        });
    }

    private void startJreService() {
        Intent i = new Intent(this, JreInstallService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        Toast.makeText(this, "Installing Java...", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
    }
}
