package com.fearlauncher.app;

import android.os.Bundle;
import android.util.Log;
import com.fearlauncher.app.adapter.VersionPagerAdapter;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
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

        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> fetchVersions());

        // Setup Tabs
        setupTabs();
        
        // Initial Fetch
        fetchVersions();
    }

    private void setupTabs() {
        // We will attach adapter after data is fetched
    }
    private void fetchVersions() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
                        .build();
                
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray versions = json.getJSONArray("versions");
                    
                    releases.clear();
                    snapshots.clear();
                    others.clear();

                    for (int i = 0; i < versions.length(); i++) {
                        JSONObject v = versions.getJSONObject(i);
                        String id = v.getString("id");
                        String type = v.getString("type");

                        if ("release".equals(type)) {
                            releases.add(id);
                        } else if ("snapshot".equals(type)) {
                            snapshots.add(id);
                        } else {
                            others.add(id); // Alpha, Beta, Pre-release
                        }
                    }
                    
                    runOnUiThread(() -> updateUI());
                }
            } catch (Exception e) {
                Log.e("Versions", "Error fetching versions", e);
            }
        }).start();
    }

    private void updateUI() {
        // Create Adapter for ViewPager2
        VersionPagerAdapter adapter = new VersionPagerAdapter(this, releases, snapshots, others);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Releases"); break;
                case 1: tab.setText("Snapshots"); break;
                case 2: tab.setText("Old/Beta"); break;            }
        }).attach();
    }
}
