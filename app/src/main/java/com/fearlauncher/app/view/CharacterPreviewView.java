package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.fearlauncher.app.model.BedrockModelParser;
import com.fearlauncher.app.model.BedrockModelParser.*;
import java.io.InputStream;
import java.util.*;

public class CharacterPreviewView extends View {

    private Model model;
    private Bitmap skinTexture;
    private float rotationY = 0f;
    private float lastTouchX;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    public CharacterPreviewView(Context context) { super(context); init(); }
    public CharacterPreviewView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        paint.setFilterBitmap(true);
        loadModel("models/twice.json"); // Load your new JSON
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
            model = BedrockModelParser.parse(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (model == null || skinTexture == null) {
            // Placeholder if model not loaded
            paint.setColor(0xFF1A1A25);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(0xFFAAAAAA);
            canvas.drawText("Loading Model...", 20, 40, paint);
            return;
        }

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float scale = Math.min(getWidth(), getHeight()) / 80f;

        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        canvas.rotate(rotationY, 0, 0);

        // Render bones recursively
        if (model.root != null) renderBone(canvas, model.root, 0, 0, 0);
    }

    private void renderBone(Canvas canvas, Bone bone, float px, float py, float pz) {
        float ax = px + bone.px;
        float ay = py + bone.py;
        float az = pz + bone.pz;

        // Render cubes
        for (Cube cube : bone.cubes) {
            renderCube(canvas, cube, ax, ay, az);
        }

        // Recurse children
        for (Bone child : bone.children) {
            renderBone(canvas, child, ax, ay, az);
        }
    }

    private void renderCube(Canvas canvas, Cube cube, float bx, float by, float bz) {
        float ox = cube.ox + bx - cube.inflate;
        float oy = cube.oy + by - cube.inflate;
        float oz = cube.oz + bz - cube.inflate;
        float w = cube.sx + cube.inflate * 2;
        float h = cube.sy + cube.inflate * 2;
        float d = cube.sz + cube.inflate * 2;

        // Isometric projection
        float projX = ox - oz * 0.5f;
        float projY = -oy - oz * 0.3f;

        // Draw front face (north) - main visible face
        BedrockModelParser.FaceUV uv = cube.faces.get("north");
        if (uv != null) {
            drawTexturedFace(canvas, projX, projY, w, h, uv.u, uv.v, uv.width, uv.height);
        }

        // Optional: draw top face for depth
        uv = cube.faces.get("up");
        if (uv != null) {
            drawTexturedFace(canvas, projX, projY - h, w, d, uv.u, uv.v, uv.width, uv.height);
        }
    }

    private void drawTexturedFace(Canvas canvas, float x, float y, float w, float h, int u, int v, int tw, int th) {
        if (skinTexture == null) return;
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
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            rotationY += (event.getX() - lastTouchX) * 0.5f;
            lastTouchX = event.getX();
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) { lastTouchX = event.getX(); return true; }
        return super.onTouchEvent(event);
    }
}
