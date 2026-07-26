package com.cashewteam.novatext.android;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * Original BigBang's cursor-attached 4 by 3 punctuation board.  It is kept
 * separate from the editor so choosing a glyph still goes through the same
 * edit-history transaction as keyboard input.
 */
final class SymbolPanelPopup {
    static final String[] CHINESE_SYMBOLS = {
            "，", "。", "？", "！", "@", "、", "\\", "：", "；", " ", "~", "…"
    };
    static final String[] ENGLISH_SYMBOLS = {
            ",", ".", "?", "!", "@", "/", "\\", ":", ";", " ", "~", "`"
    };

    interface Callback {
        void onSymbolSelected(String symbol);
    }

    private static final int PANEL_WIDTH_DP = 264;
    private static final int PANEL_HEIGHT_DP = 230;
    private static final int PANEL_ARROW_HEIGHT_DP = 8;
    private static final int CELL_WIDTH_DP = 78;
    private static final int CELL_HEIGHT_DP = 48;

    private final PopupWindow mPopup;
    private final FrameLayout mRoot;
    private final LinearLayout mPanel;
    private final ImageView mArrow;
    private final TextView[] mCells = new TextView[12];
    private final Callback mCallback;
    private final int mPanelWidth;
    private final int mPanelHeight;
    private final int mArrowHeight;
    private final ColorMatrixColorFilter mDarkModeInversion;

    private boolean mChinese = true;
    private int mPressedCell = -1;

