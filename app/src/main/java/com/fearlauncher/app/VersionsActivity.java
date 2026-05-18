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
                    updateEmpty();
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

    private void updateEmpty() {
        emptyText.setVisibility(versions.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(versions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ===== Adapter =====
    private class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.VH> {
        private List<MojangAPI.Manifest.Version> list = new ArrayList<>();
        void setVersions(List<MojangAPI.Manifest.Version> l) { list = l; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_version, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int i) {
            MojangAPI.Manifest.Version v = list.get(i);
            h.name.setText(v.id);
            h.type.setText(v.type.toUpperCase());
            h.date.setText(v.releaseTime.substring(0, 10));
            
            boolean installed = versionManager.isInstalled(v.id);
            h.btnDownload.setText(installed ? "Installed" : "Download");
            h.btnDownload.setEnabled(!installed);
            h.progress.setVisibility(View.GONE);
            h.progressText.setVisibility(View.GONE);

            if (!installed) {
                h.btnDownload.setOnClickListener(c -> download(v, h));
            }        }

        private void download(MojangAPI.Manifest.Version v, VH h) {
            h.btnDownload.setEnabled(false);
            h.progress.setVisibility(View.VISIBLE);
            h.progressText.setVisibility(View.VISIBLE);
            
            versionManager.download(v.id, v.url, new VersionManager.Listener() {
                @Override public void progress(int p) {
                    runOnUiThread(() -> {
                        h.progress.setProgress(p);
                        h.progressText.setText(p + "%");
                    });
                }
                @Override public void done(File dir) {
                    runOnUiThread(() -> {
                        Toast.makeText(VersionsActivity.this, v.id + " installed!", Toast.LENGTH_SHORT).show();
                        h.btnDownload.setText("Installed");
                        h.progress.setVisibility(View.GONE);
                        h.progressText.setVisibility(View.GONE);
                    });
                }
                @Override public void error(String e) {
                    runOnUiThread(() -> {
                        Toast.makeText(VersionsActivity.this, "Failed: " + e, Toast.LENGTH_LONG).show();
                        h.btnDownload.setEnabled(true);
                        h.progress.setVisibility(View.GONE);
                        h.progressText.setVisibility(View.GONE);
                    });
                }
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, type, date, progressText;
            GlassButton btnDownload;
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
    }}
