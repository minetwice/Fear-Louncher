package com.fearlauncher.app.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinecraftModelParser {
    
    public static class Cube {
        public float ox, oy, oz; // origin
        public float sx, sy, sz; // size
        public int u, v;         // UV coordinates
        public float inflate;    // layer offset (0.25 for jackets, etc.)
        public boolean mirror;
        
        public Cube(float ox, float oy, float oz, float sx, float sy, float sz, int u, int v, float inflate, boolean mirror) {
            this.ox = ox; this.oy = oy; this.oz = oz;
            this.sx = sx; this.sy = sy; this.sz = sz;
            this.u = u; this.v = v;
            this.inflate = inflate;
            this.mirror = mirror;
        }
    }
    
    public static class Bone {
        public String name;
        public String parent;
        public float px, py, pz; // pivot
        public float rx, ry, rz; // rotation
        public List<Cube> cubes = new ArrayList<>();
        public List<Bone> children = new ArrayList<>();
        
        public Bone(String name, String parent, float px, float py, float pz) {
            this.name = name;
            this.parent = parent;
            this.px = px; this.py = py; this.pz = pz;
        }
    }
    
    public static class Model {
        public String identifier;
        public int textureWidth = 64;
        public int textureHeight = 64;
        public Map<String, Bone> bones = new HashMap<>();
        public Bone root;
    }
    
    // ✅ Parse official Minecraft geometry JSON
    public static Model parse(String json, String modelIdentifier) {
        try {
            JSONObject rootJson = new JSONObject(json);
            JSONArray geometries = rootJson.getJSONArray("minecraft:geometry");
            
            Model model = new Model();
            model.identifier = modelIdentifier;
            
            // Find the right geometry
            for (int i = 0; i < geometries.length(); i++) {
                JSONObject geo = geometries.getJSONObject(i);
                JSONObject desc = geo.getJSONObject("description");
                
                if (modelIdentifier.equals(desc.getString("identifier"))) {
                    model.textureWidth = desc.optInt("texture_width", 64);
                    model.textureHeight = desc.optInt("texture_height", 64);
                    
                    JSONArray bonesJson = geo.getJSONArray("bones");
                    
                    // First pass: create all bones
                    for (int j = 0; j < bonesJson.length(); j++) {
                        JSONObject boneJson = bonesJson.getJSONObject(j);
                        String name = boneJson.getString("name");
                        String parent = boneJson.optString("parent", null);
                        
                        JSONObject pivot = boneJson.optJSONObject("pivot");
                        float px = pivot != null ? (float) pivot.optDouble("x", 0) : 0;
                        float py = pivot != null ? (float) pivot.optDouble("y", 0) : 0;
                        float pz = pivot != null ? (float) pivot.optDouble("z", 0) : 0;
                        
                        Bone bone = new Bone(name, parent, px, py, pz);
                        
                        // Parse rotation
                        JSONObject rot = boneJson.optJSONObject("rotation");
                        if (rot != null) {
                            bone.rx = (float) rot.optDouble("x", 0);
                            bone.ry = (float) rot.optDouble("y", 0);
                            bone.rz = (float) rot.optDouble("z", 0);
                        }
                        
                        // Parse cubes
                        if (boneJson.has("cubes")) {
                            JSONArray cubesJson = boneJson.getJSONArray("cubes");
                            for (int k = 0; k < cubesJson.length(); k++) {
                                JSONObject cubeJson = cubesJson.getJSONObject(k);
                                JSONObject origin = cubeJson.getJSONObject("origin");
                                JSONObject size = cubeJson.getJSONObject("size");
                                JSONObject uv = cubeJson.getJSONObject("uv");
                                
                                Cube cube = new Cube(
                                    (float) origin.optDouble("x"),
                                    (float) origin.optDouble("y"),
                                    (float) origin.optDouble("z"),
                                    (float) size.optDouble("x"),
                                    (float) size.optDouble("y"),
                                    (float) size.optDouble("z"),
                                    uv.optInt("u"),
                                    uv.optInt("v"),
                                    (float) cubeJson.optDouble("inflate", 0),
                                    cubeJson.optBoolean("mirror", false)
                                );
                                bone.cubes.add(cube);
                            }
                        }
                        
                        model.bones.put(name, bone);
                    }
                    
                    // Second pass: build hierarchy
                    for (Bone bone : model.bones.values()) {
                        if (bone.parent == null || "root".equals(bone.parent)) {
                            model.root = bone;
                        } else {
                            Bone parentBone = model.bones.get(bone.parent);
                            if (parentBone != null) {
                                parentBone.children.add(bone);
                            }
                        }
                    }
                    break;
                }
            }
            return model;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
