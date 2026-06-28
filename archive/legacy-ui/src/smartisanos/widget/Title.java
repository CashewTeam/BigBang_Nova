package smartisanos.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.smartisanos.textboom.R;

public class Title extends LinearLayout {
    public static final String EXTRA_TITLE_TEXT = "extra_title_text";
    public static final String EXTRA_BACK_BTN_RES_ID = "extra_back_btn_res_id";

    private final TextView titleView;
    private final ImageButton backButton;

    public Title(Context context) {
        this(context, null);
    }

    public Title(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(16), dp(12), dp(16), dp(12));

        backButton = new ImageButton(context);
        backButton.setBackgroundColor(0x00000000);
        backButton.setImageResource(android.R.drawable.ic_menu_revert);
        LayoutParams backParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        addView(backButton, backParams);

        titleView = new TextView(context);
        titleView.setTextColor(0xff202020);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleView.setPadding(dp(12), 0, 0, 0);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        addView(titleView, titleParams);
        setBackgroundColor(0xfff5f5f5);

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

    public void setTitle(int resId) {
        titleView.setText(resId);
    }

    public ImageButton getBackButton() {
        return backButton;
    }

    public void setBackButtonListener(OnClickListener listener) {
        backButton.setOnClickListener(listener);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
