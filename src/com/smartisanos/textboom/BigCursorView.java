package com.cashewteam.novatext.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

/**
 * The editor's floating cursor controls.  It deliberately owns only drawing and
 * touch feedback; {@link BoomChipPage} remains the single owner of text and
 * cursor-offset state.
 */
public final class BigCursorView extends FrameLayout {
    private static final long CURSOR_BLINK_DELAY_MS = 500L;
    private static final long DELETE_REPEAT_DELAY_MS = 50L;

    public interface Callback {
        void onCursorHandleDragStart();

        void onCursorHandleDrag(float rawX, float rawY);

        void onCursorHandleDragEnd();

        void onCursorSpace();

        /** @return whether a character was removed and repeating may continue. */
        boolean onCursorBackspace();

        void onCursorEnter();

        void onCursorPaste();
    }

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextView mHandle;
    private final TextView mSpace;
    private final TextView mDelete;
    private final TextView mEnter;
    private final TextView mPaste;
    private final ArrayList<View> mActions = new ArrayList<View>();
    private final int mHandleSize;
    private final int mActionSize;
    private final int mActionGap;
    private final int mConnectorGap;
    private final int mEdgeInset;

    private Callback mCallback;
    private float mCursorX;
    private float mCursorTop;
    private float mCursorBottom;
    private float mHandleCenterX;
    private float mHandleCenterY;
    private boolean mCursorVisible;
    private boolean mBlinkOn;
    private boolean mControlsBelowCursor;
    private boolean mHandleDragging;
    private boolean mDeletePressed;

    private final Runnable mStartDragRunnable = new Runnable() {
        @Override
        public void run() {
            // hideCursor() cancels this callback, and this guard covers a
            // callback that was already dequeued when the editor is frozen.
            if (mCursorVisible && mCallback != null) {
                mHandleDragging = true;
                mCallback.onCursorHandleDragStart();
            }
        }
    };

