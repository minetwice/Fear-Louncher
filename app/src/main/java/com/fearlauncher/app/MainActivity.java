package com.korax.launcher;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private LinearLayout menuHome, menuPlay, menuInstall, menuMods, menuSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Auto-loads correct layout (port/land)

        initViews();
        setupClickListeners();
        selectMenuItem(menuHome); // Default: Home selected
    }

    private void initViews() {
        menuHome = findViewById(R.id.menuHome);
        menuPlay = findViewById(R.id.menuPlay);
        menuInstall = findViewById(R.id.menuInstall);
        menuMods = findViewById(R.id.menuMods);
        menuSettings = findViewById(R.id.menuSettings);
    }

    private void setupClickListeners() {
        View.OnClickListener menuClick = v -> {
            resetAllMenus();
            selectMenuItem((LinearLayout) v);
            showMenuToast(v.getId());
        };

        menuHome.setOnClickListener(menuClick);
        menuPlay.setOnClickListener(menuClick);
        menuInstall.setOnClickListener(menuClick);
        menuMods.setOnClickListener(menuClick);
        menuSettings.setOnClickListener(menuClick);

        // Play Now Button
        findViewById(R.id.btnPlayNow).setOnClickListener(v -> 
            Toast.makeText(this, "🎮 Launching Minecraft...", Toast.LENGTH_SHORT).show());
    }

    private void selectMenuItem(LinearLayout selected) {
        selected.setBackgroundResource(R.drawable.menu_item_bg);
        selected.setSelected(true);
        updateMenuColors(selected, true);
    }

    private void resetAllMenus() {
        for (LinearLayout menu : new LinearLayout[]{menuHome, menuPlay, menuInstall, menuMods, menuSettings}) {
            if (menu != null) {
                menu.setBackgroundResource(android.R.color.transparent);
                menu.setSelected(false);
                updateMenuColors(menu, false);
            }
        }
    }

    private void updateMenuColors(LinearLayout menu, boolean isSelected) {
        int color = isSelected ? 
            ContextCompat.getColor(this, R.color.primary) : 
            ContextCompat.getColor(this, R.color.text_secondary);
        
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (child instanceof android.widget.TextView) {
                ((android.widget.TextView) child).setTextColor(color);
            } else if (child instanceof android.widget.ImageView) {
                ((android.widget.ImageView) child).setColorFilter(color);
            }
        }
    }

    private void showMenuToast(int viewId) {
        String name = viewId == R.id.menuHome ? "Home" :
                     viewId == R.id.menuPlay ? "Play" :
                     viewId == R.id.menuInstall ? "Installations" :
                     viewId == R.id.menuMods ? "Mods" : "Settings";
        Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
    }

    // ✅ Optional: Lock orientation agar tum chahte ho ki app rotate na ho
    // @Override
    // public void onConfigurationChanged(Configuration newConfig) {
    //     super.onConfigurationChanged(newConfig);
    //     // Handle rotation manually if needed
    // }
}
