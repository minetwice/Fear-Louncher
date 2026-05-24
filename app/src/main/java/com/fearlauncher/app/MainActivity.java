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
    
    private static final int NOTIF_PERMISSION_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Managers
        versionManager = new VersionManager(this);
        launchManager = new LaunchManager(this);

        // Check Permissions (Only Notifications for Android 13+)
        checkPermissions();

        try {
            setContentView(R.layout.activity_main);
            // Bind Views
            bgAnimated = findViewById(R.id.bgAnimated);
            btnMenu = findViewById(R.id.btnMenu);
            btnHome = findViewById(R.id.btnHome);
            btnVersions = findViewById(R.id.btnVersions);
            btnPlay = findViewById(R.id.btnPlay);
            btnSettings = findViewById(R.id.btnSettings);
            sidePanel = findViewById(R.id.sidePanel);

            // Set Click Listeners
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

            // Optional: Open Versions from Side Panel
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
        // ✅ NO STORAGE PERMISSION NEEDED (Using Scoped Storage)
                // Only request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ Notifications denied. Install progress won't show.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void attemptLaunch() {
        String installedVersion = getFirstInstalledVersion();
        
        if (installedVersion == null) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ No Version Installed")
                .setMessage("Please download a Minecraft version first from the Versions screen.")
                .setPositiveButton("Go to Versions", (d, w) -> {
                    startActivity(new Intent(this, VersionsActivity.class));
                    overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
                }).setNegativeButton("Cancel", null).show();
            return;
        }
        
        launchGame(installedVersion);
    }

    private String getFirstInstalledVersion() {
        File baseDir = versionManager.getBaseDir();
        if (baseDir == null) return null;
        
        File versionsDir = new File(baseDir, "versions");
        File[] versionFolders = versionsDir.listFiles(File::isDirectory);
        
        if (versionFolders != null) {
            for (File f : versionFolders) {
                if (new File(f, ".installed").exists()) return f.getName();
            }        }
        return null;
    }

    private void launchGame(String versionId) {
        // ✅ CHECK IF JRE EXISTS BEFORE LAUNCHING
        try {
            String javaPath = launchManager.getJavaPath();
            
            // If we got here, Java exists. Proceed to launch.
            launchManager.launchGame(versionId, "FearPlayer", "0", "0", new LaunchManager.LaunchListener() {
                
                @Override public void onJREProgress(int percent) { /* Not used here */ }
                
                @Override 
                public void onLog(String line) {
                    // Optional: Update UI with logs if needed
                }
                
                @Override 
                public void onLaunchSuccess() { 
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "🎮 Game launched successfully!", Toast.LENGTH_LONG).show()); 
                }
                
                @Override 
                public void onLaunchError(String message) { 
                    runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("❌ Launch Failed")
                        .setMessage(message)
                        .setPositiveButton("OK", null).show()); 
                }
                
                @Override 
                public void onExit(int exitCode) { 
                    // Game closed
                }
            });

        } catch (Exception e) {
            // ✅ JRE MISSING: Start Installation Service
            new AlertDialog.Builder(this)
                .setTitle("📦 Java Runtime Missing")
                .setMessage("To play Minecraft, we need to install Java Runtime Environment. This will happen in the background.\n\nCheck your notification area for progress.")
                .setPositiveButton("Install Now", (d, w) -> startJreInstallationService())
                .setNegativeButton("Cancel", null)
                .show();
        }
    }

    private void startJreInstallationService() {        Intent serviceIntent = new Intent(this, JreInstallService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "📲 Installation started. Check notifications.", Toast.LENGTH_SHORT).show();
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

    @Override 
    protected void onResume() {
        super.onResume();
        if (bgAnimated != null) bgAnimated.invalidate();
    }
}
