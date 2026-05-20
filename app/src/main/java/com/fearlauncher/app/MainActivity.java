package com.fearlauncher.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
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

    private AnimatedBackgroundView bgAnimated;
    private ImageButton btnMenu;
    private GlassButton btnHome, btnVersions, btnPlay, btnSettings;
    private View sidePanel;
    private boolean panelOpen = false;

    private LaunchManager launchManager;
    private VersionManager versionManager;
    
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int NOTIF_PERMISSION_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        versionManager = new VersionManager(this);
        launchManager = new LaunchManager(this);

        checkPermissions();

        try {
            setContentView(R.layout.activity_main);

            bgAnimated = findViewById(R.id.bgAnimated);            btnMenu = findViewById(R.id.btnMenu);
            btnHome = findViewById(R.id.btnHome);
            btnVersions = findViewById(R.id.btnVersions);
            btnPlay = findViewById(R.id.btnPlay);
            btnSettings = findViewById(R.id.btnSettings);
            sidePanel = findViewById(R.id.sidePanel);

            if (btnMenu != null) btnMenu.setOnClickListener(v -> toggleSidePanel());
            if (btnHome != null) btnHome.setOnClickListener(v -> animateClick(v));
            if (btnVersions != null) btnVersions.setOnClickListener(v -> {
                animateClick(v);
                startActivity(new Intent(this, VersionsActivity.class));
                overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
            });
            if (btnSettings != null) btnSettings.setOnClickListener(v -> {
                animateClick(v);
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
            });
            if (btnPlay != null) {
                btnPlay.setOnClickListener(v -> {
                    animateClick(v);
                    attemptLaunch();
                });
            }

            View btnOpenVersions = findViewById(R.id.btnOpenVersions);
            if (btnOpenVersions != null) btnOpenVersions.setOnClickListener(v -> {
                animateClick(v);
                toggleSidePanel();
                startActivity(new Intent(this, VersionsActivity.class));
                overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
            });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "UI Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ Storage access granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ Storage permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void attemptLaunch() {
        String installedVersion = getFirstInstalledVersion();
        if (installedVersion == null) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ No Version Installed")
                .setMessage("Please download a Minecraft version first.")
                .setPositiveButton("Go to Versions", (d, w) -> {
                    startActivity(new Intent(this, VersionsActivity.class));
                    overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
                }).setNegativeButton("Cancel", null).show();
            return;
        }
        launchGame(installedVersion);
    }

    private String getFirstInstalledVersion() {
        File[] versionFolders = new File(versionManager.getBaseDir(), "versions").listFiles(File::isDirectory);
        if (versionFolders != null) {
            for (File f : versionFolders) {
                if (new File(f, ".installed").exists()) return f.getName();
            }
        }
        return null;
    }

    private void launchGame(String versionId) {
        File jreDir = new File(getFilesDir(), "jre-17");
        File javaBin = new File(jreDir, "bin/java");

        if (!javaBin.exists() || !javaBin.canExecute()) {            new AlertDialog.Builder(this)
                .setTitle("📦 Java Runtime Missing")
                .setMessage("Java Runtime needs to be installed. This will run in the background.\n\nCheck your notification area for progress.")
                .setPositiveButton("Start Installation", (d, w) -> {
                    startJreInstallationService();
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("🚀 Launching Minecraft")
            .setMessage("Starting " + versionId + "...")
            .setCancelable(false)
            .setNegativeButton("Cancel", (d, w) -> launchManager.stopGame())
            .show();

        // ✅ FIX 3: Implement ALL methods of LaunchListener including onJREProgress
        launchManager.launchGame(versionId, "FearPlayer", "0", "0", new LaunchManager.LaunchListener() {
            
            @Override
            public void onJREProgress(int percent) {
                // Not used here because JRE is handled by Service now, but required by interface
            }

            @Override 
            public void onLog(String line) {
                runOnUiThread(() -> progressDialog.setMessage("Starting " + versionId + "...\n\n" + line));
            }
            @Override 
            public void onLaunchSuccess() {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, "🎮 Game launched!", Toast.LENGTH_LONG).show();
                });
            }
            @Override 
            public void onLaunchError(String message) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("❌ Launch Failed")
                        .setMessage(message)
                        .setPositiveButton("OK", null).show();
                });
            }
            @Override 
            public void onExit(int exitCode) {
                runOnUiThread(() -> {                    if (exitCode != 0) progressDialog.dismiss();
                });
            }
        });
    }

    private void startJreInstallationService() {
        Intent serviceIntent = new Intent(this, JreInstallService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "📲 Installation started in background.", Toast.LENGTH_SHORT).show();
    }

    private void toggleSidePanel() {
        if (sidePanel == null) return;
        panelOpen = !panelOpen;
        sidePanel.setVisibility(panelOpen ? View.VISIBLE : View.GONE);
        sidePanel.animate().translationX(panelOpen ? 0 : -sidePanel.getWidth())
                .setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void animateClick(View v) {
        if (v == null) return;
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
    }

    @Override protected void onResume() {
        super.onResume();
        if (bgAnimated != null) bgAnimated.invalidate();
    }
}
