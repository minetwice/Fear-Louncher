package com.fearlauncher.app.adapter; // ✅ CHECK THIS LINE

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.fearlauncher.app.fragment.VersionListFragment; // ✅ Ensure this fragment exists too
import java.util.List;

public class VersionPagerAdapter extends FragmentStateAdapter {

    private final List<String> releases;
    private final List<String> snapshots;
    private final List<String> others;

    public VersionPagerAdapter(@NonNull FragmentActivity activity, 
                               List<String> releases, 
                               List<String> snapshots, 
                               List<String> others) {
        super(activity);
        this.releases = releases;
        this.snapshots = snapshots;
        this.others = others;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return VersionListFragment.newInstance(releases, "Release");
            case 1:
                return VersionListFragment.newInstance(snapshots, "Snapshot");
            case 2:
                return VersionListFragment.newInstance(others, "Old/Beta");
            default:
                return VersionListFragment.newInstance(releases, "Release");
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
