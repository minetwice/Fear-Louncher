package com.fearlauncher.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.fearlauncher.app.view.GlassButton;

public class SettingsActivity extends AppCompatActivity {

    private RadioGroup javaVersionGroup;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        javaVersionGroup = findViewById(R.id.javaVersionGroup);
        GlassButton btnSave = findViewById(R.id.btnSave);
        GlassButton btnBack = findViewById(R.id.btnBack);

        // Load saved preference
        String savedVersion = prefs.getString("java_version", "jre-17");
        selectJavaVersion(savedVersion);

        // Save button
        btnSave.setOnClickListener(v -> saveSettings());

        // Back button
        btnBack.setOnClickListener(v -> finish());
    }

    private void selectJavaVersion(String version) {
        int radioButtonId = switch (version) {
            case "jre-8" -> R.id.radioJava8;
            case "jre-17" -> R.id.radioJava17;
            case "jre-21" -> R.id.radioJava21;
            case "jre-25" -> R.id.radioJava25;
            default -> R.id.radioJava17;
        };
        RadioButton rb = findViewById(radioButtonId);
        if (rb != null) rb.setChecked(true);
    }

    private void saveSettings() {
        int selectedId = javaVersionGroup.getCheckedRadioButtonId();
        if (selectedId == View.NO_ID) {
            Toast.makeText(this, "⚠️ Please select a Java version", Toast.LENGTH_SHORT).show();
            return;
        }

        String version = switch (selectedId) {
            case R.id.radioJava8 -> "jre-8";
            case R.id.radioJava17 -> "jre-17";
            case R.id.radioJava21 -> "jre-21";
            case R.id.radioJava25 -> "jre-25";
            default -> "jre-17";
        };

        prefs.edit().putString("java_version", version).apply();
        
        Toast.makeText(this, "✅ Java " + version.replace("jre-", "") + " selected!", Toast.LENGTH_SHORT).show();
        finish();
    }
              }
