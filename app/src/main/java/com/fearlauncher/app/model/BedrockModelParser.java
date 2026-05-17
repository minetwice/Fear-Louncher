package com.fearlauncher.app.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class BedrockModelParser {

    public static class FaceUV {
        public int u, v, width, height;
        public FaceUV(int u, int v, int w, int h) {
            this.u = u; this.v = v; this.width = w; this.height = h;
        }
    }

    public static class Cube {
        public String name;
        public float ox, oy, oz; // origin
        public float sx, sy, sz; // size
        public float inflate;
        public boolean mirror;
        public Map<String, FaceUV> faces = new HashMap<>(); // north, east, south, west, up, down

        public Cube(String name, float ox, float oy, float oz, float sx, float sy, float sz, float inflate, boolean mirror) {
            this.name = name; this.ox = ox; this.oy = oy; this.oz = oz;
            this.sx = sx; this.sy = sy; this.sz = sz;
            this.inflate = inflate; this.mirror = mirror;
        }

        public void addFace(String dir, int u, int v, int w, int h) {
            faces.put(dir, new FaceUV(u, v, w, h));
        }
    }

    public static class Bone {
        public String name;
        public String parent;
        public float px, py, pz; // pivot
        public List<Cube> cubes = new ArrayList<>();
        public List<Bone> children = new ArrayList<>();

        public Bone(String name, String parent, float px, float py, float pz) {
            this.name = name; this.parent = parent;
            this.px = px; this.py = py; this.pz = pz;
        }
    }

    public static class Model {
        public String identifier;
        public int texWidth = 64, texHeight = 64;
        public Map<String, Bone> bones = new HashMap<>();
        public Bone root;
    }

    // ✅ Parse Bedrock geometry JSON
    public static Model parse(String json) {
        try {
            JSONObject rootJson = new JSONObject(json);
            JSONArray geometries = rootJson.getJSONArray("minecraft:geometry");
            if (geometries.length() == 0) return null;

            JSONObject geo = geometries.getJSONObject(0);
            JSONObject desc = geo.getJSONObject("description");
            Model model = new Model();
            model.identifier = desc.getString("identifier");
            model.texWidth = desc.optInt("texture_width", 64);
            model.texHeight = desc.optInt("texture_height", 64);

            JSONArray bonesJson = geo.getJSONArray("bones");

            // First pass: create bones
            for (int i = 0; i < bonesJson.length(); i++) {
                JSONObject b = bonesJson.getJSONObject(i);
                String name = b.getString("name");
                String parent = b.optString("parent", null);
                JSONObject pivot = b.optJSONObject("pivot");
                float px = pivot != null ? (float) pivot.optDouble("x", 0) : 0;
                float py = pivot != null ? (float) pivot.optDouble("y", 0) : 0;
                float pz = pivot != null ? (float) pivot.optDouble("z", 0) : 0;
                model.bones.put(name, new Bone(name, parent, px, py, pz));
            }

            // Second pass: add cubes & build hierarchy
            for (int i = 0; i < bonesJson.length(); i++) {
                JSONObject b = bonesJson.getJSONObject(i);
                String name = b.getString("name");
                Bone bone = model.bones.get(name);
                if (bone == null) continue;

                // Parse cubes
                if (b.has("cubes")) {
                    JSONArray cubes = b.getJSONArray("cubes");
                    for (int j = 0; j < cubes.length(); j++) {
                        JSONObject c = cubes.getJSONObject(j);
                        JSONObject origin = c.getJSONObject("origin");
                        JSONObject size = c.getJSONObject("size");
                        float inflate = (float) c.optDouble("inflate", 0);
                        boolean mirror = b.optBoolean("mirror", false);

                        Cube cube = new Cube(name + "_cube" + j,
                            (float) origin.optDouble("x"),
                            (float) origin.optDouble("y"),
                            (float) origin.optDouble("z"),
                            (float) size.optDouble("x"),
                            (float) size.optDouble("y"),
                            (float) size.optDouble("z"),
                            inflate, mirror);

                        // Parse per-face UVs
                        if (c.has("uv")) {
                            JSONObject uv = c.getJSONObject("uv");
                            for (String dir : new String[]{"north", "east", "south", "west", "up", "down"}) {
                                if (uv.has(dir)) {
                                    JSONObject face = uv.getJSONObject(dir);
                                    JSONArray uvArr = face.getJSONArray("uv");
                                    JSONArray sizeArr = face.getJSONArray("uv_size");
                                    cube.addFace(dir,
                                        uvArr.optInt(0), uvArr.optInt(1),
                                        Math.abs(sizeArr.optInt(0)), Math.abs(sizeArr.optInt(1)));
                                }
                            }
                        }
                        bone.cubes.add(cube);
                    }
                }

                // Build parent-child links
                if (bone.parent != null && model.bones.containsKey(bone.parent)) {
                    model.bones.get(bone.parent).children.add(bone);
                } else if (bone.parent == null || "root".equals(bone.parent)) {
                    model.root = bone;
                }
            }
            return model;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
