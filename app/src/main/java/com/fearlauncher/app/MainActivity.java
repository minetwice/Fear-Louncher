package com.fearlauncher.app;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
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
        setContentView(R.layout.activity_main);

        // Start animated background
        bgAnimated = findViewById(R.id.bgAnimated);

        // Setup UI
        btnMenu = findViewById(R.id.btnMenu);
        btnHome = findViewById(R.id.btnHome);
        btnVersions = findViewById(R.id.btnVersions);
        sidePanel = findViewById(R.id.sidePanel);

        // Hamburger menu toggle
        btnMenu.setOnClickListener(v -> toggleSidePanel());

        // Bottom buttons
        btnHome.setOnClickListener(v -> {
            animateClick(v);
            Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show();
        });
        btnVersions.setOnClickListener(v -> {
            animateClick(v);
            startActivity(new Intent(this, VersionsActivity.class));
            overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
        });

        // Side panel version button
        findViewById(R.id.btnOpenVersions).setOnClickListener(v -> {
            animateClick(v);
            toggleSidePanel();
            startActivity(new Intent(this, VersionsActivity.class));
            overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
        });
    }

    private void toggleSidePanel() {
        panelOpen = !panelOpen;
        sidePanel.setVisibility(panelOpen ? View.VISIBLE : View.GONE);
        sidePanel.animate()
            .translationX(panelOpen ? 0 : -sidePanel.getWidth())
            .setDuration(300)
            .start();
    }

    private void animateClick(View v) {
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
            .start();
    }

    @Override protected void onResume() {
        super.onResume();
        // Restart background animation
        if (bgAnimated != null) bgAnimated.invalidate();
    }
}
