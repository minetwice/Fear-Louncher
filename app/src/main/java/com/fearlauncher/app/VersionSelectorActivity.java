package com.fearlauncher.app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.fearlauncher.app.manager.VersionManager;
import java.util.List;

public class VersionSelectorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_selector);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.select_version);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Setup ListView
        ListView versionsList = findViewById(R.id.versions_list);
        VersionManager versionManager = new VersionManager(this);

        // Get installed versions
        List<String> versions = versionManager.getInstalledVersions();

        if (versions.isEmpty()) {
            Toast.makeText(this, R.string.no_versions, Toast.LENGTH_SHORT).show();
        }

        // Set adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_list_item_1,
            versions
        );
        versionsList.setAdapter(adapter);

        // Handle version selection
        versionsList.setOnItemClickListener((parent, view, position, id) -> {
            String selectedVersion = versions.get(position);
            Toast.makeText(this, "Selected: " + selectedVersion, Toast.LENGTH_SHORT).show();
            // Here you can launch the game with the selected version
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
