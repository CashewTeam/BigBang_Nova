package smartisanos.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.smartisanos.textboom.R;

public class SettingItemCheck extends LinearLayout {
    private final ImageView iconView;
    private final TextView titleView;
    private final ImageView checkedView;

    public SettingItemCheck(Context context) {
        this(context, null);
    }

    public SettingItemCheck(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setPadding(dp(16), dp(12), dp(16), dp(12));

        iconView = new ImageView(context);
        LayoutParams iconParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        iconParams.rightMargin = dp(12);
        addView(iconView, iconParams);

        titleView = new TextView(context);
        titleView.setTextColor(0xff202020);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        addView(titleView, titleParams);

        checkedView = new ImageView(context);
        checkedView.setImageResource(android.R.drawable.checkbox_on_background);
        checkedView.setVisibility(GONE);
        addView(checkedView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        if (attrs != null) {
            android.content.res.TypedArray ta = context.obtainStyledAttributes(attrs, new int[] { android.R.attr.text });
            try {
                CharSequence title = ta.getText(0);
                if (title != null) {
                    titleView.setText(title);
                }
            } finally {
                ta.recycle();
            }
        }
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    public void setIcon(Drawable drawable) {
        iconView.setImageDrawable(drawable);
    }

    public void setChecked(boolean checked) {
        checkedView.setVisibility(checked ? VISIBLE : GONE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
