package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.*;

public class CharacterPreviewView extends View {

    private Bitmap skinTexture;
    private float rotationY = 0f;
    private float lastTouchX;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    
    // 3D Render Data
    private ArrayList<RenderCube> renderCubes = new ArrayList<>();
    private boolean isModelLoaded = false;

    static class RenderCube {
        float x, y, z; // World position
        float w, h, d; // Size
        int u, v;      // Texture UV
        float inflate; // Layer offset
        int depthSort; // For back-to-front drawing
    }

    public CharacterPreviewView(Context context) { super(context); init(); }
    public CharacterPreviewView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        paint.setFilterBitmap(true);
        loadModel("models/steve.json");
    }

    public void setSkin(Bitmap skin) {
        this.skinTexture = skin;
        invalidate();
    }

    public void switchModel(String fileName) {
        loadModel(fileName);
        invalidate();
    }

    private void loadModel(String fileName) {
        renderCubes.clear();
        try {
            InputStream is = getContext().getAssets().open(fileName);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            parseMinecraftJSON(json);
            isModelLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseMinecraftJSON(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray geometries = root.getJSONArray("minecraft:geometry");
        if (geometries.length() == 0) return;

        JSONObject geo = geometries.getJSONObject(0);
        JSONArray bones = geo.getJSONArray("bones");
        Map<String, BoneNode> boneMap = new HashMap<>();

        // 1. Create bones
        for (int i = 0; i < bones.length(); i++) {
            JSONObject b = bones.getJSONObject(i);
            String name = b.getString("name");
            String parent = b.optString("parent", null);
            JSONObject pivot = b.optJSONObject("pivot");
            float px = pivot != null ? (float) pivot.optDouble("x", 0) : 0;
            float py = pivot != null ? (float) pivot.optDouble("y", 0) : 0;
            float pz = pivot != null ? (float) pivot.optDouble("z", 0) : 0;
            boneMap.put(name, new BoneNode(name, parent, px, py, pz));
        }

        // 2. Build hierarchy & extract cubes
        for (BoneNode bone : boneMap.values()) {
            if (bone.parent != null && boneMap.containsKey(bone.parent)) {
                boneMap.get(bone.parent).children.add(bone);
            }
        }

        BoneNode rootBone = boneMap.get("root");
        if (rootBone == null) rootBone = boneMap.values().iterator().next();
        
        // 3. Recursively compute absolute positions & collect cubes
        collectCubes(rootBone, boneMap, 0, 0, 0);
    }

    static class BoneNode {
        String name, parent;
        float px, py, pz;
        List<BoneNode> children = new ArrayList<>();
        BoneNode(String n, String p, float x, float y, float z) {
            name=n; parent=p; px=x; py=y; pz=z;
        }
    }

    private void collectCubes(BoneNode bone, Map<String, BoneNode> map, float px, float py, float pz) {
        // Absolute pivot position
        float ax = px + bone.px;
        float ay = py + bone.py;
        float az = pz + bone.pz;

        // Try to find cubes in original JSON for this bone
        // Note: Simplified extraction. For production, store cubes during parsing.
        // Here we assume cubes are attached to bone origin.
        
        for (BoneNode child : bone.children) {
            collectCubes(child, map, ax, ay, az);
        }
    }

    // ✅ Simplified but working cube extraction from JSON
    private void parseMinecraftJSON(String json) throws Exception {
        renderCubes.clear();
        JSONObject root = new JSONObject(json);
        JSONArray geometries = root.getJSONArray("minecraft:geometry");
        if (geometries.length() == 0) return;

        JSONObject geo = geometries.getJSONObject(0);
        JSONArray bones = geo.getJSONArray("bones");

        // Build parent map for position accumulation
        Map<String, float[]> absPos = new HashMap<>();
        absPos.put("root", new float[]{0,0,0});

        // First pass: compute absolute pivots
        for (int i = 0; i < bones.length(); i++) {
            JSONObject b = bones.getJSONObject(i);
            String name = b.getString("name");
            String parent = b.optString("parent", "root");
            JSONObject pivot = b.optJSONObject("pivot");
            float[] pPos = absPos.getOrDefault(parent, new float[]{0,0,0});
            absPos.put(name, new float[]{
                pPos[0] + (float) pivot.optDouble("x", 0),
                pPos[1] + (float) pivot.optDouble("y", 0),
                pPos[2] + (float) pivot.optDouble("z", 0)
            });
        }

        // Second pass: extract cubes with absolute positions
        for (int i = 0; i < bones.length(); i++) {
            JSONObject b = bones.getJSONObject(i);
            String name = b.getString("name");
            float[] pos = absPos.getOrDefault(name, new float[]{0,0,0});
            JSONArray cubes = b.optJSONArray("cubes");
            if (cubes == null) continue;

            for (int j = 0; j < cubes.length(); j++) {
                JSONObject c = cubes.getJSONObject(j);
                JSONObject orig = c.getJSONObject("origin");
                JSONObject size = c.getJSONObject("size");
                JSONObject uv = c.getJSONObject("uv");
                float inf = (float) c.optDouble("inflate", 0);

                RenderCube rc = new RenderCube();
                rc.x = (float) orig.optDouble("x") + pos[0];
                rc.y = (float) orig.optDouble("y") + pos[1];
                rc.z = (float) orig.optDouble("z") + pos[2];
                rc.w = (float) size.optDouble("x");
                rc.h = (float) size.optDouble("y");
                rc.d = (float) size.optDouble("z");
                rc.u = uv.optInt("u");
                rc.v = uv.optInt("v");
                rc.inflate = inf;
                rc.depthSort = (int)(rc.z * 100); // Simple depth sort
                renderCubes.add(rc);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isModelLoaded || skinTexture == null || renderCubes.isEmpty()) return;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float scale = Math.min(getWidth(), getHeight()) / 90f;

        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.rotate(rotationY, 0, 0); // Rotate around Y axis

        // Sort back-to-front
        renderCubes.sort((a, b) -> Integer.compare(a.depthSort, b.depthSort));

        for (RenderCube cube : renderCubes) {
            drawCube3D(canvas, cube);
        }
    }

    private void drawCube3D(Canvas canvas, RenderCube c) {
        float ox = c.x - c.inflate;
        float oy = c.y - c.inflate;
        float oz = c.z - c.inflate;
        float w = c.w + c.inflate * 2;
        float h = c.h + c.inflate * 2;
        float d = c.d + c.inflate * 2;

        // Project 3D to 2D (Isometric-ish)
        // Minecraft: Y up, Z forward. Canvas: Y down.
        float px = ox - oz * 0.5f;
        float py = -oy - oz * 0.3f;

        // Draw 3 visible faces (Front, Top, Right) for performance & look
        drawFace(canvas, px, py, w, h, c.u, c.v, (int)w, (int)h); // Front
        drawFace(canvas, px, py - h, w, d, c.u, c.v + (int)h, (int)w, (int)d); // Top
        drawFace(canvas, px + w, py, d, h, c.u + (int)w, c.v, (int)d, (int)h); // Right
    }

    private void drawFace(Canvas canvas, float x, float y, float w, float h, int u, int v, int tw, int th) {
        if (skinTexture == null) return;
        // Clamp UV to texture bounds
        int tu = Math.max(0, Math.min(u, skinTexture.getWidth() - 1));
        int tv = Math.max(0, Math.min(v, skinTexture.getHeight() - 1));
        int twC = Math.min(tw, skinTexture.getWidth() - tu);
        int thC = Math.min(th, skinTexture.getHeight() - tv);

        srcRect.set(tu, tv, tu + twC, tv + thC);
        dstRect.set(x, y, x + w, y + h);
        canvas.drawBitmap(skinTexture, srcRect, dstRect, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                rotationY += (event.getX() - lastTouchX) * 0.5f;
                lastTouchX = event.getX();
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