    SymbolPanelPopup(Context context, Callback callback) {
        mCallback = callback;
        final float density = context.getResources().getDisplayMetrics().density;
        mPanelWidth = Math.round(PANEL_WIDTH_DP * density);
        mPanelHeight = Math.round(PANEL_HEIGHT_DP * density);
        mArrowHeight = Math.round(PANEL_ARROW_HEIGHT_DP * density);
        mDarkModeInversion = isNightMode(context) ? new ColorMatrixColorFilter(new float[]{
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
        }) : null;

        mRoot = new FrameLayout(context);
        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        mPanel.setBackgroundResource(R.drawable.sym_panel_bg);
        invertGrayscaleBackground(mPanel);
        final int horizontalPadding = Math.round(15f * density);
        final int verticalPadding = Math.round(19f * density);
        mPanel.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        mRoot.addView(mPanel, new FrameLayout.LayoutParams(
                mPanelWidth,
                mPanelHeight,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        ));

        for (int row = 0; row < 4; ++row) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            mPanel.addView(line, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    Math.round(CELL_HEIGHT_DP * density)
            ));
            for (int column = 0; column < 3; ++column) {
                final int index = row * 3 + column;
                TextView cell = new TextView(context);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(15f);
                cell.setTextColor(mDarkModeInversion == null ? 0x80000000 : 0x80FFFFFF);
                cell.setIncludeFontPadding(false);
                cell.setSingleLine(true);
                setCellBackground(cell, backgroundForCell(index));
                cell.setContentDescription(index == 9 ? "切换中英文标点" : "插入符号");
                cell.setOnTouchListener(new CellTouchListener(index));
                line.addView(cell, new LinearLayout.LayoutParams(
                        Math.round(CELL_WIDTH_DP * density),
                        Math.round(CELL_HEIGHT_DP * density)
                ));
                mCells[index] = cell;
            }
        }

        mArrow = new ImageView(context);
        mArrow.setImageResource(R.drawable.sym_panel_arrow);
        invertGrayscaleAsset(mArrow);
        mRoot.addView(mArrow, new FrameLayout.LayoutParams(
                Math.round(13f * density),
                mArrowHeight,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        ));
        updateSymbolTexts();

        mPopup = new PopupWindow(mRoot, mPanelWidth, mPanelHeight + mArrowHeight, true);
        mPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mPopup.setOutsideTouchable(true);
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.setAnimationStyle(0);
    }

    int getTotalHeight() {
        return mPanelHeight + mArrowHeight;
    }

    boolean isShowing() {
        return mPopup.isShowing();
    }

    void dismiss() {
        mPressedCell = -1;
        mPopup.dismiss();
    }

    void show(View host, float anchorX, float anchorTop, float anchorBottom, int visibleBottom) {
        final int maxLeft = Math.max(0, host.getWidth() - mPanelWidth);
        final int left = Math.max(0, Math.min(
                Math.round(anchorX) - mPanelWidth / 2,
                maxLeft
        ));
        final boolean showAbove = Math.round(anchorBottom) + getTotalHeight() > visibleBottom;
        final int top = showAbove
                ? Math.max(0, Math.round(anchorTop) - getTotalHeight())
                : Math.round(anchorBottom);
        positionArrow(anchorX - left, showAbove);
        if (mPopup.isShowing()) {
            mPopup.update(left, top, mPanelWidth, getTotalHeight());
        } else {
            mPopup.showAtLocation(host, Gravity.TOP | Gravity.START, left, top);
        }
    }

    private void positionArrow(float arrowCenterX, boolean showAbove) {
        FrameLayout.LayoutParams arrowParams = (FrameLayout.LayoutParams) mArrow.getLayoutParams();
        arrowParams.gravity = (showAbove ? Gravity.BOTTOM : Gravity.TOP) | Gravity.START;
        arrowParams.leftMargin = Math.max(0, Math.min(
                Math.round(arrowCenterX) - arrowParams.width / 2,
                mPanelWidth - arrowParams.width
        ));
        mArrow.setLayoutParams(arrowParams);
        mArrow.setImageResource(showAbove
                ? R.drawable.sym_panel_arrow_bottom
                : R.drawable.sym_panel_arrow);
        invertGrayscaleAsset(mArrow);
        FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
        panelParams.gravity = (showAbove ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        mPanel.setLayoutParams(panelParams);
    }

    private void updateSymbolTexts() {
        final String[] values = mChinese ? CHINESE_SYMBOLS : ENGLISH_SYMBOLS;
        for (int index = 0; index < mCells.length; ++index) {
            if (index == 9) {
                mCells[index].setText("");
                setCellBackground(mCells[index], mChinese
                        ? R.drawable.symbol_bottom_left_selector_che
                        : R.drawable.symbol_bottom_left_selector_en);
            } else {
                mCells[index].setText(values[index]);
            }
        }
    }

    private int backgroundForCell(int index) {
        switch (index) {
            case 0:
                return R.drawable.symbol_left_top_selector;
            case 1:
                return R.drawable.symbol_top_mid_selector;
            case 2:
                return R.drawable.symbol_right_top_selector;
            case 3:
            case 6:
                return R.drawable.symbol_mid_left_selector;
            case 4:
            case 7:
                return R.drawable.symbol_mid_selector;
            case 5:
            case 8:
                return R.drawable.symbol_mid_right_selector;
            case 9:
                return R.drawable.symbol_bottom_left_selector_che;
            case 10:
                return R.drawable.symbol_bottom_mid_selector;
            default:
                return R.drawable.symbol_bottom_right_selector;
        }
    }

    private boolean isNightMode(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setCellBackground(TextView cell, int backgroundRes) {
        cell.setBackgroundResource(backgroundRes);
        invertGrayscaleBackground(cell);
    }

    private void invertGrayscaleAsset(ImageView view) {
        if (mDarkModeInversion != null) {
            view.setColorFilter(mDarkModeInversion);
        }
    }

    private void invertGrayscaleBackground(View view) {
        if (mDarkModeInversion != null && view.getBackground() != null) {
            view.getBackground().setColorFilter(mDarkModeInversion);
        }
    }

    private void selectCell(int index) {
        if (index < 0) {
            return;
        }
        if (index == 9) {
            mChinese = !mChinese;
            updateSymbolTexts();
            return;
        }
        final String symbol = mCells[index].getText().toString();
        if (symbol.length() > 0) {
            mCallback.onSymbolSelected(symbol);
        }
        dismiss();
    }

    private int findCellAt(float rawX, float rawY) {
        final int[] location = new int[2];
        for (int index = 0; index < mCells.length; ++index) {
            View cell = mCells[index];
            cell.getLocationOnScreen(location);
            if (rawX >= location[0] && rawX < location[0] + cell.getWidth()
                    && rawY >= location[1] && rawY < location[1] + cell.getHeight()) {
                return index;
            }
        }
        return -1;
    }

    private void updatePressedCell(int index) {
        if (mPressedCell == index) {
            return;
        }
        if (mPressedCell >= 0) {
            mCells[mPressedCell].setPressed(false);
        }
        mPressedCell = index;
        if (mPressedCell >= 0) {
            mCells[mPressedCell].setPressed(true);
        }
    }

    private final class CellTouchListener implements View.OnTouchListener {
        private final int mIndex;

        CellTouchListener(int index) {
            mIndex = index;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    updatePressedCell(mIndex);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updatePressedCell(findCellAt(event.getRawX(), event.getRawY()));
                    return true;
                case MotionEvent.ACTION_UP:
                    final int selected = mPressedCell;
                    updatePressedCell(-1);
                    selectCell(selected);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    updatePressedCell(-1);
                    return true;
                default:
                    return true;
            }
        }
    }
}
