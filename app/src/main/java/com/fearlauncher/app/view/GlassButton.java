package com.fearlauncher.app.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatButton;
import com.fearlauncher.app.R;

public class GlassButton extends AppCompatButton {

    public GlassButton(Context context) { super(context); init(null); }
    public GlassButton(Context context, AttributeSet attrs) { super(context, attrs); init(attrs); }
    public GlassButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init(attrs);
    }

    private void init(AttributeSet attrs) {
        setAllCaps(false);
        setGravity(android.view.Gravity.CENTER);
        setBackgroundResource(R.drawable.btn_glass);
        setTextColor(getResources().getColor(R.color.text, null));
        setTextSize(12);
        setPadding(0, 0, 0, 0);
        setMinHeight(0);
        setMinWidth(0);

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.GlassButton);
            String label = a.getString(R.styleable.GlassButton_label);
            int iconRes = a.getResourceId(R.styleable.GlassButton_icon, 0);
            boolean thin = a.getBoolean(R.styleable.GlassButton_thin, false);
            a.recycle();

            if (label != null) setText(label);
            if (iconRes != 0) setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            if (thin) {
                setPadding(8, 8, 8, 8);
                setMinHeight(48);
            }
        }
    }
}
