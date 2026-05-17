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
import com.fearlauncher.app.model.BedrockModelParser;
import com.fearlauncher.app.model.Account;
import java.io.InputStream;
import java.util.List;

public class CharacterPreviewView extends View {

    private BedrockModelParser.Model model;
    private Bitmap skinTexture;
    private float rotationY = 0f;
    private float lastTouchX;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    public CharacterPreviewView(Context context) {
        super(context);
        init();
    }

    public CharacterPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setFilterBitmap(true);
        // Load your uploaded Bedrock model
        loadModel("models/twice.json");
    }

    // ✅ FIXED: Method added to resolve compilation error
    // AccountDashboardActivity calls this to switch models
    public void switchModel(Account.ModelType type) {
        // Currently we use a single model file. 
        // If you have separate files for Steve/Alex, switch logic goes here.
        // For now, we just reload the model to refresh state.
        loadModel("models/twice.json");
    }
    public void setSkin(Bitmap skin) {
        this.skinTexture = skin;
        invalidate();
    }

    private void loadModel(String assetPath) {
        try {
            InputStream is = getContext().getAssets().open(assetPath);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            
            // Parse Bedrock JSON
            model = BedrockModelParser.parse(json);
            invalidate(); // Redraw view
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Placeholder if model or skin not ready
        if (model == null || skinTexture == null) {
            paint.setColor(0xFF1A1A25);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(0xFFAAAAAA);
            canvas.drawText("Loading Model...", getWidth()/2 - 60, getHeight()/2, paint);
            return;
        }

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float scale = Math.min(getWidth(), getHeight()) / 64f;

        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        
        // Apply rotation for 3D spin effect
        canvas.rotate(rotationY);

        // Render model hierarchy starting from root bone
        if (model.root != null) {
            renderBone(canvas, model.root, 0, 0, 0);
        }        
        canvas.restore();
    }

    // Recursive render function
    private void renderBone(Canvas canvas, BedrockModelParser.Bone bone, float px, float py, float pz) {
        // Calculate absolute position
        float ax = px + bone.px;
        float ay = py + bone.py;
        float az = pz + bone.pz;

        // Draw cubes for this bone
        for (BedrockModelParser.Cube cube : bone.cubes) {
            renderCube(canvas, cube, ax, ay, az);
        }

        // Recurse children
        for (BedrockModelParser.Bone child : bone.children) {
            renderBone(canvas, child, ax, ay, az);
        }
    }

    private void renderCube(Canvas canvas, BedrockModelParser.Cube cube, float bx, float by, float bz) {
        // Apply origin offsets
        float ox = cube.ox + bx;
        float oy = cube.oy + by;
        float oz = cube.oz + bz;
        float w = cube.sx;
        float h = cube.sy;
        float d = cube.sz;

        // Simple Isometric Projection for 2D Canvas
        float projX = ox - oz;
        float projY = -oy - oz;

        // Draw North Face (Front)
        BedrockModelParser.FaceUV uv = cube.faces.get("north");
        if (uv != null) {
            drawFace(canvas, projX, projY - h, w, h, uv);
        }
        
        // Draw Top Face (Up) - Simplified placement
        uv = cube.faces.get("up");
        if (uv != null) {
            drawFace(canvas, projX - d, projY - h - d, w, d, uv);
        }
    }

    private void drawFace(Canvas canvas, float x, float y, float w, float h, BedrockModelParser.FaceUV uv) {
        if (skinTexture == null) return;        
        int u = uv.u;
        int v = uv.v;
        int uw = uv.width;
        int vh = uv.height;
        
        // Safety bounds check
        if (u < 0) u = 0;
        if (v < 0) v = 0;
        if (u + uw > skinTexture.getWidth()) uw = skinTexture.getWidth() - u;
        if (v + vh > skinTexture.getHeight()) vh = skinTexture.getHeight() - v;

        srcRect.set(u, v, u + uw, v + vh);
        dstRect.set(x, y, x + w, y + h);
        canvas.drawBitmap(skinTexture, srcRect, dstRect, paint);
    }

    // Touch handling for rotation
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            rotationY += (event.getX() - lastTouchX) * 0.5f;
            lastTouchX = event.getX();
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            lastTouchX = event.getX();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
