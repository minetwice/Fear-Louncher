package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.fearlauncher.app.model.Account;

public class CharacterPreviewView extends View {
    
    private static final String TAG = "CharacterPreview";
    
    // Character dimensions (simplified cube-based)
    private static final float HEAD_SIZE = 8f;
    private static final float BODY_WIDTH_STEVE = 8f;
    private static final float BODY_WIDTH_ALEX = 6f; // Slim arms
    private static final float BODY_HEIGHT = 12f;
    private static final float ARM_WIDTH_STEVE = 4f;
    private static final float ARM_WIDTH_ALEX = 3f;
    private static final float LEG_WIDTH = 4f;
    private static final float LIMB_HEIGHT = 12f;
    
    private Bitmap skinBitmap;
    private Account.ModelType modelType = Account.ModelType.STEVE;
    
    // Rotation state
    private float rotationY = 0f; // Y-axis rotation (horizontal spin)
    private float rotationX = 15f; // X-axis rotation (slight tilt down)
    private float lastTouchX, lastTouchY;
    private boolean isDragging = false;
    
    // Drawing helpers
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    
    public CharacterPreviewView(Context context) {
        super(context);
        init();
    }
    
    public CharacterPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setFilterBitmap(true);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }
    
    // ✅ Set skin bitmap
    public void setSkin(Bitmap skin, Account.ModelType type) {
        this.skinBitmap = skin;
        this.modelType = type;
        invalidate();
    }
    
    // ✅ Reset to default
    public void reset() {
        skinBitmap = null;
        rotationY = 0f;
        rotationX = 15f;
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (getWidth() == 0 || getHeight() == 0) return;
        
        // Center and scale
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float scale = Math.min(getWidth(), getHeight()) / 64f;
        
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        
        // Apply 3D-like rotation
        applyRotation(canvas);
        
        // Draw character parts (simplified isometric projection)
        drawHead(canvas);
        drawBody(canvas);
        drawArms(canvas);
        drawLegs(canvas);
        
        canvas.restore();
    }
    
    private void applyRotation(Canvas canvas) {
        // Simplified 3D rotation using matrix
        matrix.reset();
        matrix.postRotate(rotationY, 0, 0);
        matrix.postSkew(0, (float) Math.sin(Math.toRadians(rotationX)) * 0.3f);
        canvas.concat(matrix);
    }
    
    private void drawHead(Canvas canvas) {
        float size = HEAD_SIZE;
        float x = -size/2, y = -BODY_HEIGHT - size, z = -size/2;
        
        // Front face (with skin mapping)
        if (skinBitmap != null) {
            drawTexturedFace(canvas, x, y, size, 0, 0, 8, 8);
        } else {
            // Default face
            paint.setColor(Color.parseColor("#F5CBA7")); // Skin tone
            rect.set(x, y, x+size, y+size);
            canvas.drawRect(rect, paint);
            
            // Eyes
            paint.setColor(Color.BLACK);
            canvas.drawCircle(x+2.5f, y+3f, 0.8f, paint);
            canvas.drawCircle(x+5.5f, y+3f, 0.8f, paint);
            
            // Mouth
            rect.set(x+3f, y+5.5f, x+5f, y+6f);
            canvas.drawRect(rect, paint);
        }
    }
    
    private void drawBody(Canvas canvas) {
        float width = modelType == Account.ModelType.ALEX ? BODY_WIDTH_ALEX : BODY_WIDTH_STEVE;
        float x = -width/2, y = -BODY_HEIGHT, z = -4f;
        
        if (skinBitmap != null) {
            drawTexturedBox(canvas, x, y, width, BODY_HEIGHT, 4f, 8, 8, 12, 8);
        } else {
            paint.setColor(Color.parseColor("#4A90E2")); // Default shirt
            rect.set(x, y, x+width, y+BODY_HEIGHT);
            canvas.drawRect(rect, paint);
        }
    }
    
    private void drawArms(Canvas canvas) {
        float armWidth = modelType == Account.ModelType.ALEX ? ARM_WIDTH_ALEX : ARM_WIDTH_STEVE;
        float armXOffset = modelType == Account.ModelType.ALEX ? 5f : 6f;
        
        // Left arm
        drawLimb(canvas, -armXOffset, -BODY_HEIGHT, armWidth, LIMB_HEIGHT, 0);
        // Right arm
        drawLimb(canvas, armXOffset - armWidth, -BODY_HEIGHT, armWidth, LIMB_HEIGHT, 0);
    }
    
    private void drawLegs(Canvas canvas) {
        // Left leg
        drawLimb(canvas, -LEG_WIDTH/2, 0, LEG_WIDTH, LIMB_HEIGHT, 0);
        // Right leg
        drawLimb(canvas, LEG_WIDTH/2, 0, LEG_WIDTH, LIMB_HEIGHT, 0);
    }
    
    private void drawLimb(Canvas canvas, float x, float y, float width, float height, float depth) {
        if (skinBitmap != null) {
            drawTexturedBox(canvas, x, y, width, height, depth, 16, 16, 12, 16);
        } else {
            paint.setColor(Color.parseColor("#7FBA00")); // Default pants
            rect.set(x, y, x+width, y+height);
            canvas.drawRect(rect, paint);
        }
    }
    
    // ✅ Simplified texture mapping (for demo - real implementation would use UV mapping)
    private void drawTexturedFace(Canvas canvas, float x, float y, float size, int u, int v, int w, int h) {
        if (skinBitmap == null) return;
        
        // Extract face region from skin (simplified)
        Bitmap face = Bitmap.createBitmap(skinBitmap, u, v, Math.min(w, skinBitmap.getWidth()-u), Math.min(h, skinBitmap.getHeight()-v));
        canvas.drawBitmap(face, x, y, paint);
        face.recycle();
    }
    
    private void drawTexturedBox(Canvas canvas, float x, float y, float w, float h, float d, int u, int v, int uw, int vh) {
        // Simplified: just draw front face with texture
        drawTexturedFace(canvas, x, y, w, u, v, uw, vh);
    }
    
    // ✅ Touch handling for rotation
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = true;
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    
                    rotationY += dx * 0.5f;
                    rotationX = Math.max(-30f, Math.min(60f, rotationX + dy * 0.3f));
                    
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    invalidate();
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }
    
    // ✅ Get current rotation angles (for saving state)
    public float getRotationY() { return rotationY; }
    public float getRotationX() { return rotationX; }
    public void setRotation(float y, float x) {
        rotationY = y;
        rotationX = x;
        invalidate();
    }
}
