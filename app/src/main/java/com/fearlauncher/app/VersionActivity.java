package com.fearlauncher.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fearlauncher.app.api.MojangAPI;
import com.fearlauncher.app.manager.VersionManager;
import java.util.ArrayList;
import java.util.List;

public class VersionsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyText;
    private VersionAdapter adapter;
    private VersionManager versionManager;
    private List<MojangAPI.VersionManifest.VersionInfo> versionList = new ArrayList<>();

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
    }

    private void loadVersions() {
        swipeRefresh.setRefreshing(true);
        MojangAPI.fetchVersions(new MojangAPI.Callback() {            @Override
            public void onSuccess(MojangAPI.VersionManifest manifest) {
                runOnUiThread(() -> {
                    versionList = manifest.versions;
                    adapter.setVersions(versionList);
                    swipeRefresh.setRefreshing(false);
                    updateEmptyState();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(VersionsActivity.this, "Failed: " + error, Toast.LENGTH_LONG).show();
                    swipeRefresh.setRefreshing(false);
                });
            }
        });
    }

    private void updateEmptyState() {
        if (versionList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // ===== ADAPTER =====
    private class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.VH> {
        private List<MojangAPI.VersionManifest.VersionInfo> versions = new ArrayList<>();

        void setVersions(List<MojangAPI.VersionManifest.VersionInfo> list) {
            versions = list;
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_version, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            MojangAPI.VersionManifest.VersionInfo v = versions.get(position);
            holder.name.setText(v.id);            holder.type.setText(v.type.toUpperCase());
            holder.date.setText(v.releaseTime.substring(0, 10));
            
            boolean installed = versionManager.isVersionInstalled(v.id);
            holder.btnDownload.setText(installed ? "Installed ✓" : "Download");
            holder.btnDownload.setEnabled(!installed);
            holder.btnDownload.setOnClickListener(installed ? null : 
                click -> downloadVersion(v, holder));

            // Show progress if downloading
            holder.progress.setVisibility(View.GONE);
        }

        private void downloadVersion(MojangAPI.VersionManifest.VersionInfo version, VH holder) {
            holder.btnDownload.setEnabled(false);
            holder.progress.setVisibility(View.VISIBLE);
            holder.progress.setProgress(0);

            versionManager.downloadVersion(version.id, version.url, new VersionManager.DownloadListener() {
                @Override
                public void onProgress(String ver, int progress, long downloaded, long total) {
                    runOnUiThread(() -> {
                        holder.progress.setProgress(progress);
                        holder.progressText.setText(progress + "%");
                    });
                }

                @Override
                public void onComplete(String ver, File path) {
                    runOnUiThread(() -> {
                        Toast.makeText(VersionsActivity.this, ver + " installed!", Toast.LENGTH_SHORT).show();
                        holder.btnDownload.setText("Installed ✓");
                        holder.progress.setVisibility(View.GONE);
                    });
                }

                @Override
                public void onError(String ver, String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(VersionsActivity.this, "Failed: " + error, Toast.LENGTH_LONG).show();
                        holder.btnDownload.setEnabled(true);
                        holder.progress.setVisibility(View.GONE);
                    });
                }
            });
        }

        @Override
        public int getItemCount() { return versions.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView name, type, date, progressText;
            Button btnDownload;
            ProgressBar progress;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.versionName);
                type = v.findViewById(R.id.versionType);
                date = v.findViewById(R.id.versionDate);
                btnDownload = v.findViewById(R.id.btnDownload);
                progress = v.findViewById(R.id.progress);
                progressText = v.findViewById(R.id.progressText);
            }
        }
    }
}
