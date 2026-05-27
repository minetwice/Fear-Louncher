package com.fearlauncher.app.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fearlauncher.app.R;
import com.fearlauncher.app.adapter.VersionListAdapter; // Ensure this adapter exists
import java.util.ArrayList;
import java.util.List;

public class VersionListFragment extends Fragment {

    private static final String ARG_VERSIONS = "versions";
    private static final String ARG_TYPE = "type";
    
    private List<String> versionList;
    private String type;

    public static VersionListFragment newInstance(List<String> versions, String type) {
        VersionListFragment fragment = new VersionListFragment();
        Bundle args = new Bundle();
        // Pass a copy of the list to avoid modification issues
        args.putStringArrayList(ARG_VERSIONS, new ArrayList<>(versions));
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            versionList = getArguments().getStringArrayList(ARG_VERSIONS);
            type = getArguments().getString(ARG_TYPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Create RecyclerView programmatically or inflate a layout containing it
        // Here we assume you might want a simple layout, but for ViewPager2, 
        // it's often easier to just return the RecyclerView directly if no other UI is needed.
        
        RecyclerView recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        // Setup Adapter
        // Make sure VersionListAdapter is in com.fearlauncher.app.adapter package
        VersionListAdapter adapter = new VersionListAdapter(versionList, type, requireContext());
        recyclerView.setAdapter(adapter);
        
        return recyclerView;
    }
}
