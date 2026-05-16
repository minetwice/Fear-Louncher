package com.fearlauncher.app.view;

import android.graphics.*;
import com.fearlauncher.app.model.MinecraftModelParser;
import java.util.*;

public class MinecraftModelRenderer {
    
    private MinecraftModelParser.Model model;
    private Bitmap skinTexture;
    private float rotationY = 0f;
    private float rotationX = 15f;
    private float scale = 1f;
    
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    
    // Face indices for cube rendering (simplified isometric)
    private static final int[] FACE_ORDER = {0, 1, 2, 3, 4, 5}; // front, back, left, right, top, bottom
    
    public void setModel(String geometryJson, String modelIdentifier) {
        model = MinecraftModelParser.parse(geometryJson, modelIdentifier);
    }
    
    public void setSkin(Bitmap skin) {
        this.skinTexture = skin;
    }
    
    public void setRotation(float y, float x) {
        rotationY = y;
        rotationX = Math.max(-30f, Math.min(60f, x));
    }
    
    public void setScale(float s) {
        scale = Math.max(0.5f, Math.min(2f, s));
    }
    
    public void draw(Canvas canvas, float centerX, float centerY) {
        if (model == null || skinTexture == null) return;
        
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        
        // Apply isometric projection
        applyIsometricTransform(canvas);
        
        // Render bone hierarchy
        if (model.root != null) {
            renderBone(canvas, model.root, new float[3]); // start at origin
        }
        
        canvas.restore();
    }
    
    private void applyIsometricTransform(Canvas canvas) {
        // Simplified isometric: rotate Y, then tilt X
        matrix.reset();
        matrix.postRotate(rotationY, 0, 0);
        matrix.postScale(1f, 0.85f); // squash Y for isometric feel
        matrix.postSkew(0f, (float) Math.sin(Math.toRadians(rotationX)) * 0.2f);
        canvas.concat(matrix);
    }
    
    private void renderBone(Canvas canvas, MinecraftModelParser.Bone bone, float[] parentPos) {
        // Calculate world position
        float[] worldPos = {
            parentPos[0] + bone.px,
            parentPos[1] + bone.py,
            parentPos[2] + bone.pz
        };
        
        // Render cubes for this bone
        for (MinecraftModelParser.Cube cube : bone.cubes) {
            renderCube(canvas, worldPos, cube);
        }
        
        // Recurse to children
        for (MinecraftModelParser.Bone child : bone.children) {
            renderBone(canvas, child, worldPos);
        }
    }
    
    private void renderCube(Canvas canvas, float[] pos, MinecraftModelParser.Cube cube) {
        // Apply inflation for layers (jacket, sleeves, etc.)
        float ox = cube.ox + pos[0] - cube.inflate;
        float oy = cube.oy + pos[1] - cube.inflate;
        float oz = cube.oz + pos[2] - cube.inflate;
        float sx = cube.sx + cube.inflate * 2;
        float sy = cube.sy + cube.inflate * 2;
        float sz = cube.sz + cube.inflate * 2;
        
        // Render each face with UV mapping
        // Front face (most visible in isometric)
        drawTexturedFace(canvas, ox, oy, oz, sx, sy, sz, 
            cube.u, cube.v, (int) sx, (int) sy, false);
        
        // Right face
        drawTexturedFace(canvas, ox + sx, oy, oz, sz, sy, sx,
            cube.u + (int) sx, cube.v, (int) sz, (int) sy, cube.mirror);
        
        // Top face
        drawTexturedFace(canvas, ox, oy + sy, oz, sx, sz, sy,
            cube.u, cube.v + (int) sy, (int) sx, (int) sz, false);
    }
    
    private void drawTexturedFace(Canvas canvas, float x, float y, float z, 
                                float w, float h, float d,
                                int u, int v, int uw, int vh, boolean mirror) {
        if (skinTexture == null) return;
        
        // Project 3D to 2D (simplified orthographic)
        float projX = x - z * 0.5f;
        float projY = y - z * 0.3f;
        
        // UV mapping: convert model UV to texture pixels
        int tx = u;
        int ty = v;
        int tw = Math.min(uw, skinTexture.getWidth() - tx);
        int th = Math.min(vh, skinTexture.getHeight() - ty);
        
        // Handle mirror (for left-side limbs)
        if (mirror) {
            matrix.reset();
            matrix.postScale(-1f, 1f, projX + w/2, projY);
            canvas.save();
            canvas.concat(matrix);
        }
        
        // Draw textured quad
        srcRect.set(tx, ty, tx + tw, ty + th);
        dstRect.set(projX, projY, projX + w, projY + h);
        
        canvas.drawBitmap(skinTexture, srcRect, dstRect, paint);
        
        if (mirror) canvas.restore();
        
        // Optional: wireframe outline for debugging
        // paint.setStyle(Paint.Style.STROKE);
        // paint.setColor(Color.WHITE);
        // paint.setStrokeWidth(0.5f);
        // canvas.drawRect(dstRect, paint);
        // paint.setStyle(Paint.Style.FILL);
    }
    
    // ✅ Get current rotation (for saving state)
    public float getRotationY() { return rotationY; }
    public float getRotationX() { return rotationX; }
}
