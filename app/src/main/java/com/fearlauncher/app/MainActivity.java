package com.fearlauncher.app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.fearlauncher.app.manager.LaunchManager;
import com.fearlauncher.app.manager.VersionManager;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        versionManager = new VersionManager(this);
        launchManager = new LaunchManager(this);

        // ✅ Modern Android doesn't need WRITE_EXTERNAL_STORAGE for app-specific dirs
        // Silently skip permission request to avoid "denied" toast
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Only request on Android 9 or lower
            requestStoragePermission();
        }

        try {
            setContentView(R.layout.activity_main);
            bgAnimated = findViewById(R.id.bgAnimated);
            btnMenu = findViewById(R.id.btnMenu);
            btnHome = findViewById(R.id.btnHome);
            btnVersions = findViewById(R.id.btnVersions);
            btnPlay = findViewById(R.id.btnPlay);
            btnSettings = findViewById(R.id.btnSettings);
            sidePanel = findViewById(R.id.sidePanel);

            if (btnMenu != null) btnMenu.setOnClickListener(v -> toggleSidePanel());            if (btnHome != null) btnHome.setOnClickListener(v -> animateClick(v));
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
            if (btnPlay != null) btnPlay.setOnClickListener(v -> {
                animateClick(v);
                attemptLaunch();
            });

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

    private void requestStoragePermission() {
        // Only for legacy Android. Modern versions use scoped storage automatically.
        Toast.makeText(this, "📂 Using app storage (no permission needed)", Toast.LENGTH_SHORT).show();
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

    private String getFirstInstalledVersion() {        File[] versionFolders = new File(versionManager.getBaseDir(), "versions").listFiles(File::isDirectory);
        if (versionFolders != null) {
            for (File f : versionFolders) {
                if (new File(f, ".installed").exists()) return f.getName();
            }
        }
        return null;
    }

    private void launchGame(String versionId) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("🚀 Launching Minecraft")
            .setMessage("Extracting JRE & Starting " + versionId + "...")
            .setCancelable(false)
            .setNegativeButton("Cancel", (d, w) -> launchManager.stopGame())
            .show();

        launchManager.launchGame(versionId, "FearPlayer", "0", "0", new LaunchManager.LaunchListener() {
            @Override public void onLog(String line) {
                runOnUiThread(() -> progressDialog.setMessage("Starting " + versionId + "...\n\n" + line));
            }
            @Override public void onLaunchSuccess() {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, "🎮 Game launched!", Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onLaunchError(String message) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("❌ Launch Failed")
                        .setMessage(message)
                        .setPositiveButton("OK", null).show();
                });
            }
            @Override public void onExit(int exitCode) {
                runOnUiThread(() -> {
                    if (exitCode != 0) progressDialog.dismiss();
                });
            }
        });
    }

    private void toggleSidePanel() {
        if (sidePanel == null) return;
        panelOpen = !panelOpen;
        sidePanel.setVisibility(panelOpen ? View.VISIBLE : View.GONE);
        sidePanel.animate().translationX(panelOpen ? 0 : -sidePanel.getWidth())
                .setDuration(300).setInterpolator(new DecelerateInterpolator()).start();    }

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
