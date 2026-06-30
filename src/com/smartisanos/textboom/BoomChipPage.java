package com.cashewteam.novatext.android;

import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import com.cashewteam.novatext.android.domain.capture.TextSessionCoordinator;

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
    private final TextView mAdjacentTopHint;
    private final TextView mAdjacentBottomHint;
    private final int mScrollerBaseInset;
    private final int mTableBasePaddingTop;
    private final int mTableBasePaddingBottom;

    Serializable mSavedData;
    private OnAdjacentRequestListener mOnAdjacentRequestListener;
    private boolean mAdjacentLoading;
    private float mAdjacentOffset;

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
                final LinearLayout row = getChipRow(i);
                if (row == null) {
                    continue;
                }
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
        mAdjacentTopHint = (TextView) contentView.findViewById(R.id.boom_adjacent_top_hint);
        mAdjacentBottomHint = (TextView) contentView.findViewById(R.id.boom_adjacent_bottom_hint);
        mScrollerBaseInset = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                28,
                mActivity.getResources().getDisplayMetrics()
        );
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
        mScroller.setOnEdgeDragListener(new CustomScrollView.OnEdgeDragListener() {
            @Override
            public void onEdgeDrag(float offset) {
                updateAdjacentPull(offset);
            }

            @Override
            public void onEdgeDragRelease(float offset, boolean triggered) {
                releaseAdjacentPull(offset, triggered);
            }
        });
        mTableBasePaddingTop = mBoomTable.getPaddingTop();
        mTableBasePaddingBottom = mBoomTable.getPaddingBottom();
    }

    public interface OnAdjacentRequestListener {
        void onAdjacentRequest(String direction);
    }

    private void removeLegacyChromeSpacing() {
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
                mScrollerBaseInset,
                mScroller.getPaddingRight(),
                mScrollerBaseInset
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
            initChips(true);
            return true;
        }
        return false;
    }

    public void resetChips() {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
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

    public int getRowTop(int row) {
        View child = mBoomConent.getChildAt(row);
        return child == null ? 0 : child.getTop();
    }

    public int getRowsHeight(int topRow, int bottomRow) {
        View top = mBoomConent.getChildAt(topRow);
        View bottom = mBoomConent.getChildAt(bottomRow);
        if (top == null || bottom == null) {
            return 0;
        }
        return bottom.getBottom() - top.getTop();
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
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
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

    public void setOnAdjacentRequestListener(OnAdjacentRequestListener listener) {
        mOnAdjacentRequestListener = listener;
    }

    public boolean replaceWords(int[] segment, String text, int targetWordIndex) {
        if (mBoomActionHandler != null) {
            mBoomActionHandler.handleClick();
        }
        mSavedData = null;
        mBoomConent.removeAllViews();
        if (!mLayout.layoutWords(segment, text, -1)) {
            finishAdjacentPull();
            return false;
        }
        initChips(false);
        scrollToWord(targetWordIndex);
        finishAdjacentPull();
        return true;
    }

    private void scrollToWord(final int wordIndex) {
        if (wordIndex < 0 || wordIndex >= mLayout.getWordCount()) {
            mScroller.scrollTo(0, 0);
            return;
        }
        mScroller.post(new Runnable() {
            @Override
            public void run() {
                mScroller.scrollTo(0, getRowTop(mLayout.getRowForIndex(wordIndex)));
            }
        });
    }

    public void finishAdjacentPull() {
        mAdjacentLoading = false;
        mScroller.setEdgeDragEnabled(true);
        animateContentOffset(0f);
        hideAdjacentHint(mAdjacentTopHint);
        hideAdjacentHint(mAdjacentBottomHint);
    }

    private void initChips(boolean animate) {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final int start = mLayout.getRowStart(i);
            final int count = mLayout.getColumnCount(i);
            if (mLayout.isGapRow(i)) {
                View spacer = new View(mActivity);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        mActivity.getResources().getDimensionPixelOffset(R.dimen.chip_row_height) / 2));
                mBoomConent.addView(spacer);
                continue;
            }
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
        mScroller.post(new Runnable() {
            @Override
            public void run() {
                updateScrollerInsetsForContent();
            }
        });
        if (animate) {
            mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(mDoBoomAnimation);
        }
    }

    private boolean restoreSelectedState() {
        if (mSavedData instanceof TreeSet) {
            TreeSet<Integer> set = (TreeSet<Integer>) mSavedData;
            if (set.size() > 0) {
                for (int i = 0; i < mLayout.getRowCount(); ++i) {
                    final LinearLayout row = getChipRow(i);
                    if (row == null) {
                        continue;
                    }
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

    private LinearLayout getChipRow(int index) {
        final View child = mBoomConent.getChildAt(index);
        return child instanceof LinearLayout ? (LinearLayout) child : null;
    }

    private void updateAdjacentPull(float offset) {
        if (mAdjacentLoading) {
            return;
        }
        mAdjacentOffset = offset;
        applyContentOffset(offset);
        if (offset > 0f) {
            showAdjacentHint(mAdjacentTopHint, "before", offset);
            hideAdjacentHint(mAdjacentBottomHint);
        } else if (offset < 0f) {
            showAdjacentHint(mAdjacentBottomHint, "after", -offset);
            hideAdjacentHint(mAdjacentTopHint);
        } else {
            hideAdjacentHint(mAdjacentTopHint);
            hideAdjacentHint(mAdjacentBottomHint);
        }
    }

    private void releaseAdjacentPull(float offset, boolean triggered) {
        if (mAdjacentLoading) {
            return;
        }
        final String direction = offset > 0f ? "before" : offset < 0f ? "after" : null;
        final String previewText = direction == null ? null : TextSessionCoordinator.INSTANCE.peekAdjacentText(direction);
        if (!triggered || direction == null || previewText == null) {
            finishAdjacentPull();
            return;
        }
        mAdjacentLoading = true;
        mScroller.setEdgeDragEnabled(false);
        animateContentOffset(clampHoldOffset(offset));
        if (mOnAdjacentRequestListener != null) {
            mOnAdjacentRequestListener.onAdjacentRequest(direction);
        }
    }

    private void showAdjacentHint(TextView view, String direction, float distance) {
        final String preview = TextSessionCoordinator.INSTANCE.peekAdjacentText(direction);
        if (preview == null) {
            hideAdjacentHint(view);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(getHintTitle(direction) + "\n" + preview);
        float alpha = Math.min(1f, distance / getTriggerDistance());
        view.setAlpha(alpha);
    }

    private void hideAdjacentHint(TextView view) {
        view.setAlpha(0f);
        view.setVisibility(View.GONE);
    }

    private String getHintTitle(String direction) {
        return "before".equals(direction)
                ? mActivity.getString(R.string.bigbang_pull_previous)
                : mActivity.getString(R.string.bigbang_pull_next);
    }

    private float getTriggerDistance() {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                88f,
                mActivity.getResources().getDisplayMetrics()
        );
    }

    private float clampHoldOffset(float offset) {
        float hold = getTriggerDistance();
        return offset > 0f ? hold : -hold;
    }

    private void applyContentOffset(float offset) {
        mScroller.setTranslationY(0f);
        mBoomTable.setTranslationY(offset);
    }

    private void animateContentOffset(float offset) {
        mAdjacentOffset = offset;
        mScroller.animate().cancel();
        mScroller.setTranslationY(0f);
        mBoomTable.animate()
                .translationY(offset)
                .setDuration(180L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mAdjacentOffset == 0f && !mAdjacentLoading) {
                            hideAdjacentHint(mAdjacentTopHint);
                            hideAdjacentHint(mAdjacentBottomHint);
                        }
                    }
                })
                .start();
    }

    private void updateScrollerInsetsForContent() {
        int viewportHeight = mScroller.getHeight();
        int contentHeight = mBoomConent.getHeight();
        if (viewportHeight <= 0 || contentHeight <= 0) {
            return;
        }
        int symmetricBaseInset = Math.max(mTableBasePaddingTop, mTableBasePaddingBottom);
        int availableHeight = viewportHeight - (mScrollerBaseInset * 2) - (symmetricBaseInset * 2);
        int extraInset = Math.max(0, (availableHeight - contentHeight) / 2);
        int targetTableTop = symmetricBaseInset + extraInset;
        int targetTableBottom = symmetricBaseInset + extraInset;
        if (mBoomTable.getPaddingTop() == targetTableTop && mBoomTable.getPaddingBottom() == targetTableBottom) {
            return;
        }
        mBoomTable.setPadding(
                mBoomTable.getPaddingLeft(),
                targetTableTop,
                mBoomTable.getPaddingRight(),
                targetTableBottom
        );
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
