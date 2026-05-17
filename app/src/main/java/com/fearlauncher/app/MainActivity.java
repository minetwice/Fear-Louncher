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

public class MainActivity extends AppCompatActivity {

    // Bottom Navigation Views
    private LinearLayout menuHome, menuPlay, menuInstall, menuMods, menuSettings, menuAccount;
    
    // UI Views
    private TextView textUsername;
    private Button btnPlayNow;
    
    // Managers
    private AccountManager accountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize manager
        accountManager = AccountManager.getInstance(this);
        
        // Setup UI
        initViews();
        setupClickListeners();
        selectMenuItem(menuHome); // Default selection
        updateUsernameDisplay();
    }

    private void initViews() {
        // Bottom Navigation
        menuHome = findViewById(R.id.menuHome);
        menuPlay = findViewById(R.id.menuPlay);
        menuInstall = findViewById(R.id.menuInstall);
        menuMods = findViewById(R.id.menuMods);
        menuSettings = findViewById(R.id.menuSettings);
        menuAccount = findViewById(R.id.menuAccount);
                // Top Bar & Main Content
        textUsername = findViewById(R.id.textUsername);
        btnPlayNow = findViewById(R.id.btnPlayNow);
    }

    private void setupClickListeners() {
        // Common click handler for navigation items
        View.OnClickListener navClick = v -> {
            resetAllMenus();
            selectMenuItem((LinearLayout) v);
            handleNavigation(v.getId());
        };

        // Assign click listeners
        if (menuHome != null) menuHome.setOnClickListener(navClick);
        if (menuPlay != null) menuPlay.setOnClickListener(navClick);
        if (menuInstall != null) menuInstall.setOnClickListener(v -> {
            resetAllMenus();
            selectMenuItem(menuInstall);
            startActivity(new Intent(this, VersionsActivity.class));
        });
        if (menuMods != null) menuMods.setOnClickListener(navClick);
        if (menuSettings != null) menuSettings.setOnClickListener(navClick);
        if (menuAccount != null) menuAccount.setOnClickListener(v -> {
            resetAllMenus();
            selectMenuItem(menuAccount);
            // Open account dashboard or dialog
            showAccountOptions();
        });

        // Play Now Button
        if (btnPlayNow != null) {
            btnPlayNow.setOnClickListener(v -> launchMinecraft());
        }
    }

    private void handleNavigation(int viewId) {
        String action = "";
        if (viewId == R.id.menuHome) action = "Home";
        else if (viewId == R.id.menuPlay) action = "Play";
        else if (viewId == R.id.menuMods) action = "Mods";
        else if (viewId == R.id.menuSettings) action = "Settings";
        
        if (!action.isEmpty()) {
            Toast.makeText(this, action + " section", Toast.LENGTH_SHORT).show();
            // TODO: Implement fragment navigation or screen transitions here
        }
    }

    private void selectMenuItem(LinearLayout selected) {        if (selected == null) return;
        
        // Apply selected state with glass shine + red border
        selected.setBackgroundResource(R.drawable.menu_item_glass);
        selected.setSelected(true);
        
        // Update text/icon colors
        updateMenuColors(selected, true);
    }

    private void resetAllMenus() {
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
        int textColor = isSelected ? 
            ContextCompat.getColor(this, R.color.primary) : 
            ContextCompat.getColor(this, R.color.text_secondary);
        
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(textColor);
            }
            // If you have ImageView icons, uncomment below:
            // else if (child instanceof ImageView) {
            //     ((ImageView) child).setColorFilter(textColor);
            // }
        }
    }

    private void updateUsernameDisplay() {
        Account selected = accountManager.getSelectedAccount();
        String displayName = (selected != null) ? selected.getDisplayName() : "Guest";
        
        if (textUsername != null) {
            textUsername.setText(displayName);
        }
    }

    private void launchMinecraft() {
        Account selected = accountManager.getSelectedAccount();
                if (selected == null) {
            Toast.makeText(this, "⚠️ Please select an account first", Toast.LENGTH_LONG).show();
            showAccountOptions();
            return;
        }
        
        // Check if Minecraft version is installed
        // TODO: Integrate with VersionManager to check installed versions
        boolean isInstalled = true; // Placeholder - replace with actual check
        
        if (!isInstalled) {
            Toast.makeText(this, "📦 Minecraft not installed. Go to Install section.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, VersionsActivity.class));
            return;
        }
        
        // Update last used timestamp
        selected.setLastUsed(System.currentTimeMillis());
        accountManager.updateAccount(selected);
        
        // Launch Minecraft (placeholder - implement actual launch logic)
        Toast.makeText(this, "🎮 Launching Minecraft with: " + selected.getUsername(), Toast.LENGTH_LONG).show();
        
        // TODO: Actual launch code here:
        // 1. Get installed version path from VersionManager
        // 2. Get Java runtime path
        // 3. Build and execute Minecraft command
        // 4. Handle process output/errors
    }

    private void showAccountOptions() {
        // Simple account selection - replace with your AccountDashboardActivity
        Account selected = accountManager.getSelectedAccount();
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("👤 Account")
            .setMessage("Selected: " + (selected != null ? selected.getUsername() : "None") + 
                       "\n\nTap OK to manage accounts")
            .setPositiveButton("Manage", (d, w) -> {
                startActivity(new Intent(this, AccountDashboardActivity.class));
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh username display in case account changed
        updateUsernameDisplay();    }
}
