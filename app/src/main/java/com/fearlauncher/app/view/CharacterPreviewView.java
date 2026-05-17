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
    private Account.ModelType currentModelType = Account.ModelType.STEVE;
    
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
        setLayerType(LAYER_TYPE_HARDWARE, null);
        // Load default model
        switchModel(currentModelType);
    }

    // ✅ Set skin texture
    public void setSkin(Bitmap skin) {
        this.skinTexture = skin;
        invalidate();
    }

    // ✅ Switch between Steve/Alex model
    public void switchModel(Account.ModelType type) {
        this.currentModelType = type;
        this.model = new MinecraftPlayerModel(type == Account.ModelType.ALEX);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (model == null) return;

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float scale = Math.min(getWidth(), getHeight()) / 70f;

        // Render model with current skin
        model.render(canvas, skinTexture, paint, centerX, centerY, scale, rotationY);
    }

    // ✅ Touch rotation
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                rotationY += (event.getX() - lastTouchX) * 0.5f;
                lastTouchX = event.getX();
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
