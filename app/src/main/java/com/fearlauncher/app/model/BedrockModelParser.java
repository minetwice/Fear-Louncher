package com.fearlauncher.app.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class BedrockModelParser {

    // ===== UV Mapping =====
    public static class FaceUV {
        public int u, v, w, h;
        public FaceUV(int u, int v, int w, int h) {
            this.u = u; this.v = v; this.w = w; this.h = h;
        }
    }

    // ===== Cube Definition =====
    public static class Cube {
        public float ox, oy, oz;
        public float sx, sy, sz;
        public float inflate;
        public Map<String, FaceUV> faces = new HashMap<>();
    }

    // ===== Bone Definition =====
    public static class Bone {
        public String name;
        public String parent;
        public float px, py, pz;
        public boolean mirror = false;
        public List<Cube> cubes = new ArrayList<>();
        public List<Bone> children = new ArrayList<>();

        public Bone(String name, String parent, float px, float py, float pz) {
            this.name = name;
            this.parent = parent;
            this.px = px; this.py = py; this.pz = pz;
        }
    }

    // ===== Model Container =====
    public static class Model {
        public String identifier;
        public Bone root;
        public Map<String, Bone> bones = new HashMap<>();
    }

    // ===== Helper: Get value ignoring trailing spaces in keys =====
    private static Object getRawValue(JSONObject obj, String targetKey) {
        try {            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key != null && key.trim().equals(targetKey)) {
                    return obj.get(key);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String getStr(JSONObject obj, String key) {
        Object val = getRawValue(obj, key);
        return (val instanceof String) ? (String) val : null;
    }

    private static int getInt(JSONObject obj, String key, int def) {
        Object val = getRawValue(obj, key);
        return (val instanceof Number) ? ((Number) val).intValue() : def;
    }

    private static double getDouble(JSONObject obj, String key, double def) {
        Object val = getRawValue(obj, key);
        return (val instanceof Number) ? ((Number) val).doubleValue() : def;
    }

    private static boolean getBool(JSONObject obj, String key, boolean def) {
        Object val = getRawValue(obj, key);
        return (val instanceof Boolean) ? (Boolean) val : def;
    }

    private static JSONObject getObj(JSONObject obj, String key) {
        Object val = getRawValue(obj, key);
        return (val instanceof JSONObject) ? (JSONObject) val : null;
    }

    private static JSONArray getArr(JSONObject obj, String key) {
        Object val = getRawValue(obj, key);
        return (val instanceof JSONArray) ? (JSONArray) val : null;
    }

    // ===== MAIN PARSE FUNCTION =====
    public static Model parse(String json) {
        Model model = null;
        try {
            JSONObject rootJson = new JSONObject(json);
            JSONArray geometries = getArr(rootJson, "minecraft:geometry");
            if (geometries == null || geometries.length() == 0) return null;

            JSONObject geo = geometries.getJSONObject(0);            JSONObject desc = getObj(geo, "description");
            model = new Model();
            model.identifier = getStr(desc, "identifier");

            JSONArray bonesJson = getArr(geo, "bones");
            if (bonesJson == null) return model;

            // 1. Create all bones first
            for (int i = 0; i < bonesJson.length(); i++) {
                JSONObject b = bonesJson.getJSONObject(i);
                String name = getStr(b, "name");
                String parent = getStr(b, "parent");
                JSONArray pivotArr = getArr(b, "pivot");
                float px = pivotArr != null ? (float) pivotArr.optDouble(0, 0) : 0;
                float py = pivotArr != null ? (float) pivotArr.optDouble(1, 0) : 0;
                float pz = pivotArr != null ? (float) pivotArr.optDouble(2, 0) : 0;
                model.bones.put(name, new Bone(name, parent, px, py, pz));
            }

            // 2. Add cubes & build hierarchy
            for (int i = 0; i < bonesJson.length(); i++) {
                JSONObject b = bonesJson.getJSONObject(i);
                String name = getStr(b, "name");
                Bone bone = model.bones.get(name);
                if (bone == null) continue;

                bone.mirror = getBool(b, "mirror", false);
                JSONArray cubes = getArr(b, "cubes");
                if (cubes != null) {
                    for (int j = 0; j < cubes.length(); j++) {
                        JSONObject c = cubes.getJSONObject(j);
                        JSONArray origin = getArr(c, "origin");
                        JSONArray size = getArr(c, "size");
                        Cube cube = new Cube();
                        cube.ox = origin != null ? (float) origin.optDouble(0, 0) : 0;
                        cube.oy = origin != null ? (float) origin.optDouble(1, 0) : 0;
                        cube.oz = origin != null ? (float) origin.optDouble(2, 0) : 0;
                        cube.sx = size != null ? (float) size.optDouble(0, 0) : 0;
                        cube.sy = size != null ? (float) size.optDouble(1, 0) : 0;
                        cube.sz = size != null ? (float) size.optDouble(2, 0) : 0;
                        cube.inflate = (float) c.optDouble("inflate", 0);

                        // Parse UVs for each face
                        JSONObject uvObj = getObj(c, "uv");
                        if (uvObj != null) {
                            String[] dirs = {"north", "east", "south", "west", "up", "down"};
                            for (String dir : dirs) {
                                JSONObject face = getObj(uvObj, dir);
                                if (face != null) {
                                    JSONArray uvArr = getArr(face, "uv");                                    JSONArray sizeArr = getArr(face, "uv_size");
                                    if (uvArr != null && sizeArr != null) {
                                        cube.faces.put(dir, new FaceUV(
                                            (int) uvArr.optDouble(0),
                                            (int) uvArr.optDouble(1),
                                            (int) sizeArr.optDouble(0),
                                            (int) sizeArr.optDouble(1)
                                        ));
                                    }
                                }
                            }
                        }
                        bone.cubes.add(cube);
                    }
                }

                // Link parent-child
                if (bone.parent != null && model.bones.containsKey(bone.parent)) {
                    model.bones.get(bone.parent).children.add(bone);
                } else if (bone.parent == null || "root".equals(bone.parent)) {
                    model.root = bone;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }
}
