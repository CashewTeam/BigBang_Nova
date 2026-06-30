package com.cashewteam.novatext.android;

import android.app.Activity;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cashewteam.novatext.android.BoomActivity;
import com.cashewteam.novatext.android.BoomWordsLayout;
import com.cashewteam.novatext.android.BoomAnimator;
import com.cashewteam.novatext.android.SwipeSelectView;
import com.cashewteam.novatext.android.BoomActionHandler;

import java.io.Serializable;
import java.util.TreeSet;

public class BoomChipPage {
    
    private final static String TAG = "BoomChipPage";
    private final static boolean DBG = BoomActivity.DBG;

    final BoomWordsLayout mLayout;
    final Activity mActivity;
    final View mBoomTable;
    final View mMask;
    final CustomScrollView mScroller;
    final View mCancel;
    final View mBoomPage;
    final BoomActionHandler mBoomActionHandler;
    final View.OnClickListener mDismissClickListener;

    private final SwipeSelectView mBoomConent;
    private final boolean mEnableLegacyMask;

    Serializable mSavedData;

    private int mTouchedX;
    private int mTouchedY;

    OnGlobalLayoutListener mDoBoomAnimation = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(mDoBoomAnimation);
            if (mEnableLegacyMask && mScroller.canScrollVertically(1)) {
                mMask.setVisibility(View.VISIBLE);
            }
            if (restoreSelectedState()) {
                if (DBG) {
                    Log.d(TAG, "Skip boom animation when restoring");
                }
                return;
            }
            if (mTouchedX == -1 || mTouchedY == -1) {
                Log.e(TAG, "WTF, bad touch position passed");
                return;
            }
            float pageX = getChipParentX();
            float pageY = getChipParentY();
            if (DBG) {
                Log.d(TAG, "init Chip and do boom animation");
            }
            final int animationRows = Math.min(mLayout.getRowCount(), 12);
            for (int i = 0; i < animationRows; ++i) {
                LinearLayout row = (LinearLayout) mBoomConent.getChildAt(i);
                float rowX = row.getX();
                float rowY = row.getY();
                for (int j = 0; j < row.getChildCount(); ++j) {
                    View child = row.getChildAt(j);
                    child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    float newX = mTouchedX - pageX - rowX - child.getMeasuredWidth() / 2;
                    float newY = mTouchedY - pageY - rowY - child.getMeasuredHeight() / 2;
                    float x = child.getX();
                    float y = child.getY();
                    child.setTranslationX(newX - x);
                    child.setTranslationY(newY - y);
                    BoomAnimator.makeBoomAnimation(child);
                }
            }
        }

        private float getChipParentX() {
            int[] location = new int[2];
            mBoomConent.getLocationOnScreen(location);
            return location[0];
        }

        private float getChipParentY() {
            int[] location = new int[2];
            mBoomConent.getLocationOnScreen(location);
            return location[1];
        }
    };

    public BoomChipPage(Activity activity, View contentView, boolean enableLegacyMask) {
        mActivity = activity;
        mEnableLegacyMask = enableLegacyMask;
        mBoomPage = contentView;
        mBoomTable = contentView.findViewById(R.id.boom_table);
        mBoomConent = (SwipeSelectView) contentView.findViewById(R.id.boom_content);
        mMask = contentView.findViewById(R.id.boom_mask);
        mCancel = contentView.findViewById(R.id.mask_cancel);
        mScroller = (CustomScrollView) contentView.findViewById(R.id.boom_scroller);
        if (!mEnableLegacyMask) {
            mMask.setVisibility(View.GONE);
            removeLegacyChromeSpacing();
        }
        mLayout = new BoomWordsLayout(mActivity);
        mBoomConent.setBoomPage(this);
        mDismissClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleClick()) {
                    mActivity.finish();
                }
            }
        };
        mBoomPage.setOnClickListener(mDismissClickListener);
        mBoomTable.setOnClickListener(mDismissClickListener);
        mScroller.setOnClickListener(mDismissClickListener);
        mBoomConent.setOnClickListener(mDismissClickListener);
        mCancel.setOnClickListener(mDismissClickListener);
        mBoomActionHandler = new BoomActionHandler(this, mEnableLegacyMask);
        mScroller.setOnScrollListener(mBoomActionHandler);
    }

    private void removeLegacyChromeSpacing() {
        int edgeInset = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                28,
                mActivity.getResources().getDisplayMetrics()
        );
        int selectBarHeadroom = Math.abs(
                mActivity.getResources().getDimensionPixelOffset(R.dimen.chip_row_move_up_offset)
        );
        ViewGroup.MarginLayoutParams scrollerParams = (ViewGroup.MarginLayoutParams) mScroller.getLayoutParams();
        scrollerParams.topMargin = 0;
        scrollerParams.bottomMargin = 0;
        mScroller.setLayoutParams(scrollerParams);
        mScroller.setClipToPadding(false);
        mScroller.setPadding(
                mScroller.getPaddingLeft(),
                edgeInset,
                mScroller.getPaddingRight(),
                edgeInset
        );

        ViewGroup.MarginLayoutParams tableParams = (ViewGroup.MarginLayoutParams) mBoomTable.getLayoutParams();
        tableParams.topMargin = 0;
        tableParams.bottomMargin = 0;
        mBoomTable.setLayoutParams(tableParams);
        mBoomTable.setPadding(
                mBoomTable.getPaddingLeft(),
                selectBarHeadroom,
                mBoomTable.getPaddingRight(),
                mBoomTable.getPaddingBottom()
        );

        mBoomConent.setPadding(
                mBoomConent.getPaddingLeft(),
                0,
                mBoomConent.getPaddingRight(),
                0
        );
    }

    public boolean initWords(int[] segment, String text, int touchedIndex, int touchedX, int touchedY) {
        if (mLayout.layoutWords(segment, text, touchedIndex)) {
            mTouchedX = touchedX;
            mTouchedY = touchedY;
            initChips();
            return true;
        }
        return false;
    }

    public void resetChips() {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final LinearLayout row = (LinearLayout) mBoomConent.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    chip.setSelected(false);
                }
            }
            BoomAnimator.makeMoveAnimation(row, row.getTranslationY(), 0);
        }
    }

    public void moveChipRow(int row, float to) {
        View child = mBoomConent.getChildAt(row);
        BoomAnimator.makeMoveAnimation(child, child.getTranslationY(), to);
    }

    public boolean handleClick() {
        return mBoomActionHandler != null && mBoomActionHandler.handleClick();
    }

    public Serializable captureSelectedState() {
        if (mBoomActionHandler != null && mBoomActionHandler.hasSelection()) {
            return new TreeSet<Integer>(mBoomActionHandler.mSelectedId);
        }
        return null;
    }

    public void restoreSelectedState(Serializable savedState) {
        mSavedData = savedState;
    }

    public String getOriginalText() {
        return mLayout.getOriText();
    }

    public void selectAll() {
        final int wordCount = mLayout.getWordCount();
        if (wordCount <= 0) {
            return;
        }
        if (mBoomActionHandler != null && mBoomActionHandler.isAllSelected()) {
            handleClick();
            return;
        }
        handleClick();
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final LinearLayout row = (LinearLayout) mBoomConent.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    chip.setSelected(true);
                }
            }
        }
        mBoomActionHandler.onSelect(0, wordCount - 1);
    }

    private void initChips() {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final int start = mLayout.getRowStart(i);
            final int count = mLayout.getColumnCount(i);
            LinearLayout row = new LinearLayout(mActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for(int j = 0; j < count; ++j) {
                boolean isPunc = mLayout.isPunc(start + j);
                View chipView = mActivity.getLayoutInflater().inflate(
                        isPunc ? R.layout.boom_punc_layout : R.layout.boom_chip_layout, null);
                BoomChip chip = new BoomChip(start + j, chipView);
                chipView.setTag(chip);
                row.addView(chipView);
            }
            mBoomConent.addView(row);
        }
        mBoomConent.requestLayout();
        mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(mDoBoomAnimation);
    }

    private boolean restoreSelectedState() {
        if (mSavedData instanceof TreeSet) {
            TreeSet<Integer> set = (TreeSet<Integer>) mSavedData;
            if (set.size() > 0) {
                for (int i = 0; i < mLayout.getRowCount(); ++i) {
                    final LinearLayout row = (LinearLayout) mBoomConent.getChildAt(i);
                    for (int j = 0; j < row.getChildCount(); ++j) {
                        View child = row.getChildAt(j);
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            if (set.contains(new Integer(chip.index))) {
                                chip.setSelected(true);
                            }
                        }
                    }
                }
                mBoomActionHandler.onSelect(set);
                return true;
            }
        }
        return false;
    }

    public class BoomChip {
        int index;
        TextView word;
        boolean punc;


        public BoomChip(final int id, View chipView) {
            index = id;
            punc = mLayout.isPunc(id);
            if (punc) {
                word = (TextView) chipView.findViewById(R.id.punc);
            } else {
                word = (TextView) chipView.findViewById(R.id.word);
            }
            word.setText(mLayout.getWord(id));
        }

        public void setSelected(boolean selected) {
            word.setShadowLayer(selected ? 1.0f : 0, 0, -3.0f, 0x1f000000);
            word.setSelected(selected);
        }
    }
}
