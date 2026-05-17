package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.fearlauncher.app.model.BedrockModelParser;
import com.fearlauncher.app.model.BedrockModelParser.*;
import com.fearlauncher.app.model.Account;
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
    private final int[] boneColors = {0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFBC02D, 0xFF8E24AA};

    public CharacterPreviewView(Context context) { super(context); init(); }
    public CharacterPreviewView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        paint.setFilterBitmap(true);
        paint.setStyle(Paint.Style.FILL);
        loadModel("models/twice.json");
    }

    public void switchModel(Account.ModelType type) {
        // Future: Load different JSON for Alex/Steve
        loadModel("models/twice.json");
    }

    public void setSkin(Bitmap skin) {
        this.skinTexture = skin;
        invalidate();
    }

    private void loadModel(String path) {
        try {
            InputStream is = getContext().getAssets().open(path);
            byte[] buf = new byte[is.available()];
            is.read(buf); is.close();
            model = BedrockModelParser.parse(new String(buf, "UTF-8"));
            invalidate();        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (model == null) return;

        float cx = getWidth()/2f, cy = getHeight()/2f;
        float scale = Math.min(getWidth(), getHeight()) / 70f;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.rotate(rotationY);

        if (model.root != null) renderBone(canvas, model.root, 0, 0, 0, 0);
        canvas.restore();
    }

    private int colorForBone(String name, int idx) {
        return boneColors[idx % boneColors.length];
    }

    private void renderBone(Canvas canvas, Bone bone, float px, float py, float pz, int colorIdx) {
        float ax = px + bone.px, ay = py + bone.py, az = pz + bone.pz;
        for (Cube c : bone.cubes) renderCube(canvas, c, ax, ay, az, colorIdx++);
        for (Bone child : bone.children) renderBone(canvas, child, ax, ay, az, colorIdx);
    }

    private void renderCube(Canvas canvas, Cube c, float bx, float by, float bz, int colorIdx) {
        float ox = c.ox + bx, oy = c.oy + by, oz = c.oz + bz;
        float w = c.sx, h = c.sy, d = c.sz;
        
        // Isometric projection
        float x = ox - oz, y = -oy - oz;

        if (skinTexture != null) {
            // Draw textured faces
            drawFace(canvas, x, y - h, w, h, c.faces.get("north"));
            drawFace(canvas, x - d, y - h - d, w, d, c.faces.get("up"));
            drawFace(canvas, x + w, y - h, d, h, c.faces.get("east"));
        } else {
            // Fallback: colored cubes so you can see the model immediately
            paint.setColor(colorForBone("body", colorIdx));
            canvas.drawRect(x, y - h, x + w, y, paint);
            canvas.drawRect(x, y - h - d, x + w, y - h, paint);
            canvas.drawRect(x + w, y - h, x + w + d, y, paint);
            paint.setColor(0xFF333333);
            paint.setStrokeWidth(0.5f / (getWidth()/70f));            canvas.drawLine(x, y - h, x + w, y - h, paint);
            canvas.drawLine(x, y, x + w, y, paint);
            canvas.drawLine(x, y - h, x, y, paint);
            canvas.drawLine(x + w, y - h, x + w, y, paint);
        }
    }

    private void drawFace(Canvas canvas, float x, float y, float w, float h, FaceUV uv) {
        if (uv == null || skinTexture == null) return;
        int u = Math.max(0, Math.min(uv.u, skinTexture.getWidth()-1));
        int v = Math.max(0, Math.min(uv.v, skinTexture.getHeight()-1));
        int uw = Math.min(uv.w, skinTexture.getWidth()-u);
        int vh = Math.min(uv.h, skinTexture.getHeight()-v);
        if (uw <= 0 || vh <= 0) return;

        srcRect.set(u, v, u + uw, v + vh);
        dstRect.set(x, y, x + w, y + h);
        canvas.drawBitmap(skinTexture, srcRect, dstRect, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            rotationY += (e.getX() - lastTouchX) * 0.5f;
            lastTouchX = e.getX();
            invalidate(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_DOWN) { lastTouchX = e.getX(); return true; }
        return super.onTouchEvent(e);
    }
}
