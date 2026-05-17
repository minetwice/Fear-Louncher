package com.fearlauncher.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.fearlauncher.app.manager.AccountManager;
import com.fearlauncher.app.model.Account;
import java.io.File;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    // Bottom Navigation Views
    private LinearLayout menuHome, menuPlay, menuInstall, menuMods, menuSettings, menuAccount;
    
    // UI Elements
    private TextView textUsername;
    private Button btnPlayNow;
    
    // Managers
    private AccountManager accountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountManager = AccountManager.getInstance(this);
        
        initViews();
        setupNavigation();
        selectMenuItem(menuHome);
        updateUI();
    }

    private void initViews() {
        menuHome = findViewById(R.id.menuHome);
        menuPlay = findViewById(R.id.menuPlay);
        menuInstall = findViewById(R.id.menuInstall);
        menuMods = findViewById(R.id.menuMods);
        menuSettings = findViewById(R.id.menuSettings);
        menuAccount = findViewById(R.id.menuAccount);
        
        textUsername = findViewById(R.id.textUsername);
        btnPlayNow = findViewById(R.id.btnPlayNow);
    }

    private void setupNavigation() {
        View.OnClickListener navClick = v -> {
            resetMenus();
            selectMenuItem((LinearLayout) v);
            handleNavClick(v.getId());
        };

        if (menuHome != null) menuHome.setOnClickListener(navClick);
        if (menuPlay != null) menuPlay.setOnClickListener(navClick);
        
        if (menuInstall != null) menuInstall.setOnClickListener(v -> {
            resetMenus();
            selectMenuItem(menuInstall);
            startActivity(new Intent(this, VersionsActivity.class));
        });
        
        if (menuMods != null) menuMods.setOnClickListener(navClick);
        if (menuSettings != null) menuSettings.setOnClickListener(navClick);
        
        if (menuAccount != null) menuAccount.setOnClickListener(v -> {
            resetMenus();
            selectMenuItem(menuAccount);
            startActivity(new Intent(this, AccountDashboardActivity.class));
        });

        if (btnPlayNow != null) {
            btnPlayNow.setOnClickListener(v -> attemptLaunchGame());
        }
    }

    private void handleNavClick(int viewId) {
        String msg = "";
        if (viewId == R.id.menuHome) msg = "Home";
        else if (viewId == R.id.menuPlay) msg = "Play";
        else if (viewId == R.id.menuMods) msg = "Mods (Coming Soon)";
        else if (viewId == R.id.menuSettings) msg = "Settings (Coming Soon)";
        if (!msg.isEmpty()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void selectMenuItem(LinearLayout selected) {
        if (selected == null) return;
        selected.setBackgroundResource(R.drawable.menu_item_glass);
        selected.setSelected(true);
        updateMenuColors(selected, true);
    }

    private void resetMenus() {
        LinearLayout[] menus = {menuHome, menuPlay, menuInstall, menuMods, menuSettings, menuAccount};
        for (LinearLayout menu : menus) {
            if (menu != null) {
                menu.setBackgroundResource(android.R.color.transparent);
                menu.setSelected(false);
                updateMenuColors(menu, false);
            }
        }
    }

    private void updateMenuColors(LinearLayout menu, boolean isSelected) {
        int color = isSelected ? R.color.primary : R.color.text_secondary;
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(ContextCompat.getColor(this, color));
            }
        }
    }

    private void updateUI() {
        Account selected = accountManager.getSelectedAccount();
        if (textUsername != null) {
            textUsername.setText(selected != null ? selected.getDisplayName() : "Guest");
        }
    }

    private void attemptLaunchGame() {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) {
            Toast.makeText(this, "⚠️ Please select an account first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountDashboardActivity.class));
            return;
        }

        // Check if any version is actually downloaded
        if (!isAnyVersionInstalled()) {
            Toast.makeText(this, "📦 No versions installed. Go to Install section to download.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, VersionsActivity.class));
            return;
        }

        // ✅ Safe Launch Trigger (Replace with real JVM/LWJGL launch later)
        Toast.makeText(this, "🚀 Launching Minecraft with: " + selected.getUsername(), Toast.LENGTH_LONG).show();
        
        // TODO: Integrate real launcher core (PojavLauncher/OpenJ9/LWJGL-Android)
    }

    private boolean isAnyVersionInstalled() {
        File versionsDir = new File(getFilesDir(), "minecraft/versions");
        if (!versionsDir.exists()) return false;
        
        File[] versionFolders = versionsDir.listFiles(File::isDirectory);
        if (versionFolders == null) return false;
        
        for (File folder : versionFolders) {
            File marker = new File(folder, ".installed");
            if (marker.exists()) return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
}
