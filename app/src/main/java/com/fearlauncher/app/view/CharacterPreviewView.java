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
    
    private ArrayList<CubeData> cubes = new ArrayList<>();
    private boolean isModelLoaded = false;

    static class CubeData {
        float x, y, z, w, h, d;
        int u, v;
        float inflate;
        int depthSort;
        CubeData(float x, float y, float z, float w, float h, float d, int u, int v, float inflate) {
            this.x=x; this.y=y; this.z=z; this.w=w; this.h=h; this.d=d;
            this.u=u; this.v=v; this.inflate=inflate;
            this.depthSort = (int)(z * 100);
        }
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

    public void switchModel(String fileName) {        loadModel(fileName);
        invalidate();
    }

    private void loadModel(String fileName) {
        cubes.clear();
        try {
            InputStream is = getContext().getAssets().open(fileName);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            parseAndBuildCubes(json);
            isModelLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
            isModelLoaded = false;
        }
    }

    private void parseAndBuildCubes(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray geometries = root.getJSONArray("minecraft:geometry");
        if (geometries.length() == 0) return;

        JSONObject geo = geometries.getJSONObject(0);
        JSONArray bones = geo.getJSONArray("bones");

        // Map to store absolute pivot positions
        Map<String, float[]> absPivot = new HashMap<>();
        absPivot.put("root", new float[]{0,0,0});

        // First pass: calculate absolute pivots
        for (int i = 0; i < bones.length(); i++) {
            JSONObject b = bones.getJSONObject(i);
            String name = b.getString("name");
            String parent = b.optString("parent", "root");
            JSONObject pivot = b.optJSONObject("pivot");
            float px = (float) pivot.optDouble("x", 0);
            float py = (float) pivot.optDouble("y", 0);
            float pz = (float) pivot.optDouble("z", 0);
            
            float[] pPos = absPivot.getOrDefault(parent, new float[]{0,0,0});
            absPivot.put(name, new float[]{pPos[0]+px, pPos[1]+py, pPos[2]+pz});
        }

        // Second pass: extract cubes with absolute positions
        for (int i = 0; i < bones.length(); i++) {
            JSONObject b = bones.getJSONObject(i);
            String name = b.getString("name");            float[] pos = absPivot.getOrDefault(name, new float[]{0,0,0});
            JSONArray boneCubes = b.optJSONArray("cubes");
            if (boneCubes == null) continue;

            for (int j = 0; j < boneCubes.length(); j++) {
                JSONObject c = boneCubes.getJSONObject(j);
                JSONObject orig = c.getJSONObject("origin");
                JSONObject size = c.getJSONObject("size");
                JSONObject uv = c.getJSONObject("uv");
                float inf = (float) c.optDouble("inflate", 0);

                cubes.add(new CubeData(
                    (float)orig.optDouble("x") + pos[0],
                    (float)orig.optDouble("y") + pos[1],
                    (float)orig.optDouble("z") + pos[2],
                    (float)size.optDouble("x"),
                    (float)size.optDouble("y"),
                    (float)size.optDouble("z"),
                    uv.optInt("u"),
                    uv.optInt("v"),
                    inf
                ));
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isModelLoaded || cubes.isEmpty()) {
            // Fallback debug grid if model fails
            drawDebugGrid(canvas);
            return;
        }

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float scale = Math.min(getWidth(), getHeight()) / 80f;

        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.rotate(rotationY, 0, 0);

        // Sort back-to-front
        cubes.sort(Comparator.comparingInt(c -> c.depthSort));

        for (CubeData c : cubes) {
            drawCube(canvas, c);
        }
    }
    private void drawCube(Canvas canvas, CubeData c) {
        float ox = c.x - c.inflate;
        float oy = c.y - c.inflate;
        float oz = c.z - c.inflate;
        float w = c.w + c.inflate * 2;
        float h = c.h + c.inflate * 2;
        float d = c.d + c.inflate * 2;

        // Isometric projection: X right, Y up (flipped for canvas), Z depth
        float px = ox - oz * 0.5f;
        float py = -oy - oz * 0.3f;

        // Draw 3 visible faces
        drawFace(canvas, px, py, w, h, c.u, c.v, (int)w, (int)h);       // Front
        drawFace(canvas, px, py - h, w, d, c.u, c.v + (int)h, (int)w, (int)d); // Top
        drawFace(canvas, px + w, py, d, h, c.u + (int)w, c.v, (int)d, (int)h); // Right
    }

    private void drawFace(Canvas canvas, float x, float y, float w, float h, int u, int v, int tw, int th) {
        if (skinTexture == null) return;
        int tu = Math.max(0, Math.min(u, skinTexture.getWidth() - 1));
        int tv = Math.max(0, Math.min(v, skinTexture.getHeight() - 1));
        int twC = Math.min(tw, skinTexture.getWidth() - tu);
        int thC = Math.min(th, skinTexture.getHeight() - tv);

        srcRect.set(tu, tv, tu + twC, tv + thC);
        dstRect.set(x, y, x + w, y + h);
        canvas.drawBitmap(skinTexture, srcRect, dstRect, paint);
    }

    private void drawDebugGrid(Canvas canvas) {
        paint.setColor(Color.RED);
        paint.setStrokeWidth(2f);
        canvas.drawLine(-50, 0, 50, 0, paint);
        canvas.drawLine(0, -50, 0, 50, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText("Model Loading...", -40, -10, paint);
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
                invalidate();                return true;
        }
        return super.onTouchEvent(event);
    }
}
