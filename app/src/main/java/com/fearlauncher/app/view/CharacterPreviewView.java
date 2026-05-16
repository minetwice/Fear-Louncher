package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.fearlauncher.app.model.Account;

public class CharacterPreviewView extends View {
    
    private final MinecraftModelRenderer renderer = new MinecraftModelRenderer();
    private Account.ModelType modelType = Account.ModelType.STEVE;
    
    // Touch handling
    private float lastTouchX, lastTouchY;
    private boolean isDragging = false;
    
    public CharacterPreviewView(Context context) {
        super(context);
        init();
    }
    
    public CharacterPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        // Load default model JSON (you can store these in assets/)
        loadDefaultModel();
    }
    
    private void loadDefaultModel() {
        // Load Steve model JSON (store in assets/models/steve.json)
        String steveJson = loadAssetFile("models/steve.json");
        String alexJson = loadAssetFile("models/alex.json");
        
        if (steveJson != null) {
            renderer.setModel(steveJson, "geometry.npc.steve");
        }
    }
    
    private String loadAssetFile(String path) {
        try {
            java.io.InputStream is = getContext().getAssets().open(path);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public void setSkin(Bitmap skin, Account.ModelType type) {
        this.modelType = type;
        
        // Switch model if needed
        String modelId = type == Account.ModelType.ALEX 
            ? "geometry.npc.alex" 
            : "geometry.npc.steve";
        
        String json = loadAssetFile(type == Account.ModelType.ALEX 
            ? "models/alex.json" : "models/steve.json");
        
        if (json != null) {
            renderer.setModel(json, modelId);
        }
        
        renderer.setSkin(skin);
        invalidate();
    }
    
    public void reset() {
        renderer.setRotation(0f, 15f);
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        
        renderer.draw(canvas, centerX, centerY);
    }
    
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
                    
                    renderer.setRotation(
                        renderer.getRotationY() + dx * 0.5f,
                        renderer.getRotationX() + dy * 0.3f
                    );
                    
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
}
