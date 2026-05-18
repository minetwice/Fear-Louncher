package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.fearlauncher.app.R;
import java.util.ArrayList;
import java.util.List;

public class AnimatedBackgroundView extends View {
    
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<SmokeParticle> smokeList = new ArrayList<>();
    private float shineOffset = 0;

    public AnimatedBackgroundView(Context context) {
        super(context);
        init();
    }

    public AnimatedBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Create initial smoke particles
        for (int i = 0; i < 15; i++) {
            smokeList.add(new SmokeParticle(getWidth(), getHeight()));
        }
        startAnimationLoop();
    }

    private void startAnimationLoop() {
        post(new Runnable() {
            @Override
            public void run() {
                shineOffset += 5; // Speed of shine line
                
                // Update smoke particles
                for (SmokeParticle p : smokeList) {
                    p.update();
                }
                
                invalidate(); // Redraw
                postDelayed(this, 16); // ~60 FPS
            }
        });
    }
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        smokeList.clear();
        for (int i = 0; i < 15; i++) {
            smokeList.add(new SmokeParticle(w, h));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // 1. Base Gradient (Red/Black Theme)
        int[] colors = {
            getResources().getColor(R.color.bg_start, null),
            getResources().getColor(R.color.bg_end, null),
            getResources().getColor(R.color.primary_dark, null)
        };
        float[] positions = {0f, 0.6f, 1f};
        LinearGradient bgGrad = new LinearGradient(0, 0, w, h, colors, positions, Shader.TileMode.CLAMP);
        paint.setShader(bgGrad);
        canvas.drawRect(0, 0, w, h, paint);

        // 2. Smoke Effect (Soft fog rising)
        paint.setShader(null);
        for (SmokeParticle p : smokeList) {
            RadialGradient smokeGrad = new RadialGradient(p.x, p.y, p.radius,
                    new int[]{0x30FF5252, 0x00000000}, null, Shader.TileMode.CLAMP);
            paint.setShader(smokeGrad);
            canvas.drawCircle(p.x, p.y, p.radius, paint);
        }

        // 3. Anime Shine (Sharp diagonal cut)
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        
        // Calculate diagonal line position
        float x1 = shineOffset % (w + h) - h;
        float y1 = 0;
        float x2 = x1 + h;
        float y2 = h;

        // Make the line sharp and glowing
        paint.setAlpha(30); // Transparency
        paint.setStrokeWidth(40); // Thickness        canvas.drawLine(x1, y1, x2, y2, paint);
        
        // Core bright line
        paint.setAlpha(150);
        paint.setStrokeWidth(2);
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    // ✅ FIXED: Static inner class with proper constructor
    private static class SmokeParticle {
        float x, y, radius, alpha;
        float speed;

        // ✅ Constructor with return type NOT needed (it's a constructor)
        SmokeParticle(int w, int h) {
            reset(w, h);
            // Random start positions
            y = (float) (Math.random() * h);
            radius = (float) (Math.random() * 200 + 100);
        }

        void reset(int w, int h) {
            x = (float) (Math.random() * w);
            y = h + radius; // Start below screen
            radius = 50;
            alpha = 0;
            speed = (float) (Math.random() * 1 + 0.5f);
        }

        void update() {
            y -= speed; // Move up
            radius += 0.5f; // Expand
            alpha += 0.01f;
            if (y < -radius) reset(1000, 1000);
        }
    }
}
