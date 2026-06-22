package smartisanos.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingItemText extends LinearLayout {
    private final TextView titleView;
    private final TextView subTitleView;

    public SettingItemText(Context context) {
        this(context, null);
    }

    public SettingItemText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(14), dp(16), dp(14));

        titleView = new TextView(context);
        titleView.setTextColor(0xff202020);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        subTitleView = new TextView(context);
        subTitleView.setTextColor(0xff666666);
        subTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LayoutParams subParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        addView(subTitleView, subParams);

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

    public void setSubTitle(CharSequence subTitle) {
        subTitleView.setText(subTitle);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
