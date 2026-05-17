package com.fearlauncher.app.model;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public class MinecraftPlayerModel {

    // ===== CUBE DEFINITION =====
    public static class Cube {
        public String name;
        public float x, y, z;           // Position
        public float width, height, depth; // Size
        public float pivotX, pivotY, pivotZ; // Rotation pivot
        public Cube parent;
        
        // Texture UV mapping (for skin)
        public int texU, texV; // Top-left UV on skin texture
        public boolean isLayer; // Jacket/hat layer flag

        public Cube(String name, float x, float y, float z,
                   float width, float height, float depth,
                   float pivotX, float pivotY, float pivotZ,
                   int texU, int texV, boolean isLayer) {
            this.name = name;
            this.x = x; this.y = y; this.z = z;
            this.width = width; this.height = height; this.depth = depth;
            this.pivotX = pivotX; this.pivotY = pivotY; this.pivotZ = pivotZ;
            this.texU = texU; this.texV = texV;
            this.isLayer = isLayer;
        }

        public void setParent(Cube parent) { this.parent = parent; }

        // ✅ Render cube with texture on Canvas (isometric projection)
        public void render(Canvas canvas, Bitmap skin, Paint paint, 
                          float baseX, float baseY, float scale, float rotationY) {
            if (skin == null) return;

            // Calculate world position (with parent offset)
            float worldX = x + (parent != null ? parent.x : 0);
            float worldY = y + (parent != null ? parent.y : 0);
            float worldZ = z + (parent != null ? parent.z : 0);

            // Isometric projection: rotate around Y, then project
            float cos = (float) Math.cos(Math.toRadians(rotationY));
            float sin = (float) Math.sin(Math.toRadians(rotationY));
            float projX = worldX * cos - worldZ * sin;            float projZ = worldX * sin + worldZ * cos;

            // Convert to canvas coordinates (Y flipped)
            float canvasX = baseX + projX * scale;
            float canvasY = baseY - worldY * scale - projZ * scale * 0.3f;

            // Draw front face (main visible face)
            drawTexturedFace(canvas, skin, paint, 
                canvasX, canvasY, width * scale, height * scale,
                texU, texV, (int) width, (int) height);

            // Draw top face (simple)
            if (depth > 0) {
                drawTexturedFace(canvas, skin, paint,
                    canvasX, canvasY - height * scale, 
                    width * scale, depth * scale,
                    texU, texV + (int) height, (int) width, (int) depth);
            }

            // Draw right face
            drawTexturedFace(canvas, skin, paint,
                canvasX + width * scale, canvasY,
                depth * scale, height * scale,
                texU + (int) width, texV, (int) depth, (int) height);
        }

        private void drawTexturedFace(Canvas canvas, Bitmap skin, Paint paint,
                                   float x, float y, float w, float h,
                                   int u, int v, int tw, int th) {
            // Clamp UV to texture bounds
            int tu = Math.max(0, Math.min(u, skin.getWidth() - 1));
            int tv = Math.max(0, Math.min(v, skin.getHeight() - 1));
            int twC = Math.min(tw, skin.getWidth() - tu);
            int thC = Math.min(th, skin.getHeight() - tv);

            Rect src = new Rect(tu, tv, tu + twC, tv + thC);
            RectF dst = new RectF(x, y, x + w, y + h);
            canvas.drawBitmap(skin, src, dst, paint);
        }
    }

    // ===== MODEL PARTS =====
    public Cube root, body, bodyLayer, head, headLayer;
    public Cube rightArmUpper, rightArmLower, rightArmUpperLayer, rightArmLowerLayer;
    public Cube leftArmUpper, leftArmLower, leftArmUpperLayer, leftArmLowerLayer;
    public Cube rightLegUpper, rightLegLower, rightLegUpperLayer, rightLegLowerLayer;
    public Cube leftLegUpper, leftLegLower, leftLegUpperLayer, leftLegLowerLayer;

    public MinecraftPlayerModel(boolean isSlim) {
        float armW = isSlim ? 3f : 4f; // Alex = 3px, Steve = 4px arms        float armXOffset = isSlim ? 5f : 6f;

        // ROOT
        root = new Cube("root", 0,0,0, 0,0,0, 0,0,0, 0,0, false);

        // BODY (UV: 16,16 size 8x12x4)
        body = new Cube("body", -4,12,-2, 8,12,4, 0,24,0, 16,16, false);
        bodyLayer = new Cube("body_layer", -4.5f,11.5f,-2.5f, 9,13,5, 0,24,0, 16,32, true);

        // HEAD (UV: 0,0 size 8x8x8)
        head = new Cube("head", -4,24,-4, 8,8,8, 0,24,0, 0,0, false);
        headLayer = new Cube("head_layer", -4.5f,23.5f,-4.5f, 9,9,9, 0,24,0, 32,0, true);

        // RIGHT ARM
        rightArmUpper = new Cube("right_arm_upper", -8-armW,16,-2, armW,6,4, -5,22,0, 40,16, false);
        rightArmLower = new Cube("right_arm_lower", -8-armW,10,-2, armW,6,4, -6,16,0, 40,16, false);
        rightArmUpperLayer = new Cube("right_arm_upper_layer", -8.5f-armW,15.5f,-2.5f, armW+1,7,5, -5,22,0, 40,32, true);
        rightArmLowerLayer = new Cube("right_arm_lower_layer", -8.5f-armW,9.5f,-2.5f, armW+1,7,5, -6,16,0, 40,32, true);

        // LEFT ARM
        leftArmUpper = new Cube("left_arm_upper", 4,16,-2, armW,6,4, 5,22,0, 32,48, false);
        leftArmLower = new Cube("left_arm_lower", 4,10,-2, armW,6,4, 6,16,0, 32,48, false);
        leftArmUpperLayer = new Cube("left_arm_upper_layer", 3.5f,15.5f,-2.5f, armW+1,7,5, 5,22,0, 48,48, true);
        leftArmLowerLayer = new Cube("left_arm_lower_layer", 3.5f,9.5f,-2.5f, armW+1,7,5, 6,16,0, 48,48, true);

        // RIGHT LEG
        rightLegUpper = new Cube("right_leg_upper", -4,6,-2, 4,6,4, -2,12,0, 0,16, false);
        rightLegLower = new Cube("right_leg_lower", -4,0,-2, 4,6,4, -2,6,0, 0,16, false);
        rightLegUpperLayer = new Cube("right_leg_upper_layer", -4.5f,5.5f,-2.5f, 5,7,5, -2,12,0, 0,32, true);
        rightLegLowerLayer = new Cube("right_leg_lower_layer", -4.5f,-0.5f,-2.5f, 5,7,5, -2,6,0, 0,32, true);

        // LEFT LEG
        leftLegUpper = new Cube("left_leg_upper", 0,6,-2, 4,6,4, 2,12,0, 16,48, false);
        leftLegLower = new Cube("left_leg_lower", 0,0,-2, 4,6,4, 2,6,0, 16,48, false);
        leftLegUpperLayer = new Cube("left_leg_upper_layer", -0.5f,5.5f,-2.5f, 5,7,5, 2,12,0, 0,48, true);
        leftLegLowerLayer = new Cube("left_leg_lower_layer", -0.5f,-0.5f,-2.5f, 5,7,5, 2,6,0, 0,48, true);

        // ===== PARENTING =====
        head.setParent(body);
        headLayer.setParent(head);
        body.setParent(root);
        bodyLayer.setParent(body);
        rightArmUpper.setParent(body);
        rightArmLower.setParent(rightArmUpper);
        leftArmUpper.setParent(body);
        leftArmLower.setParent(leftArmUpper);
        rightLegUpper.setParent(root);
        rightLegLower.setParent(rightLegUpper);
        leftLegUpper.setParent(root);
        leftLegLower.setParent(leftLegUpper);    }

    // ✅ Render entire model
    public void render(Canvas canvas, Bitmap skin, Paint paint, 
                      float centerX, float centerY, float scale, float rotationY) {
        if (skin == null) return;
        
        // Render order: back to front (simple depth sort)
        leftLegLower.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        rightLegLower.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        leftLegUpper.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        rightLegUpper.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        body.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        leftArmLower.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        rightArmLower.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        leftArmUpper.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        rightArmUpper.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        head.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        
        // Layers (drawn last for overlay effect)
        bodyLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        headLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        leftArmLowerLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        rightArmLowerLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        leftArmUpperLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        rightArmUpperLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        leftLegLowerLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        rightLegLowerLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        leftLegUpperLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
        rightLegUpperLayer.render(canvas, skin, paint, centerX, centerY, scale, rotationY);
    }
                                      }
