package com.cashewteam.novatext.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

public class OcrSelectionView extends View {
    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_LEFT = 2;
    private static final int MODE_TOP = 3;
    private static final int MODE_RIGHT = 4;
    private static final int MODE_BOTTOM = 5;
    private static final int MODE_LEFT_TOP = 6;
    private static final int MODE_RIGHT_TOP = 7;
    private static final int MODE_LEFT_BOTTOM = 8;
    private static final int MODE_RIGHT_BOTTOM = 9;

    private final Paint mScrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mImageBounds = new RectF();
    private final RectF mSelectionRect = new RectF();
    private final float mTouchThresholdPx;
    private final float mHandleRadiusPx;
    private final float mHandleHitRadiusPx;
    private final float mMinSelectionSizePx;

    private boolean mHasSelection = false;
    private int mMode = MODE_NONE;
    private float mLastX;
    private float mLastY;

    public OcrSelectionView(Context context) {
        this(context, null);
    }

    public OcrSelectionView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OcrSelectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchThresholdPx = dp(26);
        mHandleRadiusPx = dp(13);
        mHandleHitRadiusPx = dp(28);
        mMinSelectionSizePx = dp(96);

        mScrimPaint.setColor(0x7A000000);
        mBorderPaint.setColor(0xFF6FA0FF);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(dp(2));

        mHandlePaint.setColor(0xFF8DB7FF);
        mHandlePaint.setStyle(Paint.Style.FILL);

