package smartisanos.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.smartisanos.textboom.R;

public class SettingItemSwitch extends LinearLayout {
    private final TextView titleView;
    private final Switch switchView;

    public SettingItemSwitch(Context context) {
        this(context, null);
    }

    public SettingItemSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setPadding(dp(16), dp(14), dp(16), dp(14));

        titleView = new TextView(context);
        titleView.setTextColor(0xff202020);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        addView(titleView, titleParams);

        switchView = new Switch(context);
        addView(switchView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

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

    public Switch getSwitch() {
        return switchView;
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    public void setChecked(boolean checked) {
        switchView.setChecked(checked);
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        switchView.setOnCheckedChangeListener(listener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        titleView.setEnabled(enabled);
        switchView.setEnabled(enabled);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
