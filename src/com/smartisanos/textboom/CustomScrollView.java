package com.cashewteam.novatext.android;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/**
 * Created by denglinling on 16-11-11.
 */
public class CustomScrollView extends ScrollView{
    private OnScrollListener mOnScrollListener;
    private OnEdgeDragListener mOnEdgeDragListener;
    private float mLastY;
    private float mEdgeOffset;
    private boolean mEdgeDragging;
    private boolean mEdgeDragEnabled = true;
    private final int mTouchSlop;
    private final float mTriggerDistance;
    private final float mDampingDistance;

    public CustomScrollView(Context context) {
        this(context, null);
    }
    public CustomScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        float density = context.getResources().getDisplayMetrics().density;
        mTriggerDistance = 88f * density;
        mDampingDistance = 180f * density;
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void setOnScrollListener(OnScrollListener onScrollListener) {
        mOnScrollListener = onScrollListener;
    }

    public void setOnEdgeDragListener(OnEdgeDragListener onEdgeDragListener) {
        mOnEdgeDragListener = onEdgeDragListener;
    }

    public void setEdgeDragEnabled(boolean enabled) {
        mEdgeDragEnabled = enabled;
        if (!enabled) {
            resetEdgeDrag(false);
        }
    }

    public interface OnEdgeDragListener {
        void onEdgeDrag(float offset);
        void onEdgeDragRelease(float offset, boolean triggered);
    }

    public interface OnScrollListener{
        void onScrollChanged();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mEdgeDragEnabled) {
            return super.onTouchEvent(ev);
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float currentY = ev.getY();
                float dy = currentY - mLastY;
                if (mEdgeDragging || shouldStartEdgeDrag(dy)) {
                    if (!mEdgeDragging && Math.abs(dy) < mTouchSlop) {
                        mLastY = currentY;
                        return true;
                    }
                    float nextOffset = mEdgeOffset + applyResistance(dy);
                    if (mEdgeDragging && crossesZero(mEdgeOffset, nextOffset)) {
                        resetEdgeDrag(true);
                        mLastY = currentY;
                        return super.onTouchEvent(ev);
                    }
                    mEdgeDragging = true;
                    mEdgeOffset = nextOffset;
                    dispatchEdgeDrag();
                    mLastY = currentY;
                    return true;
                }
                mLastY = currentY;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mEdgeDragging || mEdgeOffset != 0f) {
                    float releaseOffset = mEdgeOffset;
                    boolean triggered = Math.abs(releaseOffset) >= mTriggerDistance;
                    resetEdgeDrag(false);
                    if (mOnEdgeDragListener != null) {
                        mOnEdgeDragListener.onEdgeDragRelease(releaseOffset, triggered);
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollChanged();
        }
    }

    private boolean shouldStartEdgeDrag(float dy) {
        if (dy == 0f) {
            return false;
        }
        if (dy > 0f) {
            return !canScrollVertically(-1);
        }
        return !canScrollVertically(1);
    }

    private float applyResistance(float delta) {
        return (float) (delta / (1f + Math.abs(mEdgeOffset) / mDampingDistance));
    }

    private boolean crossesZero(float oldOffset, float newOffset) {
        return (oldOffset > 0f && newOffset <= 0f) || (oldOffset < 0f && newOffset >= 0f);
    }

    private void dispatchEdgeDrag() {
        if (mOnEdgeDragListener != null) {
            mOnEdgeDragListener.onEdgeDrag(mEdgeOffset);
        }
    }

    private void resetEdgeDrag(boolean notify) {
        mEdgeDragging = false;
        mEdgeOffset = 0f;
        if (notify) {
            dispatchEdgeDrag();
        }
    }
}
