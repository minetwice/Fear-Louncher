package com.fearlauncher.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fearlauncher.app.R;
import com.fearlauncher.app.manager.VersionManager;
import java.util.List;

public class VersionListAdapter extends RecyclerView.Adapter<VersionListAdapter.ViewHolder> {

    private final List<String> versions;
    private final String type;
    private final Context context;

    public VersionListAdapter(List<String> versions, String type, Context context) {
        this.versions = versions;
        this.type = type;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure item_version_slim.xml exists in res/layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version_slim, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String versionId = versions.get(position);
        holder.tvName.setText(versionId);
        holder.tvType.setText(type);

        // Check if already installed using VersionManager
        VersionManager vm = new VersionManager(context);
        boolean isInstalled = vm.isVersionInstalled(versionId);

        if (isInstalled) {
            holder.btnAction.setText("Play");
            holder.btnAction.setOnClickListener(v -> 
                Toast.makeText(context, "Launching " + versionId + "...", Toast.LENGTH_SHORT).show()
            );
        } else {
            holder.btnAction.setText("Download");
            holder.btnAction.setOnClickListener(v -> 
                Toast.makeText(context, "Downloading " + versionId + "...", Toast.LENGTH_SHORT).show()
            );
        }
    }

    @Override
    public int getItemCount() {
        return versions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvType;
        Button btnAction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.versionName);
            tvType = itemView.findViewById(R.id.versionType);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }
}
