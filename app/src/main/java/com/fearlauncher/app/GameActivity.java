package com.fearlauncher.app;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.fearlauncher.app.manager.LaunchManager;

public class GameActivity extends AppCompatActivity {
    
    private LaunchManager launchManager;
    private ImageView cursor;
    private TextView tvLog;
    private ProgressBar pbJRE; // Progress bar for JRE download
    private float dX, dY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        launchManager = new LaunchManager(this);
        String versionId = getIntent().getStringExtra("VERSION_ID");

        cursor = findViewById(R.id.cursorOverlay);
        tvLog = findViewById(R.id.tvLog);
        pbJRE = findViewById(R.id.pbJRE); // Make sure you add this to layout

        setupCursorDrag();

        launchManager.launchGame(versionId, "FearPlayer", "0", "0", new LaunchManager.LaunchListener() {
            @Override 
            public void onLog(String line) { 
                runOnUiThread(() -> tvLog.setText(line)); 
            }
            
            @Override 
            public void onJREProgress(int percent) {
                runOnUiThread(() -> {
                    pbJRE.setVisibility(View.VISIBLE);
                    pbJRE.setProgress(percent);
                    tvLog.setText("⬇️ Downloading Java Runtime: " + percent + "%");
                });
            }

            @Override 
            public void onLaunchSuccess() { 
                runOnUiThread(() -> {
                    pbJRE.setVisibility(View.GONE);
                    Toast.makeText(GameActivity.this, "🎮 Game Started!", Toast.LENGTH_SHORT).show();
                }); 
            }
            
            @Override 
            public void onLaunchError(String msg) { 
                runOnUiThread(() -> new AlertDialog.Builder(GameActivity.this)
                    .setTitle("❌ Launch Failed")
                    .setMessage(msg)
                    .setPositiveButton("OK", (d,w) -> finish())
                    .show()); 
            }
            
            @Override 
            public void onExit(int code) { 
                runOnUiThread(() -> finish()); 
            }
        });
    }

    private void setupCursorDrag() {
        cursor.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dX = v.getX() - event.getRawX();
                    dY = v.getY() - event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    v.animate().x(event.getRawX() + dX).y(event.getRawY() + dY).setDuration(0).start();
                    return true;
                case MotionEvent.ACTION_UP:
                    new AlertDialog.Builder(this)
                        .setTitle("🎮 Controls")
                        .setMessage("• Drag cursor to move\n• Tap = Left Click\n• Long Press = Right Click")
                        .setNegativeButton("Exit", (d,w) -> { launchManager.stopGame(); finish(); })
                        .show();
                    return true;
            }
            return false;
        });
    }
}
