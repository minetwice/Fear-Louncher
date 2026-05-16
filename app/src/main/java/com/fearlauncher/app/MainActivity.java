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
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        menuHome = findViewById(R.id.menuHome);
        menuPlay = findViewById(R.id.menuPlay);
        menuInstall = findViewById(R.id.menuInstall);
        menuMods = findViewById(R.id.menuMods);
        menuSettings = findViewById(R.id.menuSettings);
    }

    private void setupClickListeners() {
        menuHome.setOnClickListener(v -> {
            selectMenuItem(menuHome);
            Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show();
        });

        menuPlay.setOnClickListener(v -> {
            selectMenuItem(menuPlay);
            Toast.makeText(this, "Play", Toast.LENGTH_SHORT).show();
        });

        menuInstall.setOnClickListener(v -> {
            selectMenuItem(menuInstall);
            Toast.makeText(this, "Installations", Toast.LENGTH_SHORT).show();
        });

        menuMods.setOnClickListener(v -> {
            selectMenuItem(menuMods);
            Toast.makeText(this, "Mods", Toast.LENGTH_SHORT).show();
        });

        menuSettings.setOnClickListener(v -> {
            selectMenuItem(menuSettings);
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
        });
    }

    private void selectMenuItem(LinearLayout selectedMenu) {
        // Reset all menus
        resetMenu(menuHome);
        resetMenu(menuPlay);
        resetMenu(menuInstall);
        resetMenu(menuMods);
        resetMenu(menuSettings);

        // Set selected menu
        selectedMenu.setBackgroundResource(R.drawable.menu_item_bg);
        selectedMenu.setSelected(true);
        
        // Update text and icon colors
        updateMenuColors(selectedMenu, true);
    }

    private void resetMenu(LinearLayout menu) {
        menu.setBackgroundResource(android.R.color.transparent);
        menu.setSelected(false);
        updateMenuColors(menu, false);
    }

    private void updateMenuColors(LinearLayout menu, boolean isSelected) {
        int textColor = isSelected ? 
            ContextCompat.getColor(this, R.color.primary) : 
            ContextCompat.getColor(this, R.color.text_secondary);
        
        // Update TextView color
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (child instanceof android.widget.TextView) {
                ((android.widget.TextView) child).setTextColor(textColor);
            }
        }
    }
}
