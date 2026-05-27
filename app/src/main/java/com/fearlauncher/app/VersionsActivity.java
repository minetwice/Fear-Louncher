package com.fearlauncher.app;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.fearlauncher.app.adapter.VersionPagerAdapter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class VersionsActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private List<String> releases = new ArrayList<>();
    private List<String> snapshots = new ArrayList<>();
    private List<String> others = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_versions);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        
        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> fetchVersions());

        fetchVersions();
    }

    private void fetchVersions() {
        // Show loading state if needed
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
                        .build();
                
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray versions = json.getJSONArray("versions");
                    
                    releases.clear();
                    snapshots.clear();
                    others.clear();

                    for (int i = 0; i < versions.length(); i++) {
                        JSONObject v = versions.getJSONObject(i);
                        String id = v.getString("id");
                        String type = v.getString("type");

                        if ("release".equals(type)) releases.add(id);
                        else if ("snapshot".equals(type)) snapshots.add(id);
                        else others.add(id);
                    }
                    
                    runOnUiThread(this::updateUI);
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to fetch versions", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e("Versions", "Error", e);
                runOnUiThread(() -> Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateUI() {
        if (isFinishing()) return;

        VersionPagerAdapter adapter = new VersionPagerAdapter(this, releases, snapshots, others);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("RELEASES"); break;
                case 1: tab.setText("SNAPSHOTS"); break;
                case 2: tab.setText("LEGACY"); break;
            }
        }).attach();
    }
}
