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

public class MainActivity extends AppCompatActivity {

    private LinearLayout menuHome, menuPlay, menuInstall, menuMods, menuSettings, menuAccount;
    private TextView textUsername;
    private AccountManager accountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        accountManager = AccountManager.getInstance(this);
        initViews();
        setupClickListeners();
        updateUsernameDisplay();
    }

    private void initViews() {
        menuHome = findViewById(R.id.menuHome);
        menuPlay = findViewById(R.id.menuPlay);
        menuInstall = findViewById(R.id.menuInstall);
        menuMods = findViewById(R.id.menuMods);
        menuSettings = findViewById(R.id.menuSettings);
        menuAccount = findViewById(R.id.menuAccount);
        textUsername = findViewById(R.id.textUsername);
        // Note: All other preview views removed from this layout
    }

    private void setupClickListeners() {
        View.OnClickListener menuClick = v -> {
            resetMenus();
            highlightMenu((LinearLayout) v);
        };
        safeClick(menuHome, menuClick);
        safeClick(menuPlay, menuClick);
        safeClick(menuInstall, menuClick);
        safeClick(menuMods, menuClick);
        safeClick(menuSettings, menuClick);        
        safeClick(menuAccount, v -> openAccountDashboard());
        safeClick(findViewById(R.id.btnPlayNow), v -> launchMinecraft());
    }

    private void safeClick(View v, View.OnClickListener l) {
        if (v != null) v.setOnClickListener(l);
    }

    private void highlightMenu(LinearLayout selected) {
        resetMenus();
        if (selected == null) return;
        selected.setBackgroundResource(R.drawable.menu_item_bg);
        selected.setSelected(true);
        tintMenu(selected, true);
    }

    private void resetMenus() {
        LinearLayout[] menus = {menuHome, menuPlay, menuInstall, menuMods, menuSettings};
        for (LinearLayout m : menus) {
            if (m != null) {
                m.setBackgroundResource(android.R.color.transparent);
                m.setSelected(false);
                tintMenu(m, false);
            }
        }
    }

    private void tintMenu(LinearLayout menu, boolean active) {
        int color = active ? R.color.primary : R.color.text_secondary;
        for (int i = 0; i < menu.getChildCount(); i++) {
            View c = menu.getChildAt(i);
            if (c instanceof TextView) ((TextView)c).setTextColor(ContextCompat.getColor(this, color));
        }
    }

    private void openAccountDashboard() {
        try {
            startActivity(new Intent(this, AccountDashboardActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "⚠️ Dashboard failed to open. Check Logcat.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void launchMinecraft() {
        var selected = accountManager.getSelectedAccount();
        if (selected == null) {
            Toast.makeText(this, "🎮 Please select an account first", Toast.LENGTH_SHORT).show();
            openAccountDashboard();            return;
        }
        selected.setLastUsed(System.currentTimeMillis());
        accountManager.updateAccount(selected);
        Toast.makeText(this, "▶️ Launching Minecraft with: " + selected.getUsername(), Toast.LENGTH_LONG).show();
    }

    private void updateUsernameDisplay() {
        var selected = accountManager.getSelectedAccount();
        if (textUsername != null) {
            textUsername.setText(selected != null ? selected.getDisplayName() : "GuestUser");
        }
    }

    @Override protected void onResume() { super.onResume(); updateUsernameDisplay(); }
}
