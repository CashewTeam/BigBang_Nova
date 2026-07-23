package com.cashewteam.novatext.android;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import com.cashewteam.novatext.android.BoomChipPage;
import com.cashewteam.novatext.android.BoomChipPage.BoomChip;

import smartisanos.util.SidebarUtils;

public class SwipeSelectView extends LinearLayout {

    private final long AUTO_SCROLL_DELAY = 25;
    private int mSelStart;
    private int mSelEnd;
    private int mStartBound;
    private int mEndBound;
    private int mLastTouchIndex;
    private int mStartIndex;
    private boolean mIsSelected;
    private BoomChipPage mBoomPage;
    private boolean mDragStarted;
    private boolean mDeferInitialSelectionVisual;
    private boolean mVisualSelectionApplied;
    private int mAutoScrollTopInset;
    private int mAutoScrollBottomInset;
    private int mAutoScrollVelocity;
    private float mEditTouchDownX;
    private float mEditTouchDownY;
    private long mEditTouchDownTime;
    private boolean mEditTapCandidate;

    // Smartisan's editor reserves a brief chip tap for moving the insertion point.
    private static final long EDIT_CURSOR_TAP_TIMEOUT_MS = 100L;

    private String mDragText;
    private Runnable mStartDrag = new Runnable() {
        @Override
        public void run() {
            if (!TextUtils.isEmpty(mDragText) && SidebarUtils.isSidebarShowing(getContext())) {
                mDragStarted = true;
                SidebarUtils.dragText(SwipeSelectView.this, getContext(), mDragText);
            }
        }
    };

    private Runnable mAutoScroll = new Runnable() {
        @Override
        public void run() {
            if (mAutoScrollVelocity != 0) {
                mBoomPage.mScroller.scrollBy(0, mAutoScrollVelocity);
                postDelayed(mAutoScroll, AUTO_SCROLL_DELAY);
            }
        }
    };

    public SwipeSelectView(Context context) {
        this(context, null);
    }

