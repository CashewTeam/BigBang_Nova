package com.cashewteam.novatext.android;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;

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

        /**
         * @param cursorScreenX mapped screen X for the blue insertion line
         * @param cursorScreenY mapped screen Y for the blue insertion line
         * @param fingerScreenY unadjusted finger Y, used only for edge scrolling
         */
        void onCursorHandleDrag(float cursorScreenX, float cursorScreenY, float fingerScreenY);

        void onCursorHandleDragEnd();

        /** A short press toggles the optional paste affordance above the cursor. */
        void onCursorHandleTap();

        void onCursorSpace();

        /** @return whether a character was removed and repeating may continue. */
        boolean onCursorBackspace();

        void onCursorEnter();

        void onCursorPaste();

        void onCursorSymbol(float cursorX, float cursorTop, float cursorBottom);
    }

    private final ImageView mHandle;
    private final ImageView mBlinkCursor;
    private final ImageView mSpace;
    private final ImageView mSymbol;
    private final ImageView mDelete;
    private final ImageView mEnter;
    private final ImageView mPaste;
    private final ArrayList<View> mActions = new ArrayList<View>();
    private final int mHandleWidth;
    private final int mHandleHeight;
    private final int mActionWidth;
    private final int mActionHeight;
    private final int mActionGap;
    private final int mEdgeInset;
    private final int mContentLeftInset;
    private final int mContentRightInset;
    private final int mTouchSlop;
    private final int mCursorBlinkWidth;
    private final int mCursorBlinkHeight;
    private final int mCursorBlinkTopInset;
    private final int mCursorTopProtrusion;

    private Callback mCallback;
    private float mCursorX;
    private float mCursorTop;
    private float mCursorBottom;
    private float mHandleCenterX;
    private boolean mCursorVisible;
    private boolean mBlinkOn;
    private boolean mControlsBelowCursor;
    private boolean mHandleDragging;
    private boolean mHandleMovedBeyondSlop;
    private boolean mDeletePressed;
    private boolean mPasteAvailable;
    private boolean mPasteShown;
    private float mHandleDownRawX;
    private float mHandleDownRawY;
    private float mHandleLastRawX;
    private float mHandleLastRawY;
    private float mCursorDownScreenX;
    private float mCursorDownScreenY;

    private final Runnable mStartDragRunnable = new Runnable() {
        @Override
        public void run() {
            // hideCursor() cancels this callback, and this guard covers a
            // callback that was already dequeued when the editor is frozen.
            if (mCursorVisible && mCallback != null) {
                mHandleDragging = true;
                // Keep the insertion line visible while the user's attention
                // is on drag placement; the regular blink resumes on drop.
                mBlinkOn = true;
                updateBlinkCursorVisibility();
                setPasteShown(false);
                removeCallbacks(mBlinkRunnable);
                mCallback.onCursorHandleDragStart();
                dispatchMappedCursorDrag();
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
            updateBlinkCursorVisibility();
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
        // Keep the original xxhdpi assets at their intrinsic, density-scaled size.
        // The old layout used wrap_content for every one of these views.
        mHandleWidth = getDrawableWidth(R.drawable.boom_cursor_without_line, 47f * density);
        mHandleHeight = getDrawableHeight(R.drawable.boom_cursor_without_line, 109f * density);
        mActionWidth = getDrawableWidth(R.drawable.boom_chips_all_space, 47f * density);
        mActionHeight = getDrawableHeight(R.drawable.boom_chips_all_space, 51f * density);
        mCursorBlinkWidth = getDrawableWidth(R.drawable.boom_cursor, 7f * density);
        mCursorBlinkHeight = Math.round(20f * density);
        mCursorBlinkTopInset = Math.round(14f * density);
        // The original white stem rises slightly above its word chip before
        // the short blue insertion bar aligns with the text line.
        mCursorTopProtrusion = Math.round(5f * density);
        mActionGap = Math.round(7f * density);
        mEdgeInset = Math.round(8f * density);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        // The original cursor changes its five-button order against the text
        // content bounds, rather than only when it touches the physical edge.
        mContentLeftInset = getResources().getDimensionPixelSize(R.dimen.page_margin_left);
        mContentRightInset = getResources().getDimensionPixelSize(R.dimen.page_margin_right);

        mHandle = new ImageView(getContext());
        mHandle.setImageResource(R.drawable.boom_cursor_without_line);
        mHandle.setScaleType(ImageView.ScaleType.FIT_XY);
        mHandle.setClickable(true);
        mHandle.setFocusable(true);
        mHandle.setContentDescription("拖动光标");
        addView(mHandle, new FrameLayout.LayoutParams(mHandleWidth, mHandleHeight));
        mBlinkCursor = new ImageView(getContext());
        mBlinkCursor.setImageResource(R.drawable.boom_cursor);
        mBlinkCursor.setScaleType(ImageView.ScaleType.CENTER);
        mBlinkCursor.setEnabled(false);
        mBlinkCursor.setVisibility(INVISIBLE);
        addView(mBlinkCursor, new FrameLayout.LayoutParams(mCursorBlinkWidth, mCursorBlinkHeight));
        mHandle.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        animatePress(view, true);
                        view.setPressed(true);
                        mHandleDragging = false;
                        mHandleMovedBeyondSlop = false;
                        mHandleDownRawX = event.getRawX();
                        mHandleDownRawY = event.getRawY();
                        mHandleLastRawX = mHandleDownRawX;
                        mHandleLastRawY = mHandleDownRawY;
                        final int[] cursorHostLocation = new int[2];
                        getLocationOnScreen(cursorHostLocation);
                        // The large gray knob is far below the text line. Keep
                        // this initial gap throughout the gesture so the blue
                        // insertion line, rather than the knob, tracks text.
                        mCursorDownScreenX = cursorHostLocation[0] + mCursorX;
                        mCursorDownScreenY = cursorHostLocation[1]
                                + (mCursorTop + mCursorBottom) / 2f;
                        postDelayed(mStartDragRunnable, 150L);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mHandleLastRawX = event.getRawX();
                        mHandleLastRawY = event.getRawY();
                        if (!mHandleMovedBeyondSlop) {
                            mHandleMovedBeyondSlop = exceedsTouchSlop(
                                    mHandleDownRawX,
                                    mHandleDownRawY,
                                    mHandleLastRawX,
                                    mHandleLastRawY,
                                    mTouchSlop
                            );
                        }
                        if (mHandleDragging && mCallback != null) {
                            dispatchMappedCursorDrag();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        final boolean wasDragging = mHandleDragging;
                        animatePress(view, false);
                        view.setPressed(false);
                        removeCallbacks(mStartDragRunnable);
                        if (wasDragging && mCallback != null) {
                            mCallback.onCursorHandleDragEnd();
                        }
                        mHandleDragging = false;
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            view.performClick();
                            if (!wasDragging && !mHandleMovedBeyondSlop && mCallback != null) {
                                mCallback.onCursorHandleTap();
                            }
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });

        mSpace = createImageButton(
                R.drawable.boom_chips_all_space,
                R.drawable.cursor_back_black_selector_extend
        );
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

        mSymbol = createImageButton(
                R.drawable.boom_chips_all_sym,
                R.drawable.cursor_back_black_selector_extend
        );
        mSymbol.setContentDescription("符号面板");
        mSymbol.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onCursorSymbol(mCursorX, mCursorTop, mCursorBottom);
                }
            }
        });
        addAction(mSymbol);

        mDelete = createImageButton(
                R.drawable.boom_chips_cursor_delete,
                R.drawable.cursor_back_red_selector_extend
        );
        mDelete.setContentDescription("删除前一个字符");
        mDelete.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        animatePress(view, true);
                        view.setPressed(true);
                        mDeletePressed = true;
                        if (mCallback != null && mCallback.onCursorBackspace()) {
                            postDelayed(mDeleteRepeatRunnable, ViewConfiguration.getLongPressTimeout());
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        animatePress(view, false);
                        view.setPressed(false);
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

        mEnter = createImageButton(
                R.drawable.boom_chips_cursor_enter,
                R.drawable.cursor_back_black_selector_extend
        );
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

        mPaste = createImageButton(
                R.drawable.boom_extend_paste,
                R.drawable.cursor_back_black_selector_extend
        );
        mPaste.setContentDescription("粘贴");
        mPaste.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setPasteShown(false);
                if (mCallback != null) {
                    mCallback.onCursorPaste();
                }
            }
        });
        mPaste.setVisibility(GONE);
        addView(mPaste, new FrameLayout.LayoutParams(mActionWidth, mActionHeight));

        setVisibility(GONE);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void showCursor(float x, float top, float bottom, boolean pasteAvailable) {
        mCursorVisible = true;
        mBlinkOn = true;
        mCursorX = x;
        mCursorTop = top;
        mCursorBottom = Math.max(top + 1f, bottom);
        // Matching Smartisan BigBang: clipboard availability only makes the
        // paste icon eligible; a tap on the cursor handle reveals it.
        mPasteAvailable = pasteAvailable;
        if (!mPasteAvailable) {
            mPasteShown = false;
        }
        updatePasteVisibility();
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        removeCallbacks(mBlinkRunnable);
        if (!mHandleDragging) {
            postDelayed(mBlinkRunnable, CURSOR_BLINK_DELAY_MS);
        }
        updateBlinkCursorVisibility();
        requestLayout();
        invalidate();
    }

    public void hideCursor() {
        mCursorVisible = false;
        mHandleDragging = false;
        mHandleMovedBeyondSlop = false;
        mDeletePressed = false;
        mPasteAvailable = false;
        mPasteShown = false;
        updatePasteVisibility();
        updateBlinkCursorVisibility();
        removeCallbacks(mStartDragRunnable);
        removeCallbacks(mBlinkRunnable);
        removeCallbacks(mDeleteRepeatRunnable);
        setVisibility(GONE);
    }

    public boolean isCursorVisible() {
        return mCursorVisible;
    }

    /** Toggles paste only after an explicit cursor-handle tap. */
    public void togglePaste() {
        if (!mPasteAvailable) {
            setPasteShown(false);
            return;
        }
        setPasteShown(!mPasteShown);
    }

    /** Hides a previously revealed paste affordance when its cursor offset changes. */
    public void hidePaste() {
        setPasteShown(false);
    }

    private void setPasteShown(boolean shown) {
        mPasteShown = shown && mPasteAvailable;
        updatePasteVisibility();
        requestLayout();
    }

    private void updatePasteVisibility() {
        mPaste.setVisibility(mPasteShown ? VISIBLE : GONE);
    }

    /** Visible editor bottom after IME insets; used to place the symbol panel. */
    public int getVisibleBottomForEditor() {
        return getVisibleBottom(getHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        mHandle.measure(MeasureSpec.makeMeasureSpec(mHandleWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mHandleHeight, MeasureSpec.EXACTLY));
        mBlinkCursor.measure(MeasureSpec.makeMeasureSpec(mCursorBlinkWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mCursorBlinkHeight, MeasureSpec.EXACTLY));
        for (View action : mActions) {
            action.measure(MeasureSpec.makeMeasureSpec(mActionWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mActionHeight, MeasureSpec.EXACTLY));
        }
        mPaste.measure(MeasureSpec.makeMeasureSpec(mActionWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mActionHeight, MeasureSpec.EXACTLY));
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
        final int controlsWidth = visibleActionCount * mActionWidth + mHandleWidth
                + visibleActionCount * mActionGap;
        final int belowTop = Math.round(mCursorTop) - mCursorTopProtrusion;
        final int aboveTop = Math.round(mCursorBottom) - mHandleHeight;
        mControlsBelowCursor = belowTop + mHandleHeight + mEdgeInset <= visibleBottom
                || aboveTop < mEdgeInset;
        final int handleTop = clamp(
                mControlsBelowCursor ? belowTop : aboveTop,
                mEdgeInset,
                Math.max(mEdgeInset, visibleBottom - mHandleHeight - mEdgeInset)
        );
        final int handleSlot = getCursorHandleSlot(
                mCursorX,
                width,
                controlsWidth,
                mActionWidth,
                mActionGap,
                mContentLeftInset,
                mContentRightInset
        );
        final int controlsLeft = clamp(
                Math.round(mCursorX - mActionWidth / 2f
                        - handleSlot * (mActionWidth + mActionGap)),
                mEdgeInset,
                Math.max(mEdgeInset, width - controlsWidth - mEdgeInset)
        );
        final int handleLeft = controlsLeft + handleSlot * (mActionWidth + mActionGap)
                + (mActionWidth - mHandleWidth) / 2;
        mHandle.setRotation(mControlsBelowCursor ? 0f : 180f);
        mHandle.layout(handleLeft, handleTop, handleLeft + mHandleWidth, handleTop + mHandleHeight);
        final int blinkTop = mControlsBelowCursor
                ? handleTop + mCursorBlinkTopInset
                : handleTop + mHandleHeight - mCursorBlinkTopInset - mCursorBlinkHeight;
        final int blinkLeft = handleLeft + (mHandleWidth - mCursorBlinkWidth) / 2;
        mBlinkCursor.layout(blinkLeft, blinkTop,
                blinkLeft + mCursorBlinkWidth, blinkTop + mCursorBlinkHeight);
        mHandleCenterX = handleLeft + mHandleWidth / 2f;
        final int actionTop = mControlsBelowCursor
                ? handleTop + mHandleHeight - mActionHeight
                : handleTop;

        // Original five-slot ordering: cursor swaps places with the four
        // actions as it approaches an edge, keeping every button reachable.
        layoutAction(mSpace, handleSlot == 0 ? 1 : 0, controlsLeft, actionTop);
        layoutAction(mSymbol, handleSlot <= 1 ? 2 : 1, controlsLeft, actionTop);
        layoutAction(mDelete, handleSlot <= 2 ? 3 : 2, controlsLeft, actionTop);
        layoutAction(mEnter, handleSlot <= 3 ? 4 : 3, controlsLeft, actionTop);
        if (mPaste.getVisibility() == VISIBLE) {
            final int pasteTop = clamp(
                    mControlsBelowCursor ? handleTop - mActionHeight - mActionGap
                            : handleTop + mHandleHeight + mActionGap,
                    mEdgeInset,
                    Math.max(mEdgeInset, visibleBottom - mActionHeight - mEdgeInset)
            );
            final int pasteLeft = clamp(
                    Math.round(mHandleCenterX) - mActionWidth / 2,
                    mEdgeInset,
                    Math.max(mEdgeInset, width - mActionWidth - mEdgeInset)
            );
            mPaste.layout(pasteLeft, pasteTop,
                    pasteLeft + mActionWidth, pasteTop + mActionHeight);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void addAction(ImageView action) {
        mActions.add(action);
        addView(action, new FrameLayout.LayoutParams(mActionWidth, mActionHeight));
    }

    private ImageView createImageButton(int iconRes, int backgroundRes) {
        ImageView view = new ImageView(getContext());
        view.setImageResource(iconRes);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setBackgroundResource(backgroundRes);
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    animatePress(touched, true);
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    animatePress(touched, false);
                }
                return false;
            }
        });
        return view;
    }

    private void animatePress(View view, boolean pressed) {
        view.animate().cancel();
        view.animate()
                .scaleX(pressed ? 0.92f : 1f)
                .scaleY(pressed ? 0.92f : 1f)
                .setDuration(pressed ? 70L : 120L)
                .start();
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

    private void layoutAction(View action, int slot, int controlsLeft, int actionTop) {
        if (action.getVisibility() != VISIBLE) {
            action.layout(0, 0, 0, 0);
            return;
        }
        final int actionLeft = controlsLeft + slot * (mActionWidth + mActionGap);
        action.layout(actionLeft, actionTop,
                actionLeft + mActionWidth, actionTop + mActionHeight);
    }

    private void updateBlinkCursorVisibility() {
        mBlinkCursor.setVisibility(mCursorVisible && mBlinkOn ? VISIBLE : INVISIBLE);
    }

    /**
     * Chooses the original cursor's slot among five controls.  Slot 0 is the
     * leftmost position and slot 4 the rightmost; the preferred middle slot is
     * retained whenever the whole control strip still fits beside the text.
     */
    static int getCursorHandleSlot(float cursorX, int viewportWidth, int controlsWidth,
            int actionWidth, int actionGap, int contentLeftInset, int contentRightInset) {
        final int step = actionWidth + actionGap;
        final float desiredLeftAtSlotZero = cursorX - actionWidth / 2f;
        final int minimumSlot = Math.max(0, (int) Math.ceil(
                (desiredLeftAtSlotZero - (viewportWidth - contentRightInset - controlsWidth))
                        / step
        ));
        final int maximumSlot = Math.min(4, (int) Math.floor(
                (desiredLeftAtSlotZero - contentLeftInset) / step
        ));
        if (minimumSlot > maximumSlot) {
            return cursorX <= viewportWidth / 2f ? 0 : 4;
        }
        return clampStatic(2, minimumSlot, maximumSlot);
    }

    /** Pure transform used by the drag gesture and unit tests. */
    static float mapHandleDragCoordinate(float pointerDown, float pointerNow, float cursorDown) {
        return cursorDown + (pointerNow - pointerDown);
    }

    /** Uses Android's system threshold so motion noise cannot invoke paste. */
    static boolean exceedsTouchSlop(float downX, float downY, float currentX, float currentY,
            int touchSlop) {
        final float dx = currentX - downX;
        final float dy = currentY - downY;
        return dx * dx + dy * dy > touchSlop * touchSlop;
    }

    private void dispatchMappedCursorDrag() {
        if (mCallback == null) {
            return;
        }
        mCallback.onCursorHandleDrag(
                mapHandleDragCoordinate(mHandleDownRawX, mHandleLastRawX, mCursorDownScreenX),
                mapHandleDragCoordinate(mHandleDownRawY, mHandleLastRawY, mCursorDownScreenY),
                mHandleLastRawY
        );
    }

    private int getDrawableWidth(int drawableRes, float fallback) {
        final Drawable drawable = getResources().getDrawable(drawableRes);
        return drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : Math.round(fallback);
    }

    private int getDrawableHeight(int drawableRes, float fallback) {
        final Drawable drawable = getResources().getDrawable(drawableRes);
        return drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : Math.round(fallback);
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
        return clampStatic(value, min, max);
    }

    private static int clampStatic(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
