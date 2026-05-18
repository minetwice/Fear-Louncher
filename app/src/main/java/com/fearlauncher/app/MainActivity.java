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
import com.fearlauncher.app.view.AnimatedBackgroundView;
import com.fearlauncher.app.view.GlassButton;

// ✅ FIXED: Added missing import for File class
import java.io.File; 

public class MainActivity extends AppCompatActivity {

    private AnimatedBackgroundView bgAnimated;
    private ImageButton btnMenu;
    private GlassButton btnHome, btnVersions, btnPlay;
    private View sidePanel;
    private boolean panelOpen = false;

    private LaunchManager launchManager;
    private VersionManager versionManager;
    
    private static final int PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize managers
        versionManager = new VersionManager(this);
        launchManager = new LaunchManager(this);

        checkPermissions();

        try {
            setContentView(R.layout.activity_main);

            bgAnimated = findViewById(R.id.bgAnimated);
            btnMenu = findViewById(R.id.btnMenu);
            btnHome = findViewById(R.id.btnHome);
            btnVersions = findViewById(R.id.btnVersions);
            btnPlay = findViewById(R.id.btnPlay);
            sidePanel = findViewById(R.id.sidePanel);

            // Navigation clicks
            if (btnMenu != null) btnMenu.setOnClickListener(v -> toggleSidePanel());
            if (btnHome != null) btnHome.setOnClickListener(v -> animateClick(v));
            if (btnVersions != null) btnVersions.setOnClickListener(v -> {
                animateClick(v);
                startActivity(new Intent(this, VersionsActivity.class));
                overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
            });

            // ✅ PLAY BUTTON LOGIC
            if (btnPlay != null) {
                btnPlay.setOnClickListener(v -> {
                    animateClick(v);
                    attemptLaunch();
                });
            }

            // Side panel -> Versions
            View btnOpenVersions = findViewById(R.id.btnOpenVersions);
            if (btnOpenVersions != null) {
                btnOpenVersions.setOnClickListener(v -> {
                    animateClick(v);
                    toggleSidePanel();
                    startActivity(new Intent(this, VersionsActivity.class));
                    overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
                });
            }

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

    // ✅ REAL LAUNCH LOGIC
    private void attemptLaunch() {
        // 1. Check if any version is installed
        String installedVersion = getFirstInstalledVersion();
        if (installedVersion == null) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ No Version Installed")
                .setMessage("Please download a Minecraft version first from the Versions menu.")
                .setPositiveButton("Go to Versions", (d, w) -> {
                    startActivity(new Intent(this, VersionsActivity.class));
                    overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        // 2. Check launch readiness
        if (!launchManager.isLaunchReady(installedVersion)) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ Launch Prerequisites Missing")
                .setMessage("Java runtime or game files not found.\n\n" +
                        "To run Minecraft Java Edition on Android, you need:\n" +
                        "• ARM-compatible Java Runtime (JRE)\n" +
                        "• LWJGL3-Android port\n" +
                        "• Native libraries compiled for ARM\n\n" +
                        "Recommendation: Integrate PojavLauncher core for full support.")
                .setPositiveButton("Learn More", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/PojavLauncher/PojavLauncher")));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Try Anyway", (d, w) -> launchWithFallback(installedVersion))
                .show();
            return;
        }

        // 3. Launch the game!
        launchGame(installedVersion);
    }

    private String getFirstInstalledVersion() {
        // ✅ FIXED: Now 'File' is recognized
        File[] versionFolders = new File(versionManager.getBaseDir(), "versions").listFiles(File::isDirectory);
        if (versionFolders != null) {
            for (File f : versionFolders) {
                if (new File(f, ".installed").exists()) {
                    return f.getName();
                }
            }
        }
        return null;
    }

    private void launchGame(String versionId) {
        // Show loading dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("🚀 Launching Minecraft")
            .setMessage("Starting " + versionId + "...\n\nCheck Logcat for output.")
            .setCancelable(false)
            .setNegativeButton("Cancel", (d, w) -> {})
            .show();

        // Use offline-mode credentials for testing
        String username = "FearPlayer";
        String uuid = "00000000-0000-0000-0000-000000000000";
        String accessToken = "0";

        launchManager.launchGame(versionId, username, uuid, accessToken, new LaunchManager.LaunchListener() {
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
                        .setPositiveButton("OK", null)
                        .show();
                });
            }

            @Override
            public void onLog(String line) {
                // Stream game output to Logcat
            }
        });
    }

    private void launchWithFallback(String versionId) {
        Toast.makeText(this, "⚠️ Fallback mode: May not work without proper JVM", Toast.LENGTH_LONG).show();
        launchGame(versionId);
    }

    // UI helpers
    private void toggleSidePanel() {
        if (sidePanel == null) return;
        panelOpen = !panelOpen;
        sidePanel.setVisibility(panelOpen ? View.VISIBLE : View.GONE);
        sidePanel.animate()
                .translationX(panelOpen ? 0 : -sidePanel.getWidth())
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void animateClick(View v) {
        if (v == null) return;
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bgAnimated != null) bgAnimated.invalidate();
    }
}