        mGridPaint.setColor(0x556FA0FF);
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(dp(1));
    }

    public void setImageBounds(RectF bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return;
        }
        mImageBounds.set(bounds);
        if (!mHasSelection) {
            resetSelection();
        } else {
            clampRect(mSelectionRect);
            invalidate();
        }
    }

    public void resetSelection() {
        if (mImageBounds.isEmpty()) {
            return;
        }
        float selectionHeight = Math.max(mMinSelectionSizePx, mImageBounds.height() * 0.30f);
        float centerY = mImageBounds.centerY();
        mSelectionRect.set(
                mImageBounds.left,
                centerY - selectionHeight / 2f,
                mImageBounds.right,
                centerY + selectionHeight / 2f
        );
        ensureMinimumSize(mSelectionRect);
        clampRect(mSelectionRect);
        mHasSelection = true;
        invalidate();
    }

    public Rect getSelectionRectInBitmap(int bitmapWidth, int bitmapHeight) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || mImageBounds.isEmpty() || mSelectionRect.isEmpty()) {
            return new Rect(0, 0, bitmapWidth, bitmapHeight);
        }
        float scaleX = bitmapWidth / mImageBounds.width();
        float scaleY = bitmapHeight / mImageBounds.height();
        int left = Math.max(0, Math.round((mSelectionRect.left - mImageBounds.left) * scaleX));
        int top = Math.max(0, Math.round((mSelectionRect.top - mImageBounds.top) * scaleY));
        int right = Math.min(bitmapWidth, Math.round((mSelectionRect.right - mImageBounds.left) * scaleX));
        int bottom = Math.min(bitmapHeight, Math.round((mSelectionRect.bottom - mImageBounds.top) * scaleY));
        if (right <= left) {
            right = Math.min(bitmapWidth, left + 1);
        }
        if (bottom <= top) {
            bottom = Math.min(bitmapHeight, top + 1);
        }
        return new Rect(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mImageBounds.isEmpty() || mSelectionRect.isEmpty()) {
            return;
        }

        canvas.drawRect(mImageBounds.left, mImageBounds.top, mImageBounds.right, mSelectionRect.top, mScrimPaint);
        canvas.drawRect(mImageBounds.left, mSelectionRect.bottom, mImageBounds.right, mImageBounds.bottom, mScrimPaint);
        canvas.drawRect(mImageBounds.left, mSelectionRect.top, mSelectionRect.left, mSelectionRect.bottom, mScrimPaint);
        canvas.drawRect(mSelectionRect.right, mSelectionRect.top, mImageBounds.right, mSelectionRect.bottom, mScrimPaint);

        float thirdWidth = mSelectionRect.width() / 3f;
        float thirdHeight = mSelectionRect.height() / 3f;
        canvas.drawRect(mSelectionRect, mBorderPaint);
        canvas.drawLine(mSelectionRect.left + thirdWidth, mSelectionRect.top, mSelectionRect.left + thirdWidth, mSelectionRect.bottom, mGridPaint);
        canvas.drawLine(mSelectionRect.left + thirdWidth * 2, mSelectionRect.top, mSelectionRect.left + thirdWidth * 2, mSelectionRect.bottom, mGridPaint);
        canvas.drawLine(mSelectionRect.left, mSelectionRect.top + thirdHeight, mSelectionRect.right, mSelectionRect.top + thirdHeight, mGridPaint);
        canvas.drawLine(mSelectionRect.left, mSelectionRect.top + thirdHeight * 2, mSelectionRect.right, mSelectionRect.top + thirdHeight * 2, mGridPaint);

        drawHandle(canvas, mSelectionRect.left, mSelectionRect.top);
        drawHandle(canvas, mSelectionRect.right, mSelectionRect.top);
        drawHandle(canvas, mSelectionRect.left, mSelectionRect.bottom);
        drawHandle(canvas, mSelectionRect.right, mSelectionRect.bottom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mImageBounds.isEmpty() || mSelectionRect.isEmpty()) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mMode = resolveMode(x, y);
                if (mMode == MODE_NONE) {
                    return false;
                }
                mLastX = x;
                mLastY = y;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mMode == MODE_NONE) {
                    return false;
                }
                updateSelection(x - mLastX, y - mLastY);
                mLastX = x;
                mLastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mMode = MODE_NONE;
                return true;
            default:
                return false;
        }
    }

    private void updateSelection(float dx, float dy) {
        RectF next = new RectF(mSelectionRect);
        switch (mMode) {
            case MODE_MOVE:
                next.offset(dx, dy);
                break;
            case MODE_LEFT:
                next.left += dx;
                break;
            case MODE_TOP:
                next.top += dy;
                break;
            case MODE_RIGHT:
                next.right += dx;
                break;
            case MODE_BOTTOM:
                next.bottom += dy;
                break;
            case MODE_LEFT_TOP:
                next.left += dx;
                next.top += dy;
                break;
            case MODE_RIGHT_TOP:
                next.right += dx;
                next.top += dy;
                break;
            case MODE_LEFT_BOTTOM:
                next.left += dx;
                next.bottom += dy;
                break;
            case MODE_RIGHT_BOTTOM:
                next.right += dx;
                next.bottom += dy;
                break;
            default:
                return;
        }
        ensureMinimumSize(next);
        clampRect(next);
        mSelectionRect.set(next);
    }

    private int resolveMode(float x, float y) {
        if (isNearHandle(x, y, mSelectionRect.left, mSelectionRect.top)) return MODE_LEFT_TOP;
        if (isNearHandle(x, y, mSelectionRect.right, mSelectionRect.top)) return MODE_RIGHT_TOP;
        if (isNearHandle(x, y, mSelectionRect.left, mSelectionRect.bottom)) return MODE_LEFT_BOTTOM;
        if (isNearHandle(x, y, mSelectionRect.right, mSelectionRect.bottom)) return MODE_RIGHT_BOTTOM;

        boolean nearLeft = Math.abs(x - mSelectionRect.left) <= mTouchThresholdPx;
        boolean nearRight = Math.abs(x - mSelectionRect.right) <= mTouchThresholdPx;
        boolean nearTop = Math.abs(y - mSelectionRect.top) <= mTouchThresholdPx;
        boolean nearBottom = Math.abs(y - mSelectionRect.bottom) <= mTouchThresholdPx;
        if (nearLeft && withinVerticalBounds(y)) return MODE_LEFT;
        if (nearRight && withinVerticalBounds(y)) return MODE_RIGHT;
        if (nearTop && withinHorizontalBounds(x)) return MODE_TOP;
        if (nearBottom && withinHorizontalBounds(x)) return MODE_BOTTOM;

        if (!containsWithSlop(mImageBounds, x, y, mTouchThresholdPx)) {
            return MODE_NONE;
        }
        if (mSelectionRect.contains(x, y)) return MODE_MOVE;
        return MODE_NONE;
    }

    private boolean isNearHandle(float x, float y, float handleX, float handleY) {
        float dx = x - handleX;
        float dy = y - handleY;
        return dx * dx + dy * dy <= mHandleHitRadiusPx * mHandleHitRadiusPx;
    }

    private boolean containsWithSlop(RectF rect, float x, float y, float slop) {
        return x >= rect.left - slop
                && x <= rect.right + slop
                && y >= rect.top - slop
                && y <= rect.bottom + slop;
    }

    private boolean withinHorizontalBounds(float x) {
        return x >= mSelectionRect.left - mTouchThresholdPx && x <= mSelectionRect.right + mTouchThresholdPx;
    }

    private boolean withinVerticalBounds(float y) {
        return y >= mSelectionRect.top - mTouchThresholdPx && y <= mSelectionRect.bottom + mTouchThresholdPx;
    }

    private void ensureMinimumSize(RectF rect) {
        if (rect.width() < mMinSelectionSizePx) {
            float centerX = rect.centerX();
            rect.left = centerX - mMinSelectionSizePx / 2f;
            rect.right = centerX + mMinSelectionSizePx / 2f;
        }
        if (rect.height() < mMinSelectionSizePx) {
            float centerY = rect.centerY();
            rect.top = centerY - mMinSelectionSizePx / 2f;
            rect.bottom = centerY + mMinSelectionSizePx / 2f;
        }
    }

    private void clampRect(RectF rect) {
        if (rect.width() > mImageBounds.width()) {
            rect.left = mImageBounds.left;
            rect.right = mImageBounds.right;
        }
        if (rect.height() > mImageBounds.height()) {
            rect.top = mImageBounds.top;
            rect.bottom = mImageBounds.bottom;
        }
        if (rect.left < mImageBounds.left) {
            rect.offset(mImageBounds.left - rect.left, 0);
        }
        if (rect.top < mImageBounds.top) {
            rect.offset(0, mImageBounds.top - rect.top);
        }
        if (rect.right > mImageBounds.right) {
            rect.offset(mImageBounds.right - rect.right, 0);
        }
        if (rect.bottom > mImageBounds.bottom) {
            rect.offset(0, mImageBounds.bottom - rect.bottom);
        }
        rect.left = Math.max(rect.left, mImageBounds.left);
        rect.top = Math.max(rect.top, mImageBounds.top);
        rect.right = Math.min(rect.right, mImageBounds.right);
        rect.bottom = Math.min(rect.bottom, mImageBounds.bottom);
    }

    private void drawHandle(Canvas canvas, float cx, float cy) {
        canvas.drawCircle(cx, cy, mHandleRadiusPx, mHandlePaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
