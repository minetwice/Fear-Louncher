package com.fearlauncher.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fearlauncher.app.api.MojangAPI;
import com.fearlauncher.app.manager.VersionManager;
import com.fearlauncher.app.view.GlassButton;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VersionsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyText;
    private VersionAdapter adapter;
    private VersionManager versionManager;
    private List<MojangAPI.Manifest.Version> versions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_versions);
        versionManager = new VersionManager(this);
        initViews();
        loadVersions();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        emptyText = findViewById(R.id.emptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VersionAdapter();
        recyclerView.setAdapter(adapter);
        swipeRefresh.setOnRefreshListener(this::loadVersions);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> loadVersions());
    }

    private void loadVersions() {
        swipeRefresh.setRefreshing(true);
        MojangAPI.fetchVersions(new MojangAPI.Callback() {
            @Override public void onSuccess(MojangAPI.Manifest manifest) {
                runOnUiThread(() -> {
                    versions = manifest.versions;
                    adapter.setVersions(versions);
                    swipeRefresh.setRefreshing(false);
                    emptyText.setVisibility(versions.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> {
                    Toast.makeText(VersionsActivity.this, "Failed: " + err, Toast.LENGTH_LONG).show();
                    swipeRefresh.setRefreshing(false);
                });
            }
        });
    }

    private class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.VH> {
        private List<MojangAPI.Manifest.Version> list = new ArrayList<>();
        void setVersions(List<MojangAPI.Manifest.Version> l) { list = l; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.activity_instance_item, p, false));
        }

        @Override public void onBindViewHolder(VH h, int i) {
            MojangAPI.Manifest.Version v = list.get(i);
            h.name.setText(v.id);
            h.status.setText(v.type.toUpperCase() + (v.type.equals("release") ? " (Stable)" : ""));
            
            boolean installed = versionManager.isVersionInstalled(v.id);
            if (installed) {
                h.btnAction.setText("▶ Launch");
                h.btnAction.setOnClickListener(c -> launchInstance(v.id));
                h.icon.setImageResource(R.mipmap.ic_launcher); // Replace with version-specific icon if available
            } else {
                h.btnAction.setText("⬇ Install");
                h.btnAction.setOnClickListener(c -> downloadInstance(v, h));
                h.icon.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }

        private void downloadInstance(MojangAPI.Manifest.Version v, VH h) {
            h.btnAction.setEnabled(false);
            h.status.setText("Downloading... 0%");
            ProgressBar pb = new ProgressBar(VersionsActivity.this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8));
            ((ViewGroup)h.itemView).addView(pb);

            versionManager.downloadVersion(v.id, v.url, new VersionManager.Listener() {
                @Override public void onStatus(String msg) { runOnUiThread(() -> h.status.setText(msg)); }
                @Override public void onProgress(int p, String s) { 
                    runOnUiThread(() -> { pb.setProgress(p); h.status.setText(s + " " + p + "%"); });
                }
                @Override public void onComplete(File dir) {
                    runOnUiThread(() -> {
                        Toast.makeText(VersionsActivity.this, "✅ " + v.id + " installed!", Toast.LENGTH_SHORT).show();
                        h.btnAction.setText("▶ Launch");
                        h.btnAction.setEnabled(true);
                        h.btnAction.setOnClickListener(c -> launchInstance(v.id));
                        h.status.setText("Ready to launch");
                        h.icon.setImageResource(R.mipmap.ic_launcher);
                        ((ViewGroup)h.itemView).removeView(pb);
                    });
                }
                @Override public void onError(String e) {
                    runOnUiThread(() -> {
                        Toast.makeText(VersionsActivity.this, "❌ " + e, Toast.LENGTH_LONG).show();
                        h.btnAction.setEnabled(true);
                        h.status.setText("Failed. Tap to retry.");
                        ((ViewGroup)h.itemView).removeView(pb);
                    });
                }
            });
        }

        private void launchInstance(String versionId) {
            Intent intent = new Intent(VersionsActivity.this, GameActivity.class);
            intent.putExtra("VERSION_ID", versionId);
            startActivity(intent);
            overridePendingTransition(R.anim.bubble_enter, R.anim.bubble_exit);
        }

        @Override public int getItemCount() { return list.size(); }
        class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView name, status; GlassButton btnAction;
            VH(View v) { super(v); icon = v.findViewById(R.id.iconVersion); name = v.findViewById(R.id.versionName); status = v.findViewById(R.id.versionStatus); btnAction = v.findViewById(R.id.btnAction); }
        }
    }
}
