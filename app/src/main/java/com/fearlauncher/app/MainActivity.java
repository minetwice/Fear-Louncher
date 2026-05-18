package com.fearlauncher.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fearlauncher.app.view.AnimatedBackgroundView;
import com.fearlauncher.app.view.GlassButton;

public class MainActivity extends AppCompatActivity {

    private AnimatedBackgroundView bgAnimated;
    private ImageButton btnMenu;
    private GlassButton btnHome, btnVersions;
    private View sidePanel;
    private boolean panelOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            bgAnimated = findViewById(R.id.bgAnimated);
            btnMenu = findViewById(R.id.btnMenu);
            btnHome = findViewById(R.id.btnHome);
            btnVersions = findViewById(R.id.btnVersions);
            sidePanel = findViewById(R.id.sidePanel);

            if (btnMenu != null) btnMenu.setOnClickListener(v -> toggleSidePanel());
            if (btnHome != null) btnHome.setOnClickListener(v -> animateClick(v));
            if (btnVersions != null) btnVersions.setOnClickListener(v -> {
                animateClick(v);
                startActivity(new Intent(this, VersionsActivity.class));
                overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
            });

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
            Toast.makeText(this, "UI Init Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void toggleSidePanel() {
        if (sidePanel == null) return;
        panelOpen = !panelOpen;
        sidePanel.setVisibility(panelOpen ? View.VISIBLE : View.GONE);
        sidePanel.animate()
            .translationX(panelOpen ? 0 : -sidePanel.getWidth())
            .setDuration(300)
            .start();
    }

    private void animateClick(View v) {
        if (v == null) return;
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
            .start();
    }

    @Override protected void onResume() {
        super.onResume();
        if (bgAnimated != null) bgAnimated.invalidate();
    }
}