    public SwipeSelectView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setBoomPage(BoomChipPage boomPage) {
        mBoomPage = boomPage;
        mAutoScrollVelocity = 0;
        final Resources res = getResources();
        mAutoScrollTopInset = res.getDimensionPixelSize(R.dimen.auto_scroll_top);
        mAutoScrollBottomInset = res.getDimensionPixelSize(R.dimen.auto_scroll_bottom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Text is frozen while the Activity re-segments an edit commit. Do not
        // let a late chip gesture change its persisted selection meanwhile.
        if (mBoomPage != null && mBoomPage.isEditMode() && !mBoomPage.canModifyEditText()) {
            return true;
        }
        final float x = ev.getX();
        final float y = ev.getY();
        final boolean isEditing = mBoomPage != null && mBoomPage.isEditMode();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mEditTouchDownX = ev.getRawX();
                mEditTouchDownY = ev.getRawY();
                mEditTouchDownTime = ev.getEventTime();
                mEditTapCandidate = isEditing;
                mDeferInitialSelectionVisual = isAtScrollEdge();
                mVisualSelectionApplied = false;
                BoomChip touchedChip = findChip(x, y, false);
                if (touchedChip != null) {
                    initSelection(touchedChip.index);
                    mIsSelected = !touchedChip.word.isSelected();
                    if (!mDeferInitialSelectionVisual) {
                        applyInitialSelectionVisual(touchedChip);
                    }
                } else {
                    initSelection(-1);
                }
                mAutoScrollVelocity = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                if (isEditing && mEditTapCandidate) {
                    final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (Math.abs(ev.getRawX() - mEditTouchDownX) > touchSlop
                            || Math.abs(ev.getRawY() - mEditTouchDownY) > touchSlop) {
                        mEditTapCandidate = false;
                    }
                }
                if (mLastTouchIndex != -1) {
                    scrollIfNeeded((int) ev.getRawY());
                    touchedChip = findChip(x, y, mSelEnd > mSelStart);
                    if (touchedChip != null && touchedChip.index != mLastTouchIndex) {
                        mVisualSelectionApplied = true;
                        requestDisallowInterceptTouchEvent(true);
                        removeCallbacks(mStartDrag);
                        mLastTouchIndex = touchedChip.index;
                        mStartBound = Math.min(mStartBound, touchedChip.index);
                        mEndBound = Math.max(mEndBound, touchedChip.index);
                        mSelStart = Math.min(mStartIndex, touchedChip.index);
                        mSelEnd = Math.max(mStartIndex, touchedChip.index);
                        performSelect(mIsSelected);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                removeCallbacks(mStartDrag);
                requestDisallowInterceptTouchEvent(false);
                final boolean isShortEditTap = isEditing
                        && mEditTapCandidate
                        && ev.getEventTime() - mEditTouchDownTime < EDIT_CURSOR_TAP_TIMEOUT_MS;
                if (isShortEditTap && mSelStart != -1
                        && !mBoomPage.mBoomActionHandler.hasSelection()) {
                    // Undo the transient chip highlight before using this tap as a cursor move.
                    performSelect(!mIsSelected);
                    mBoomPage.moveEditCursorFromContentTap(ev.getRawX(), ev.getRawY());
                } else if (mSelStart != -1) {
                    if (mSelStart == mSelEnd) {
                        performSelect(mIsSelected);
                    }
                    if (mIsSelected) {
                        mBoomPage.mBoomActionHandler.onSelect(mSelStart, mSelEnd);
                        if (mStartBound < mSelStart) {
                            mBoomPage.mBoomActionHandler.deSelect(mStartBound, mSelStart - 1);
                        }
                        if (mSelEnd < mEndBound) {
                            mBoomPage.mBoomActionHandler.deSelect(mSelEnd + 1, mEndBound);
                        }
                    } else {
                        if (mStartBound < mSelStart) {
                            mBoomPage.mBoomActionHandler.onSelect(mStartBound, mSelStart - 1);
                        }
                        if (mSelEnd < mEndBound) {
                            mBoomPage.mBoomActionHandler.onSelect(mSelEnd + 1, mEndBound);
                        }
                        mBoomPage.mBoomActionHandler.deSelect(mSelStart, mSelEnd);
                    }
                    if (isEditing) {
                        mBoomPage.moveEditCursorToSelectionEnd();
                    }
                } else if (isShortEditTap) {
                    // A tap in an edge or row gap positions the caret without creating a selection.
                    mBoomPage.moveEditCursorFromContentTap(ev.getRawX(), ev.getRawY());
                }
                mAutoScrollVelocity = 0;
                mDeferInitialSelectionVisual = false;
                mVisualSelectionApplied = false;
                mEditTapCandidate = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mStartDrag);
                requestDisallowInterceptTouchEvent(false);
                if (mVisualSelectionApplied && mSelStart != -1 && mSelStart == mSelEnd) {
                    if (!mDragStarted || !mBoomPage.mBoomActionHandler.hasSelection()) {
                        performSelect(!mIsSelected);
                    }
                }
                mDragStarted = false;
                mAutoScrollVelocity = 0;
                mDeferInitialSelectionVisual = false;
                mVisualSelectionApplied = false;
                mEditTapCandidate = false;
                break;
            default:
                removeCallbacks(mStartDrag);
                mAutoScrollVelocity = 0;
                mEditTapCandidate = false;
                break;
        }
        return mSelStart != -1 || isEditing ? true : super.onTouchEvent(ev);
    }

    private void scrollIfNeeded(int screenY) {
        final int[] scrollerLocation = new int[2];
        mBoomPage.mScroller.getLocationOnScreen(scrollerLocation);
        final int scrollerTop = scrollerLocation[1];
        final int scrollerBottom = scrollerTop + mBoomPage.mScroller.getHeight();
        final int autoScrollTop = Math.min(
                scrollerTop + mAutoScrollTopInset,
                scrollerTop + mBoomPage.mScroller.getHeight() / 2
        );
        final int autoScrollBottom = Math.max(
                scrollerBottom - mAutoScrollBottomInset,
                scrollerTop + mBoomPage.mScroller.getHeight() / 2
        );
        if (screenY < autoScrollTop) {
            if (mAutoScrollVelocity >= 0) {
                removeCallbacks(mAutoScroll);
                postDelayed(mAutoScroll, AUTO_SCROLL_DELAY);
            }
            mAutoScrollVelocity = (screenY - autoScrollTop) / 2;
        } else if (screenY > autoScrollBottom) {
            if (mAutoScrollVelocity <= 0) {
                removeCallbacks(mAutoScroll);
                postDelayed(mAutoScroll, AUTO_SCROLL_DELAY);
            }
            mAutoScrollVelocity = (screenY - autoScrollBottom) / 2;
        } else {
            mAutoScrollVelocity = 0;
        }
    }

    private void initSelection(int value) {
        mStartBound = value;
        mEndBound = value;
        mSelStart = value;
        mSelEnd = value;
        mStartIndex = value;
        mLastTouchIndex = value;
    }

    private void applyInitialSelectionVisual(BoomChip touchedChip) {
        mVisualSelectionApplied = true;
        if (mIsSelected) {
            touchedChip.setSelected(true);
            if (!mBoomPage.mBoomActionHandler.hasSelection() && SidebarUtils.isSidebarShowing(getContext())) {
                mDragText = touchedChip.word.getText().toString();
                postDelayed(mStartDrag, ViewConfiguration.getLongPressTimeout());
            }
        } else if (SidebarUtils.isSidebarShowing(getContext())) {
            mDragText = mBoomPage.mBoomActionHandler.getSelectedText();
            postDelayed(mStartDrag, ViewConfiguration.getLongPressTimeout());
        }
    }

    private boolean isAtScrollEdge() {
        return !mBoomPage.mScroller.canScrollVertically(-1) || !mBoomPage.mScroller.canScrollVertically(1);
    }

    private void performSelect(boolean isSelected) {
        int startRow = mBoomPage.mLayout.getRowForIndex(mStartBound);
        int endRow = mBoomPage.mLayout.getRowForIndex(mEndBound);
        for (int i = startRow; i <= endRow; ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    final int index = chip.index;
                    if (index < mStartBound) continue;
                    if (index > mEndBound) return;
                    if (index >= mSelStart && index <= mSelEnd) {
                        chip.setSelected(isSelected);
                    } else {
                        chip.setSelected(!isSelected);
                    }
                }
            }
        }
    }

    private boolean isPointInsideView(float x, float y, View view) {
        final Rect hitRect = new Rect();
        view.getHitRect(hitRect);
        return hitRect.contains((int) x, (int) y);
    }

    private BoomChip findChip(float x, float y, boolean isSwiping) {
        for (int i = 0; i < getChildCount(); ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            if (isPointInsideView(x, y, row)) {
                final float offsetX = row.getScrollX() - row.getLeft();
                final float offsetY = row.getScrollY() - row.getTop();
                float newX = x + offsetX;
                float newY = y + offsetY - row.getTranslationY();
                for (int j = row.getChildCount() - 1; j >= 0; --j) {
                    View child = row.getChildAt(j);
                    if (isSwiping && newX > child.getX()) {
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            return chip;
                        }
                        return null;
                    }
                    if (isPointInsideView(newX, newY, child)) {
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            return chip;
                        }
                        return null;
                    }
                }
                return null;
            }
        }
        return null;
    }

    private LinearLayout getChipRow(int index) {
        final View child = getChildAt(index);
        return child instanceof LinearLayout ? (LinearLayout) child : null;
    }
}
