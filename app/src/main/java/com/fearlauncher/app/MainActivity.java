package com.fearlauncher.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.fearlauncher.app.manager.AccountManager;
import com.fearlauncher.app.model.Account;

public class MainActivity extends AppCompatActivity {

    private AccountManager accountManager;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ Fullscreen + Immersive Mode
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_main);

        accountManager = AccountManager.getInstance(this);
        statusText = findViewById(R.id.statusText);

        // Background logic (no buttons)
        initMinimalLauncher();
    }

    private void initMinimalLauncher() {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) {
            statusText.setText("⚠️ No account");
            // Auto-redirect after 2s if needed
            // runOnUiThread(() -> startActivity(new Intent(this, AccountDashboardActivity.class)));
            return;
        }
        statusText.setText("✅ " + selected.getUsername());
        // Auto-check installed versions, prepare JVM, etc.
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
