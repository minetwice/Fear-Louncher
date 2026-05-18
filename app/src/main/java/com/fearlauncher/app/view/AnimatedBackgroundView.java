package com.fearlauncher.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.fearlauncher.app.R;

public class AnimatedBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float offset = 0f;

    public AnimatedBackgroundView(Context c) { super(c); init(); }
    public AnimatedBackgroundView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }

    private void init() {
        // ✅ Safe UI animation loop (no raw threads)
        post(new Runnable() {
            @Override public void run() {
                offset += 0.005f;
                if (offset > 1f) offset = 0f;
                invalidate();
                postDelayed(this, 16); // ~60 FPS
            }
        });
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        int[] colors = {
            getResources().getColor(R.color.bg_start, null),
            getResources().getColor(R.color.bg_end, null),
            getResources().getColor(R.color.primary_dark, null)
        };
        float[] positions = {0f, 0.5f + offset * 0.3f, 1f};

        LinearGradient gradient = new LinearGradient(0, 0, w, h, colors, positions, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, paint);

        // Glass shine line
        paint.setShader(null);
        paint.setColor(getResources().getColor(R.color.glass_shine, null));
        paint.setAlpha(20);
        float shineX = (offset * w * 2) % (w + 200) - 100;
        canvas.drawRect(shineX, 0, shineX + 2, h, paint);
    }
}
