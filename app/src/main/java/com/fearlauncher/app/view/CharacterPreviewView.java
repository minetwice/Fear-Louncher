package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.fearlauncher.app.model.Account;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;

public class CharacterPreviewView extends View {

    private Bitmap skinTexture;
    private Account.ModelType currentModelType = Account.ModelType.STEVE;
    private float rotationY = 0f;
    private float lastTouchX;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    
    // Model Data Structure
    private ArrayList<CubeData> cubes = new ArrayList<>();

    static class CubeData {
        float ox, oy, oz; // Origin
        float sx, sy, sz; // Size
        int u, v;         // Texture Coordinates
        float inflate;    // Layer offset (0.25 for jacket)
        
        CubeData(float ox, float oy, float oz, float sx, float sy, float sz, int u, int v, float inflate) {
            this.ox = ox; this.oy = oy; this.oz = oz;
            this.sx = sx; this.sy = sy; this.sz = sz;
            this.u = u; this.v = v;
            this.inflate = inflate;
        }
    }

    public CharacterPreviewView(Context context) { super(context); init(); }
    public CharacterPreviewView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        paint.setFilterBitmap(true);
        loadModelData("models/steve.json"); // Default load
    }

    public void setSkin(Bitmap skin, Account.ModelType type) {
        this.skinTexture = skin;
        this.currentModelType = type;
        
        // Reload model geometry based on type
        String fileName = type == Account.ModelType.ALEX ? "models/alex.json" : "models/steve.json";
        loadModelData(fileName);
        
        invalidate();
    }

    private void loadModelData(String fileName) {
        cubes.clear();
        try {
            InputStream is = getContext().getAssets().open(fileName);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            String jsonStr = new String(buffer, "UTF-8");
            is.close();

            JSONObject root = new JSONObject(jsonStr);
            JSONArray geometries = root.getJSONArray("minecraft:geometry");
            
            // Parse bones and cubes
            // Simplified parsing: We just extract all cubes from all bones for rendering
            // Note: Real hierarchy needs matrix transforms, but for a static preview, 
            // we can approximate positions if the JSON origins are absolute relative to body.
            // However, the provided JSON uses relative pivots. 
            // For this preview, we will use the 'origin' property directly which defines the box position.
            
            if (geometries.length() > 0) {
                JSONObject geo = geometries.getJSONObject(0);
                JSONArray bones = geo.getJSONArray("bones");
                
                for (int i = 0; i < bones.length(); i++) {
                    JSONObject bone = bones.getJSONObject(i);
                    JSONArray boneCubes = bone.optJSONArray("cubes");
                    if (boneCubes != null) {
                        for (int j = 0; j < boneCubes.length(); j++) {
                            JSONObject cube = boneCubes.getJSONObject(j);
                            JSONObject origin = cube.getJSONObject("origin");
                            JSONObject size = cube.getJSONObject("size");
                            JSONObject uv = cube.getJSONObject("uv");
                            float inflate = (float) cube.optDouble("inflate", 0);
                            
                            cubes.add(new CubeData(
                                (float) origin.optDouble("x"),
                                (float) origin.optDouble("y"),
                                (float) origin.optDouble("z"),
                                (float) size.optDouble("x"),
                                (float) size.optDouble("y"),
                                (float) size.optDouble("z"),
                                uv.optInt("u"),
                                uv.optInt("v"),
                                inflate
                            ));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (skinTexture == null || cubes.isEmpty()) return;

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float scale = Math.min(getWidth(), getHeight()) / 80f; // Adjust scale

        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        
        // Simple rotation effect (skew for 3D feel)
        float skewX = (float) Math.sin(Math.toRadians(rotationY)) * 0.5f;
        canvas.skew(skewX, 0);
        
        // Sort cubes to draw back-to-front (rough approximation based on Z)
        // For Minecraft JSON, Z increases towards viewer.
        // We just iterate and draw.
        
        for (CubeData cube : cubes) {
            drawCube(canvas, cube);
        }
    }

    private void drawCube(Canvas canvas, CubeData cube) {
        // Apply inflation
        float ox = cube.ox - cube.inflate;
        float oy = cube.oy - cube.inflate;
        float oz = cube.oz - cube.inflate; // Note: Z is depth
        
        // Project 3D coords to 2D canvas
        // X axis is horizontal, Y is vertical (inverted in Android usually, but JSON origin is bottom-up often?)
        // In Android Canvas, Y=0 is top. Minecraft Y=0 is feet.
        // Let's map: Canvas X = Model X, Canvas Y = -Model Y
        
        float x = ox;
        float y = -oy - cube.sy; // Shift up by height so feet are at 0
        
        // Draw Front Face
        // UV Mapping: u, v is top-left of face on texture
        int textureU = cube.u;
        int textureV = cube.v;
        int textureW = (int) cube.sx;
        int textureH = (int) cube.sy;

        // Safety check for texture bounds
        if (textureU + textureW > skinTexture.getWidth()) textureW = skinTexture.getWidth() - textureU;
        if (textureV + textureH > skinTexture.getHeight()) textureH = skinTexture.getHeight() - textureV;

        srcRect.set(textureU, textureV, textureU + textureW, textureV + textureH);
        dstRect.set(x, y, x + cube.sx, y + cube.sy);
        
        canvas.drawBitmap(skinTexture, srcRect, dstRect, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                rotationY += dx * 0.5f;
                lastTouchX = event.getX();
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