    private final Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mCursorVisible) {
                return;
            }
            mBlinkOn = !mBlinkOn;
            invalidate();
            postDelayed(this, CURSOR_BLINK_DELAY_MS);
        }
    };

    private final Runnable mDeleteRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mDeletePressed || mCallback == null || !mCallback.onCursorBackspace()) {
                return;
            }
            postDelayed(this, DELETE_REPEAT_DELAY_MS);
        }
    };

    public BigCursorView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        final float density = getResources().getDisplayMetrics().density;
        mHandleSize = Math.round(44f * density);
        mActionSize = Math.round(42f * density);
        mActionGap = Math.round(8f * density);
        mConnectorGap = Math.round(10f * density);
        mEdgeInset = Math.round(8f * density);

        mLinePaint.setColor(Color.rgb(82, 132, 238));
        mLinePaint.setStrokeWidth(Math.max(2f * density, 1f));
        mLinePaint.setStrokeCap(Paint.Cap.ROUND);

        mHandle = createButton("≡", 26f, Color.rgb(39, 43, 50));
        mHandle.setContentDescription("拖动光标");
        addView(mHandle, new FrameLayout.LayoutParams(mHandleSize, mHandleSize));
        mHandle.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mHandleDragging = false;
                        postDelayed(mStartDragRunnable, 150L);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (mHandleDragging && mCallback != null) {
                            mCallback.onCursorHandleDrag(event.getRawX(), event.getRawY());
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        removeCallbacks(mStartDragRunnable);
                        if (mHandleDragging && mCallback != null) {
                            mCallback.onCursorHandleDragEnd();
                        }
                        mHandleDragging = false;
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            view.performClick();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });

        mSpace = createButton("空格", 13f, Color.rgb(39, 43, 50));
        mSpace.setContentDescription("插入空格");
        mSpace.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onCursorSpace();
                }
            }
        });
        addAction(mSpace);

        mDelete = createButton("⌫", 25f, Color.rgb(214, 81, 73));
        mDelete.setContentDescription("删除前一个字符");
        mDelete.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mDeletePressed = true;
                        if (mCallback != null && mCallback.onCursorBackspace()) {
                            postDelayed(mDeleteRepeatRunnable, ViewConfiguration.getLongPressTimeout());
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mDeletePressed = false;
                        removeCallbacks(mDeleteRepeatRunnable);
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            view.performClick();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        addAction(mDelete);

        mEnter = createButton("↵", 27f, Color.rgb(39, 43, 50));
        mEnter.setContentDescription("插入换行");
        mEnter.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onCursorEnter();
                }
            }
        });
        addAction(mEnter);

        mPaste = createButton("粘贴", 13f, Color.rgb(39, 43, 50));
        mPaste.setContentDescription("粘贴");
        mPaste.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onCursorPaste();
                }
            }
        });
        mPaste.setVisibility(GONE);
        addAction(mPaste);

        setVisibility(GONE);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void showCursor(float x, float top, float bottom, boolean showPaste) {
        mCursorVisible = true;
        mBlinkOn = true;
        mCursorX = x;
        mCursorTop = top;
        mCursorBottom = Math.max(top + 1f, bottom);
        mPaste.setVisibility(showPaste ? VISIBLE : GONE);
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        removeCallbacks(mBlinkRunnable);
        postDelayed(mBlinkRunnable, CURSOR_BLINK_DELAY_MS);
        requestLayout();
        invalidate();
    }

    public void hideCursor() {
        mCursorVisible = false;
        mHandleDragging = false;
        mDeletePressed = false;
        removeCallbacks(mStartDragRunnable);
        removeCallbacks(mBlinkRunnable);
        removeCallbacks(mDeleteRepeatRunnable);
        setVisibility(GONE);
    }

    public boolean isCursorVisible() {
        return mCursorVisible;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        mHandle.measure(MeasureSpec.makeMeasureSpec(mHandleSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mHandleSize, MeasureSpec.EXACTLY));
        for (View action : mActions) {
            action.measure(MeasureSpec.makeMeasureSpec(mActionSize, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mActionSize, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!mCursorVisible) {
            return;
        }
        final int width = getWidth();
        final int height = getHeight();
        final int visibleBottom = getVisibleBottom(height);
        final int visibleActionCount = getVisibleActionCount();
        final int controlsHeight = mHandleSize + mActionGap + mActionSize;
        final int belowTop = Math.round(mCursorBottom) + mConnectorGap;
        final int aboveTop = Math.round(mCursorTop) - mConnectorGap - controlsHeight;
        mControlsBelowCursor = belowTop + controlsHeight + mEdgeInset <= visibleBottom
                || aboveTop < mEdgeInset;
        final int handleTop = clamp(
                mControlsBelowCursor ? belowTop : aboveTop + mActionSize + mActionGap,
                mEdgeInset,
                Math.max(mEdgeInset, visibleBottom - mHandleSize - mEdgeInset)
        );
        final int handleLeft = clamp(
                Math.round(mCursorX) - mHandleSize / 2,
                mEdgeInset,
                Math.max(mEdgeInset, width - mHandleSize - mEdgeInset)
        );
        mHandle.layout(handleLeft, handleTop, handleLeft + mHandleSize, handleTop + mHandleSize);
        mHandleCenterX = handleLeft + mHandleSize / 2f;
        mHandleCenterY = handleTop + mHandleSize / 2f;

        final int actionsWidth = visibleActionCount * mActionSize
                + Math.max(0, visibleActionCount - 1) * mActionGap;
        int actionLeft = clamp(
                Math.round(mCursorX) - actionsWidth / 2,
                mEdgeInset,
                Math.max(mEdgeInset, width - actionsWidth - mEdgeInset)
        );
        final int actionTop = clamp(
                mControlsBelowCursor ? handleTop + mHandleSize + mActionGap : handleTop - mActionGap - mActionSize,
                mEdgeInset,
                Math.max(mEdgeInset, visibleBottom - mActionSize - mEdgeInset)
        );
        for (View action : mActions) {
            if (action.getVisibility() != VISIBLE) {
                action.layout(0, 0, 0, 0);
                continue;
            }
            action.layout(actionLeft, actionTop, actionLeft + mActionSize, actionTop + mActionSize);
            actionLeft += mActionSize + mActionGap;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mCursorVisible) {
            return;
        }
        final float connectorStart = mControlsBelowCursor ? mCursorBottom : mCursorTop;
        final float connectorEnd = mControlsBelowCursor
                ? mHandle.getTop() : mHandle.getBottom();
        canvas.drawLine(mCursorX, connectorStart, mHandleCenterX, connectorEnd, mLinePaint);
        if (mBlinkOn) {
            canvas.drawLine(mCursorX, mCursorTop, mCursorX, mCursorBottom, mLinePaint);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void addAction(TextView action) {
        mActions.add(action);
        addView(action, new FrameLayout.LayoutParams(mActionSize, mActionSize));
    }

    private TextView createButton(String text, float textSizeSp, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(textSizeSp);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        view.setBackground(background);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private int getVisibleActionCount() {
        int count = 0;
        for (View action : mActions) {
            if (action.getVisibility() == VISIBLE) {
                ++count;
            }
        }
        return count;
    }

    private int getVisibleBottom(int height) {
        final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(this);
        if (rootInsets == null) {
            return height;
        }
        // The full-screen helper is edge-to-edge, so the cursor controls must reserve IME space.
        return Math.max(mEdgeInset, height - rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
