package com.yourname.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class VersionParser {
    public List<String> releases = new ArrayList<>();
    public List<String> snapshots = new ArrayList<>();

    public void parseVersions(String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);
            JSONArray versions = root.getJSONArray("versions");

            for (int i = 0; i < versions.length(); i++) {
                JSONObject versionObj = versions.getJSONObject(i);
                String id = versionObj.getString("id");
                String type = versionObj.getString("type");

                if (type.equals("release")) {
                    releases.add(id);
                } else if (type.equals("snapshot")) {
                    snapshots.add(id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
