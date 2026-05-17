package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.fearlauncher.app.model.Account;
import com.fearlauncher.app.model.MinecraftPlayerModel;

public class CharacterPreviewView extends View {

    private MinecraftPlayerModel model;
    private Bitmap skinTexture;
    private float rotationY = 0f;
    private float lastTouchX;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        // Default model: Steve (Classic)
        model = new MinecraftPlayerModel(false);
    }

    // ✅ FIXED: Method ab ModelType leta hai, JSON String nahi
    public void switchModel(Account.ModelType type) {
        boolean isAlex = (type == Account.ModelType.ALEX);
        // Create new model instance based on type
        this.model = new MinecraftPlayerModel(isAlex);
        invalidate(); // Refresh view
    }

    public void setSkin(Bitmap skin) {
        this.skinTexture = skin;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (model == null) return;

        // Center and Scale logic
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        // Scale to fit view nicely (Model is roughly 64 units high)
        float scale = Math.min(getWidth(), getHeight()) / 80f;

        // Render model
        // Note: This requires MinecraftPlayerModel to have the render() method
        model.render(canvas, skinTexture, paint, centerX, centerY, scale, rotationY);
    }

    // Touch Rotation Logic
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                rotationY += dx * 0.5f; // Rotate sensitivity
                lastTouchX = event.getX();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                return true;
        }
        return super.onTouchEvent(event);
    }
}
