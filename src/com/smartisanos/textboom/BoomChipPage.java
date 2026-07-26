package com.cashewteam.novatext.android;

import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cashewteam.novatext.android.BoomActivity;
import com.cashewteam.novatext.android.BoomWordsLayout;
import com.cashewteam.novatext.android.BoomAnimator;
import com.cashewteam.novatext.android.SwipeSelectView;
import com.cashewteam.novatext.android.BoomActionHandler;
import com.cashewteam.novatext.android.data.BigBangSettings;
import com.cashewteam.novatext.android.domain.capture.TextSessionCoordinator;
import com.cashewteam.novatext.android.util.LogUtils;

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
    private final FrameLayout mEditorOverlayHost;
    private final BigCursorView mBigCursorView;
    private final EditorInputView mEditInput;
    private final ClipboardManager mClipboard;
    private final ClipboardManager.OnPrimaryClipChangedListener mClipboardListener;
    private SymbolPanelPopup mSymbolPanelPopup;

    private boolean mSynchronizingEditInput;
    private String mInputBuffer = "";
    private int mInputBufferOffset;
    private boolean mCursorDragActive;
    private int mCursorAutoScrollVelocity;
    private float mCursorDragCaretX;
    private float mCursorDragCaretY;
    private float mCursorDragFingerY;
    private boolean mCursorUpdatePending;
    private boolean mCursorUpdateNeedsVisibility;
    private Runnable mCursorAutoScrollRunnable;

    Serializable mSavedData;
    private EditSessionState mEditSession;
    private boolean mEditCommitPending;
    private OnEditUiStateListener mOnEditUiStateListener;
    private OnAdjacentRequestListener mOnAdjacentRequestListener;
    private boolean mAdjacentLoading;
    private float mAdjacentOffset;

    private int mTouchedX;
    private int mTouchedY;

    private interface EditorInputCallback {
        boolean onInputDeleteBefore(int count, boolean inCodePoints);

        boolean onInputDeleteAfter(int count, boolean inCodePoints);

        boolean onInputDeleteSurrounding(int beforeCount, int afterCount,
                boolean inCodePoints, int inputSelectionStart);

        void onInputEnter();

        void onInputSelectionChanged(int selectionStart, int selectionEnd);
    }

    /**
     * A transparent EditText keeps Android's normal IME input connection while
     * the visible text continues to be rendered as BigBang chips.
     */
    private static final class EditorInputView extends EditText {
        private EditorInputCallback mCallback;

        EditorInputView(Context context) {
            super(context);
        }

        void setEditorInputCallback(EditorInputCallback callback) {
            mCallback = callback;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            final InputConnection target = super.onCreateInputConnection(outAttrs);
            outAttrs.imeOptions = (outAttrs.imeOptions & ~EditorInfo.IME_MASK_ACTION)
                    | EditorInfo.IME_ACTION_NONE;
            return new InputConnectionWrapper(target, true) {
                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    if (deleteSurroundingMainText(beforeLength, afterLength, false)) {
                        return true;
                    }
                    return super.deleteSurroundingText(beforeLength, afterLength);
                }

                @Override
                public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                    if (deleteSurroundingMainText(beforeLength, afterLength, true)) {
                        return true;
                    }
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
                }

                @Override
                public boolean sendKeyEvent(KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && mCallback != null) {
                        final int selection = Math.max(0, getSelectionStart());
                        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL && selection == 0) {
                            return mCallback.onInputDeleteBefore(1, true);
                        }
                        if (event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL && selection >= getBufferLength()) {
                            return mCallback.onInputDeleteAfter(1, true);
                        }
                        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                            mCallback.onInputEnter();
                            return true;
                        }
                    }
                    return super.sendKeyEvent(event);
                }
            };
        }

        @Override
        protected void onSelectionChanged(int selectionStart, int selectionEnd) {
            super.onSelectionChanged(selectionStart, selectionEnd);
            if (mCallback != null) {
                mCallback.onInputSelectionChanged(selectionStart, selectionEnd);
            }
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (mCallback != null) {
                final int selection = Math.max(0, getSelectionStart());
                if (keyCode == KeyEvent.KEYCODE_DEL && selection == 0) {
                    return mCallback.onInputDeleteBefore(1, true);
                }
                if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL && selection >= getBufferLength()) {
                    return mCallback.onInputDeleteAfter(1, true);
                }
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    mCallback.onInputEnter();
                    return true;
                }
            }
            return super.onKeyDown(keyCode, event);
        }

        private int getBufferLength() {
            return getText() == null ? 0 : getText().length();
        }

        private boolean deleteSurroundingMainText(
                int beforeLength, int afterLength, boolean inCodePoints) {
            if (mCallback == null) {
                return false;
            }
            final int bufferLength = getBufferLength();
            final int selectionStart = Math.max(0, Math.min(getSelectionStart(), bufferLength));
            final int selectionEnd = Math.max(selectionStart,
                    Math.min(Math.max(0, getSelectionEnd()), bufferLength));
            final int beforeInBuffer = getInputUnitCount(0, selectionStart, inCodePoints);
            final int afterInBuffer = getInputUnitCount(selectionEnd, bufferLength, inCodePoints);
            if (beforeLength <= beforeInBuffer && afterLength <= afterInBuffer
                    && !deletesPartialInputUnit(
                            beforeLength, afterLength, inCodePoints, selectionStart, selectionEnd)) {
                return false;
            }
            // Resolve the whole requested range at once: the other side may still be in preedit text.
            return mCallback.onInputDeleteSurrounding(
                    beforeLength, afterLength, inCodePoints, selectionStart);
        }

        private int getInputUnitCount(int start, int end, boolean inCodePoints) {
            if (!inCodePoints) {
                return end - start;
            }
            final Editable text = getText();
            return text == null ? 0 : Character.codePointCount(text, start, end);
        }

        private boolean deletesPartialInputUnit(int beforeLength, int afterLength,
                boolean inCodePoints, int selectionStart, int selectionEnd) {
            final Editable text = getText();
            if (text == null) {
                return false;
            }
            final int start = inCodePoints
                    ? moveInputCodePoints(text, selectionStart, -beforeLength)
                    : Math.max(0, selectionStart - beforeLength);
            final int end = inCodePoints
                    ? moveInputCodePoints(text, selectionEnd, afterLength)
                    : Math.min(text.length(), selectionEnd + afterLength);
            return isInsideInputEditableUnit(text, start) || isInsideInputEditableUnit(text, end);
        }

        private int moveInputCodePoints(CharSequence text, int offset, int count) {
            int result = offset;
            int remaining = count;
            while (remaining < 0 && result > 0) {
                result = Character.offsetByCodePoints(text, result, -1);
                ++remaining;
            }
            while (remaining > 0 && result < text.length()) {
                result = Character.offsetByCodePoints(text, result, 1);
                --remaining;
            }
            return result;
        }

        private boolean isInsideInputEditableUnit(CharSequence text, int offset) {
            if (offset <= 0 || offset >= text.length()) {
                return false;
            }
            final char before = text.charAt(offset - 1);
            final char after = text.charAt(offset);
            return Character.isHighSurrogate(before) && Character.isLowSurrogate(after)
                    || before == '\r' && after == '\n';
        }
    }

    OnGlobalLayoutListener mDoBoomAnimation = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(mDoBoomAnimation);
            if (mEnableLegacyMask && mScroller.canScrollVertically(1)) {
                mMask.setVisibility(View.VISIBLE);
            }
            if (restoreSelectedState()) {
                if (DBG) {
                    LogUtils.d(TAG, "Skip boom animation when restoring");
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
                LogUtils.d(TAG, "init Chip and do boom animation");
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
        mEditorOverlayHost = (FrameLayout) mBoomPage;
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
                if (!handleClick() && !isEditMode()) {
                    if (mActivity instanceof BoomActivity) {
                        ((BoomActivity) mActivity).requestAnimatedDismissFromLegacy();
                    } else {
                        mActivity.finish();
                    }
                }
            }
        };
        mBoomPage.setOnClickListener(mDismissClickListener);
        mBoomTable.setOnClickListener(mDismissClickListener);
        mScroller.setOnClickListener(mDismissClickListener);
        mBoomConent.setOnClickListener(mDismissClickListener);
        mCancel.setOnClickListener(mDismissClickListener);
        mBoomActionHandler = new BoomActionHandler(this, mEnableLegacyMask);
        mClipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        mBigCursorView = new BigCursorView(mActivity);
        mBigCursorView.setCallback(new BigCursorView.Callback() {
            @Override
            public void onCursorHandleDragStart() {
                beginCursorDrag();
            }

            @Override
            public void onCursorHandleDrag(
                    float cursorScreenX,
                    float cursorScreenY,
                    float fingerScreenY
            ) {
                moveEditCursorFromScreen(cursorScreenX, cursorScreenY, fingerScreenY, true);
            }

            @Override
            public void onCursorHandleDragEnd() {
                endCursorDrag();
            }

            @Override
            public void onCursorHandleTap() {
                // Clipboard content does not reveal paste by itself; the
                // original editor exposes it only after tapping the handle.
                mBigCursorView.togglePaste();
            }

            @Override
            public void onCursorSpace() {
                insertEditText(" ");
            }

            @Override
            public boolean onCursorBackspace() {
                return deleteEditCodePointBefore();
            }

            @Override
            public void onCursorEnter() {
                insertEditText("\n");
            }

            @Override
            public void onCursorPaste() {
                pasteEditText();
            }

            @Override
            public void onCursorSymbol(float cursorX, float cursorTop, float cursorBottom) {
                toggleSymbolPanel(cursorX, cursorTop, cursorBottom, true);
            }
        });
        mEditorOverlayHost.addView(mBigCursorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mEditInput = new EditorInputView(mActivity);
        mEditInput.setBackgroundColor(Color.TRANSPARENT);
        mEditInput.setTextColor(Color.TRANSPARENT);
        mEditInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0f);
        mEditInput.setCursorVisible(false);
        mEditInput.setSingleLine(false);
        mEditInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        mEditInput.setImeOptions(EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mEditInput.setAlpha(0f);
        mEditInput.setContentDescription(mActivity.getString(R.string.bigbang_edit_input_description));
        mEditInput.setEditorInputCallback(new EditorInputCallback() {
            @Override
            public boolean onInputDeleteBefore(int count, boolean inCodePoints) {
                return deleteEditBefore(count, inCodePoints);
            }

            @Override
            public boolean onInputDeleteAfter(int count, boolean inCodePoints) {
                return deleteEditAfter(count, inCodePoints);
            }

            @Override
            public boolean onInputDeleteSurrounding(int beforeCount, int afterCount,
                    boolean inCodePoints, int inputSelectionStart) {
                return deleteEditSurrounding(
                        beforeCount, afterCount, inCodePoints, inputSelectionStart);
            }

            @Override
            public void onInputEnter() {
                insertEditText("\n");
            }

            @Override
            public void onInputSelectionChanged(int selectionStart, int selectionEnd) {
                onEditInputSelectionChanged(selectionStart, selectionEnd);
            }
        });
        mEditInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                onEditInputChanged(s, start, before, count);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        mEditorOverlayHost.addView(mEditInput, new FrameLayout.LayoutParams(1, 1));
        mClipboardListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                scheduleEditCursorUpdate(false);
                mBoomActionHandler.refreshToolbarForCurrentMode();
            }
        };
        mClipboard.addPrimaryClipChangedListener(mClipboardListener);
        mCursorAutoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!canModifyEditText() || !mCursorDragActive || mCursorAutoScrollVelocity == 0) {
                    return;
                }
                final int scrollBefore = mScroller.getScrollY();
                mScroller.scrollBy(0, mCursorAutoScrollVelocity);
                if (scrollBefore == mScroller.getScrollY()) {
                    // The content edge has been reached; do not keep polling
                    // an already-stopped ScrollView for the rest of this drag.
                    mCursorAutoScrollVelocity = 0;
                    return;
                }
                moveEditCursorFromScreen(
                        mCursorDragCaretX,
                        mCursorDragCaretY,
                        mCursorDragFingerY,
                        false
                );
                mBigCursorView.postDelayed(this, 25L);
            }
        };
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

    /** Keeps the Compose chrome in sync with legacy selection and history state. */
    public interface OnEditUiStateListener {
        void onEditUiStateChanged(
                boolean allSelected,
                boolean selectAllEnabled,
                boolean canUndo,
                boolean canRedo
        );
    }

    public void setOnEditUiStateListener(OnEditUiStateListener listener) {
        mOnEditUiStateListener = listener;
        notifyEditUiStateChanged();
    }

    void notifyEditUiStateChanged() {
        if (mOnEditUiStateListener == null) {
            return;
        }
        mOnEditUiStateListener.onEditUiStateChanged(
                isEditMode() && mBoomActionHandler != null && mBoomActionHandler.isAllSelected(),
                isEditMode() && mLayout.getWordCount() > 0,
                canUndoEdit(),
                canRedoEdit()
        );
    }

    /** Stores a user-driven chip selection for rotation and history snapshots. */
    void syncEditSelectionStateFromHandler() {
        if (!isEditMode()) {
            return;
        }
        final Serializable selection = captureSelectedState();
        mEditSession = new EditSessionState(
                mEditSession.originalText,
                mEditSession.text,
                getEditCursorOffset(),
                selection instanceof int[][] ? (int[][]) selection : null,
                mEditSession.getUndoSnapshots(),
                mEditSession.getRedoSnapshots()
        );
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
        endCursorDrag();
        hideEditorKeyboard();
        dismissSymbolPanel();
        mBigCursorView.hideCursor();
        resetEditInputBuffer(0);
        mEditCommitPending = false;
        mEditSession = null;
        if (mLayout.layoutWords(segment, text, touchedIndex)) {
            mTouchedX = touchedX;
            mTouchedY = touchedY;
            initChips(true);
            return true;
        }
        return false;
    }

    /**
     * Clear all chip views and selection state so that a subsequent
     * {@link #initWords} call can fully reinitialise the page (e.g. when
     * the Activity receives a new Intent via {@code onNewIntent}).
     */
    public void prepareForReinit() {
        endCursorDrag();
        hideEditorKeyboard();
        dismissSymbolPanel();
        mBigCursorView.hideCursor();
        resetEditInputBuffer(0);
        mEditCommitPending = false;
        mEditSession = null;
        finishAdjacentPull();
        if (mBoomActionHandler != null) {
            mBoomActionHandler.clearSelectionStateForRelayout();
        }
        mBoomConent.removeAllViews();
    }

    public boolean isEditMode() {
        return mEditSession != null;
    }

    /** Selection and cursor movement are not edits; only changed text needs a discard prompt. */
    public boolean isEditDirty() {
        return isEditMode() && hasEditTextChanged(mEditSession.originalText, mEditSession.text);
    }

    static boolean hasEditTextChanged(String originalText, String currentText) {
        return originalText == null ? currentText != null : !originalText.equals(currentText);
    }

    /** Blocks all text mutations while Activity re-segments a pending edit commit. */
    boolean canModifyEditText() {
        return isEditMode() && !mEditCommitPending;
    }

    /**
     * Freezes the editable surface before background segmentation captures its
     * text, preventing IME or legacy-toolbar changes from racing the commit.
     */
    public boolean beginEditCommit() {
        if (!canModifyEditText()) {
            return false;
        }
        mEditCommitPending = true;
        endCursorDrag();
        hideEditorKeyboard();
        dismissSymbolPanel();
        mBigCursorView.hideCursor();
        mBoomActionHandler.refreshToolbarForCurrentMode();
        notifyEditUiStateChanged();
        return true;
    }

    /** Reopens editing after a failed background segmentation attempt. */
    public void cancelEditCommit() {
        if (!isEditMode() || !mEditCommitPending) {
            return;
        }
        mEditCommitPending = false;
        resetEditInputBuffer(getEditCursorOffset());
        scheduleEditCursorUpdate(true);
        mBoomActionHandler.refreshToolbarForCurrentMode();
        notifyEditUiStateChanged();
    }

    /** Show the IME without exposing a second visual text field. */
    public void showEditorKeyboard() {
        if (!canModifyEditText()) {
            return;
        }
        clearEditSelectionForCursor();
        resetEditInputBuffer(getEditCursorOffset());
        mEditInput.requestFocus();
        mEditInput.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager inputMethodManager = (InputMethodManager) mActivity
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.showSoftInput(mEditInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /** Called by {@link SwipeSelectView} for a short tap in editable content. */
    public void moveEditCursorFromContentTap(float rawX, float rawY) {
        moveEditCursorFromScreen(rawX, rawY, rawY, false);
    }

    /**
     * Delete, cut and paste all use this page-level selection API so text,
     * cursor placement and history cannot diverge from the visible chips.
     */
    public boolean deleteEditSelection() {
        return replaceEditSelection("");
    }

    public boolean cutEditSelection() {
        if (!canModifyEditText() || !hasEditSelection()) {
            return false;
        }
        final String selectedText = mBoomActionHandler.getSelectedText();
        if (!copyEditTextToClipboard(selectedText)) {
            return false;
        }
        if (!replaceEditSelection("")) {
            return false;
        }
        Toast.makeText(mActivity, R.string.bigbang_edit_cut_tips, Toast.LENGTH_SHORT).show();
        return true;
    }

    /** Original BigBang keeps the selected chips visible after copy. */
    public boolean copyEditSelection() {
        if (!canModifyEditText() || !hasEditSelection()) {
            return false;
        }
        if (!copyEditTextToClipboard(mBoomActionHandler.getSelectedText())) {
            return false;
        }
        Toast.makeText(mActivity, R.string.copy_tips, Toast.LENGTH_SHORT).show();
        return true;
    }

    public boolean pasteEditSelection() {
        if (!canModifyEditText() || !hasEditSelection()) {
            return false;
        }
        final String clipboardText = getClipboardText();
        if (TextUtils.isEmpty(clipboardText)) {
            Toast.makeText(mActivity, R.string.bigbang_edit_paste_empty, Toast.LENGTH_SHORT).show();
            return false;
        }
        return replaceEditSelection(clipboardText);
    }

    /** Shared by the big cursor and hardware Ctrl/Cmd+V. */
    public boolean pasteEditClipboard() {
        if (!canModifyEditText()) {
            return false;
        }
        if (hasEditSelection()) {
            return pasteEditSelection();
        }
        final String clipboardText = getClipboardText();
        return !TextUtils.isEmpty(clipboardText) && insertEditText(clipboardText);
    }

    public void clearEditSelection() {
        if (!canModifyEditText()) {
            return;
        }
        if (clearEditSelectionForCursor()) {
            resetEditInputBuffer(getEditCursorOffset());
            scheduleEditCursorUpdate(true);
        }
    }

    public boolean canUndoEdit() {
        return canModifyEditText() && mEditSession.getUndoSnapshots().length > 0;
    }

    public boolean canRedoEdit() {
        return canModifyEditText() && mEditSession.getRedoSnapshots().length > 0;
    }

    public boolean undoEdit() {
        if (!canUndoEdit()) {
            return false;
        }
        final EditHistorySnapshot[] undo = mEditSession.getUndoSnapshots();
        final EditHistorySnapshot target = undo[undo.length - 1];
        return restoreEditHistory(
                target,
                removeLastHistorySnapshot(undo),
                appendHistorySnapshot(mEditSession.getRedoSnapshots(), captureEditSnapshot())
        );
    }

    public boolean redoEdit() {
        if (!canRedoEdit()) {
            return false;
        }
        final EditHistorySnapshot[] redo = mEditSession.getRedoSnapshots();
        final EditHistorySnapshot target = redo[redo.length - 1];
        return restoreEditHistory(
                target,
                appendHistorySnapshot(mEditSession.getUndoSnapshots(), captureEditSnapshot()),
                removeLastHistorySnapshot(redo)
        );
    }

    boolean hasEditClipboardText() {
        return !TextUtils.isEmpty(getClipboardText());
    }

    /**
     * The original editor keeps the insertion point on the trailing edge of
     * the rightmost selected chip, including a selected whitespace chip.
     */
    void moveEditCursorToSelectionEnd() {
        if (!canModifyEditText()) {
            return;
        }
        if (!hasEditSelection()) {
            scheduleEditCursorUpdate(true);
            return;
        }
        updateEditCursorOffset(mLayout.getWordEnd(mBoomActionHandler.mSelectedId.last()), true);
    }

    /** Refreshes the overlay after ordinary ScrollView scrolling. */
    public void onScrollerChanged() {
        if (isEditMode()) {
            scheduleEditCursorUpdate(false);
        }
    }

    public void release() {
        endCursorDrag();
        hideEditorKeyboard();
        dismissSymbolPanel();
        mBigCursorView.hideCursor();
        mClipboard.removePrimaryClipChangedListener(mClipboardListener);
    }

    private void onEditInputChanged(CharSequence text, int start, int before, int count) {
        if (mSynchronizingEditInput) {
            return;
        }
        if (!isEditMode()) {
            mInputBuffer = text.toString();
            return;
        }
        if (mEditCommitPending) {
            return;
        }
        if (start < 0 || before < 0 || start + before > mInputBuffer.length()) {
            Log.w(TAG, "Ignoring invalid editor input range");
            return;
        }
        final int rangeStart = mInputBufferOffset + start;
        final int rangeEnd = rangeStart + before;
        final String replacement = text.subSequence(start, start + count).toString();
        final int selection = mEditInput.getSelectionStart();
        final int cursor = mInputBufferOffset + (selection < 0 ? start + count : selection);
        if (replaceEditRange(rangeStart, rangeEnd, replacement, cursor, false)) {
            mInputBuffer = text.toString();
        }
    }

    private void onEditInputSelectionChanged(int selectionStart, int selectionEnd) {
        if (mSynchronizingEditInput || !canModifyEditText()) {
            return;
        }
        final int safeSelection = Math.max(0, Math.min(selectionStart, mInputBuffer.length()));
        if (selectionStart != selectionEnd) {
            mSynchronizingEditInput = true;
            mEditInput.setSelection(safeSelection);
            mSynchronizingEditInput = false;
        }
        updateEditCursorOffset(mInputBufferOffset + safeSelection, false);
    }

    private boolean insertEditText(String text) {
        if (!canModifyEditText() || text == null || text.length() == 0) {
            return false;
        }
        prepareForDirectEdit();
        final int cursor = getEditCursorOffset();
        return replaceEditRange(cursor, cursor, text, cursor + text.length(), true);
    }

    private boolean deleteEditCodePointBefore() {
        return deleteEditBefore(1, true);
    }

    private boolean deleteEditBefore(int count, boolean inCodePoints) {
        if (!canModifyEditText()) {
            return false;
        }
        prepareForDirectEdit();
        final String text = mEditSession.text;
        final int cursor = getEditCursorOffset();
        final int start = getEditDeleteStart(text, cursor, count, inCodePoints);
        if (start == cursor) {
            return false;
        }
        return replaceEditRange(start, cursor, "", start, true);
    }

    private boolean deleteEditCodePointAfter() {
        return deleteEditAfter(1, true);
    }

    private boolean deleteEditAfter(int count, boolean inCodePoints) {
        if (!canModifyEditText()) {
            return false;
        }
        prepareForDirectEdit();
        final String text = mEditSession.text;
        final int cursor = getEditCursorOffset();
        final int end = getEditDeleteEnd(text, cursor, count, inCodePoints);
        if (end == cursor) {
            return false;
        }
        return replaceEditRange(cursor, end, "", cursor, true);
    }

    private boolean deleteEditSurrounding(int beforeCount, int afterCount,
            boolean inCodePoints, int inputSelectionStart) {
        if (!canModifyEditText()) {
            return false;
        }
        final String text = mEditSession.text;
        final int cursor = clampEditOffset(text, mInputBufferOffset + inputSelectionStart);
        // Flatten the affected preedit range into the document before resetting the hidden field.
        prepareForDirectEdit();
        final int start = getEditDeleteStart(text, cursor, beforeCount, inCodePoints);
        final int end = getEditDeleteEnd(text, cursor, afterCount, inCodePoints);
        if (start == cursor && end == cursor) {
            return false;
        }
        return replaceEditRange(start, end, "", start, true);
    }

    private int getEditDeleteStart(String text, int cursor, int count, boolean inCodePoints) {
        int start = cursor;
        int remaining = Math.max(0, count);
        while (remaining > 0) {
            final int previous = previousEditableOffset(text, start);
            if (previous == start) {
                break;
            }
            final int length = inCodePoints ? 1 : start - previous;
            // deleteSurroundingText reports UTF-16 units. Never split a surrogate pair or CRLF;
            // after a complete unit, leave a partial following unit for the next request.
            if (!inCodePoints && length > remaining && start != cursor) {
                break;
            }
            start = previous;
            remaining -= length;
        }
        return start;
    }

    private int getEditDeleteEnd(String text, int cursor, int count, boolean inCodePoints) {
        int end = cursor;
        int remaining = Math.max(0, count);
        while (remaining > 0) {
            final int next = nextEditableOffset(text, end);
            if (next == end) {
                break;
            }
            final int length = inCodePoints ? 1 : next - end;
            // See getEditDeleteStart: the visible editor always keeps whole code points/CRLF pairs.
            if (!inCodePoints && length > remaining && end != cursor) {
                break;
            }
            end = next;
            remaining -= length;
        }
        return end;
    }

    private void pasteEditText() {
        pasteEditClipboard();
    }

    private void toggleSymbolPanel(float cursorX, float cursorTop, float cursorBottom,
            boolean allowScroll) {
        if (!canModifyEditText()) {
            return;
        }
        if (mSymbolPanelPopup == null) {
            mSymbolPanelPopup = new SymbolPanelPopup(mActivity, new SymbolPanelPopup.Callback() {
                @Override
                public void onSymbolSelected(String symbol) {
                    // Symbols share the direct edit path so they get the same history and cursor rules.
                    insertEditText(symbol);
                }
            });
        }
        if (mSymbolPanelPopup.isShowing()) {
            mSymbolPanelPopup.dismiss();
            return;
        }
        final int visibleBottom = mBigCursorView.getVisibleBottomForEditor();
        final int overflow = Math.round(cursorBottom) + mSymbolPanelPopup.getTotalHeight()
                - visibleBottom;
        if (allowScroll && overflow > 0) {
            final int scrollBefore = mScroller.getScrollY();
            mScroller.scrollBy(0, overflow);
            if (scrollBefore != mScroller.getScrollY()) {
                mBigCursorView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!canModifyEditText() || mSymbolPanelPopup == null
                                || mSymbolPanelPopup.isShowing()) {
                            return;
                        }
                        final CursorAnchor anchor = findEditCursorAnchor();
                        toggleSymbolPanel(anchor.x, anchor.top, anchor.bottom, false);
                    }
                }, 120L);
                return;
            }
        }
        mSymbolPanelPopup.show(
                mEditorOverlayHost,
                cursorX,
                cursorTop,
                cursorBottom,
                visibleBottom
        );
    }

    private void dismissSymbolPanel() {
        if (mSymbolPanelPopup != null) {
            mSymbolPanelPopup.dismiss();
        }
    }

    private void prepareForDirectEdit() {
        clearEditSelectionForCursor();
        resetEditInputBuffer(getEditCursorOffset());
    }

    private boolean replaceEditRange(int start, int end, String replacement, int cursorOffset,
            boolean resetInputBuffer) {
        if (!canModifyEditText()) {
            return false;
        }
        final String oldText = mEditSession.text;
        final int safeStart = clampEditOffset(oldText, start);
        final int safeEnd = clampEditOffset(oldText, Math.max(safeStart, end));
        final String newText = oldText.substring(0, safeStart) + replacement + oldText.substring(safeEnd);
        return applyEditText(newText, cursorOffset, resetInputBuffer);
    }

    /**
     * Replaces every selected character range in one transaction. For a
     * discontinuous selection the replacement is inserted once at the first
     * range, while every selected range is removed without shifting later ones.
     */
    private boolean replaceEditSelection(String replacement) {
        if (!canModifyEditText() || !hasEditSelection()) {
            return false;
        }
        final Serializable selection = captureSelectedState();
        if (!(selection instanceof int[][])) {
            return false;
        }
        final EditTextMutation mutation = replaceSelectedText(
                mEditSession.text,
                (int[][]) selection,
                replacement == null ? "" : replacement
        );
        return mutation != null && applyEditText(mutation.text, mutation.cursorOffset, true);
    }

    /** Package-private for the JVM selection transaction tests. */
    static EditTextMutation replaceSelectedText(String source, int[][] ranges, String replacement) {
        if (source == null || ranges == null || ranges.length == 0 || replacement == null) {
            return null;
        }
        final StringBuilder result = new StringBuilder(source.length() + replacement.length());
        int sourceOffset = 0;
        int replacementStart = -1;
        for (int[] range : ranges) {
            if (range == null || range.length < 2) {
                continue;
            }
            final int start = Math.max(sourceOffset, Math.min(range[0], source.length()));
            final int end = Math.max(start, Math.min(range[1], source.length()));
            if (start == end) {
                continue;
            }
            result.append(source, sourceOffset, start);
            if (replacementStart < 0) {
                replacementStart = result.length();
                result.append(replacement);
            }
            sourceOffset = end;
        }
        if (replacementStart < 0) {
            return null;
        }
        result.append(source, sourceOffset, source.length());
        return new EditTextMutation(result.toString(), replacementStart + replacement.length());
    }

    private boolean copyEditTextToClipboard(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        mClipboard.setPrimaryClip(ClipData.newPlainText(null, text));
        return true;
    }

    private EditHistorySnapshot captureEditSnapshot() {
        if (!isEditMode()) {
            return null;
        }
        final Serializable selection = captureSelectedState();
        return new EditHistorySnapshot(
                mEditSession.text,
                getEditCursorOffset(),
                // Rebuild restores selected chips asynchronously. While that
                // pass is pending, retain the session snapshot so a quick
                // redo cannot overwrite the restored selection with null.
                selection instanceof int[][] ? (int[][]) selection : mEditSession.selectedRanges
        );
    }

    private boolean restoreEditHistory(
            EditHistorySnapshot snapshot,
            EditHistorySnapshot[] undoHistory,
            EditHistorySnapshot[] redoHistory
    ) {
        if (!isEditMode() || snapshot == null || !mLayout.layoutEditWords(snapshot.text)) {
            return false;
        }
        mEditSession = new EditSessionState(
                mEditSession.originalText,
                snapshot.text,
                clampEditOffset(snapshot.text, snapshot.cursorOffset),
                snapshot.selectedRanges,
                undoHistory,
                redoHistory
        );
        rebuildChips(snapshot.selectedRanges);
        resetEditInputBuffer(getEditCursorOffset());
        scheduleEditCursorUpdate(true);
        notifyEditUiStateChanged();
        return true;
    }

    private static EditHistorySnapshot[] appendHistorySnapshot(
            EditHistorySnapshot[] history, EditHistorySnapshot snapshot) {
        if (snapshot == null) {
            return history == null ? new EditHistorySnapshot[0] : history.clone();
        }
        final int historyLength = history == null ? 0 : history.length;
        final EditHistorySnapshot[] result = new EditHistorySnapshot[historyLength + 1];
        if (historyLength > 0) {
            System.arraycopy(history, 0, result, 0, historyLength);
        }
        result[historyLength] = snapshot;
        return result;
    }

    private static EditHistorySnapshot[] removeLastHistorySnapshot(EditHistorySnapshot[] history) {
        if (history == null || history.length == 0) {
            return new EditHistorySnapshot[0];
        }
        final EditHistorySnapshot[] result = new EditHistorySnapshot[history.length - 1];
        if (result.length > 0) {
            System.arraycopy(history, 0, result, 0, result.length);
        }
        return result;
    }

    private boolean applyEditText(String text, int cursorOffset, boolean resetInputBuffer) {
        if (!canModifyEditText()) {
            return false;
        }
        final EditHistorySnapshot beforeEdit = captureEditSnapshot();
        final int safeCursor = clampEditOffset(text, cursorOffset);
        if (mEditSession.text.equals(text) || !mLayout.layoutEditWords(text)) {
            return false;
        }
        // A text mutation closes the optional paste affordance.  Cursor-only
        // layout refreshes deliberately do not: otherwise a tap can flash it
        // for one frame before the next anchor update arrives.
        mBigCursorView.hidePaste();
        mEditSession = new EditSessionState(
                mEditSession.originalText,
                text,
                safeCursor,
                null,
                appendHistorySnapshot(mEditSession.getUndoSnapshots(), beforeEdit),
                new EditHistorySnapshot[0]
        );
        rebuildChips(null);
        if (resetInputBuffer) {
            resetEditInputBuffer(safeCursor);
        }
        scheduleEditCursorUpdate(true);
        notifyEditUiStateChanged();
        return true;
    }

    private void updateEditCursorOffset(int cursorOffset, boolean resetInputBuffer) {
        updateEditCursorOffset(cursorOffset, resetInputBuffer, true);
    }

    /**
     * A handle drag owns scroll position through its edge-scrolling loop.  It
     * must not also invoke the ordinary cursor-visibility scroll on every move.
     */
    private void updateEditCursorOffset(
            int cursorOffset,
            boolean resetInputBuffer,
            boolean ensureVisible
    ) {
        if (!isEditMode()) {
            return;
        }
        final int safeCursor = clampEditOffset(mEditSession.text, cursorOffset);
        if (safeCursor == mEditSession.cursorOffset && !resetInputBuffer) {
            return;
        }
        mEditSession = new EditSessionState(
                mEditSession.originalText,
                mEditSession.text,
                safeCursor,
                mEditSession.selectedRanges,
                mEditSession.getUndoSnapshots(),
                mEditSession.getRedoSnapshots()
        );
        if (resetInputBuffer) {
            resetEditInputBuffer(safeCursor);
        }
        scheduleEditCursorUpdate(ensureVisible);
    }

    private int getEditCursorOffset() {
        return mEditSession == null ? 0 : clampEditOffset(mEditSession.text, mEditSession.cursorOffset);
    }

    private int clampEditOffset(String text, int offset) {
        int safeOffset = Math.max(0, Math.min(offset, text.length()));
        if (safeOffset > 0 && safeOffset < text.length()
                && Character.isHighSurrogate(text.charAt(safeOffset - 1))
                && Character.isLowSurrogate(text.charAt(safeOffset))) {
            --safeOffset;
        }
        if (safeOffset > 0 && safeOffset < text.length()
                && text.charAt(safeOffset - 1) == '\r' && text.charAt(safeOffset) == '\n') {
            --safeOffset;
        }
        return safeOffset;
    }

    private int previousEditableOffset(String text, int offset) {
        final int safeOffset = clampEditOffset(text, offset);
        if (safeOffset <= 0) {
            return safeOffset;
        }
        if (safeOffset >= 2 && text.charAt(safeOffset - 2) == '\r'
                && text.charAt(safeOffset - 1) == '\n') {
            return safeOffset - 2;
        }
        return Character.offsetByCodePoints(text, safeOffset, -1);
    }

    private int nextEditableOffset(String text, int offset) {
        final int safeOffset = clampEditOffset(text, offset);
        if (safeOffset >= text.length()) {
            return safeOffset;
        }
        if (safeOffset + 1 < text.length() && text.charAt(safeOffset) == '\r'
                && text.charAt(safeOffset + 1) == '\n') {
            return safeOffset + 2;
        }
        return Character.offsetByCodePoints(text, safeOffset, 1);
    }

    private void resetEditInputBuffer(int cursorOffset) {
        mSynchronizingEditInput = true;
        mEditInput.setText("");
        mEditInput.setSelection(0);
        mSynchronizingEditInput = false;
        mInputBuffer = "";
        mInputBufferOffset = cursorOffset;
    }

    private boolean clearEditSelectionForCursor() {
        if (!hasEditSelection()) {
            return false;
        }
        mSavedData = null;
        mBoomActionHandler.clearSelectionStateForRelayout();
        resetChips();
        mEditSession = new EditSessionState(
                mEditSession.originalText,
                mEditSession.text,
                mEditSession.cursorOffset,
                null,
                mEditSession.getUndoSnapshots(),
                mEditSession.getRedoSnapshots()
        );
        notifyEditUiStateChanged();
        return true;
    }

    private boolean hasEditSelection() {
        return mBoomActionHandler != null && mBoomActionHandler.hasSelection();
    }

    private String getClipboardText() {
        if (!mClipboard.hasPrimaryClip()) {
            return null;
        }
        final ClipData clip = mClipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        final CharSequence text = clip.getItemAt(0).getText();
        return text == null ? null : text.toString();
    }

    private void hideEditorKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) mActivity
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mEditInput.getWindowToken(), 0);
        mEditInput.clearFocus();
    }

    private void beginCursorDrag() {
        if (!canModifyEditText()) {
            return;
        }
        clearEditSelectionForCursor();
        resetEditInputBuffer(getEditCursorOffset());
        mCursorDragActive = true;
    }

    private void endCursorDrag() {
        mCursorDragActive = false;
        mCursorAutoScrollVelocity = 0;
        if (mBigCursorView != null && mCursorAutoScrollRunnable != null) {
            mBigCursorView.removeCallbacks(mCursorAutoScrollRunnable);
        }
        // Once the handle is released, ordinary visibility correction can
        // resume without competing with the drag's edge-scroll loop.
        scheduleEditCursorUpdate(true);
    }

    private void moveEditCursorFromScreen(
            float cursorScreenX,
            float cursorScreenY,
            float fingerScreenY,
            boolean updateAutoScroll
    ) {
        if (!canModifyEditText()) {
            return;
        }
        clearEditSelectionForCursor();
        updateEditCursorOffset(
                findEditOffsetForScreenPosition(cursorScreenX, cursorScreenY),
                false,
                !mCursorDragActive
        );
        if (mCursorDragActive) {
            mCursorDragCaretX = cursorScreenX;
            mCursorDragCaretY = cursorScreenY;
            mCursorDragFingerY = fingerScreenY;
            if (updateAutoScroll) {
                updateCursorAutoScroll(fingerScreenY);
            }
        }
    }

    private void updateCursorAutoScroll(float fingerScreenY) {
        final int[] scrollerLocation = new int[2];
        mScroller.getLocationOnScreen(scrollerLocation);
        final int scrollerTop = scrollerLocation[1];
        int scrollerBottom = scrollerTop + mScroller.getHeight();
        final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(mBoomPage);
        if (rootInsets != null) {
            scrollerBottom -= rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        }
        if (scrollerBottom <= scrollerTop) {
            mCursorAutoScrollVelocity = 0;
            return;
        }
        final int visibleScrollerHeight = scrollerBottom - scrollerTop;
        final int edgeInset = Math.min(
                mActivity.getResources().getDimensionPixelSize(R.dimen.auto_scroll_top),
                visibleScrollerHeight / 2
        );
        final int autoScrollTop = scrollerTop + edgeInset;
        final int autoScrollBottom = scrollerBottom - Math.min(
                mActivity.getResources().getDimensionPixelSize(R.dimen.auto_scroll_bottom),
                visibleScrollerHeight / 2
        );
        int velocity = getOriginalAutoScrollVelocity(
                fingerScreenY,
                autoScrollTop,
                autoScrollBottom
        );
        if (shouldStopAutoScrollAtContentEdge(
                velocity,
                mScroller.canScrollVertically(-1),
                mScroller.canScrollVertically(1)
        )) {
            velocity = 0;
        }
        if (velocity == mCursorAutoScrollVelocity) {
            return;
        }
        mCursorAutoScrollVelocity = velocity;
        mBigCursorView.removeCallbacks(mCursorAutoScrollRunnable);
        if (velocity != 0) {
            mBigCursorView.postDelayed(mCursorAutoScrollRunnable, 25L);
        }
    }

    /** Original BigBang's quadratic 25ms edge-scroll velocity curve. */
    static int getOriginalAutoScrollVelocity(float fingerScreenY, int autoScrollTop,
            int autoScrollBottom) {
        final int fingerY = Math.round(fingerScreenY);
        if (fingerY < autoScrollTop) {
            return -getOriginalAutoScrollSpeed(autoScrollTop - fingerY);
        }
        if (fingerY > autoScrollBottom) {
            return getOriginalAutoScrollSpeed(fingerY - autoScrollBottom);
        }
        return 0;
    }

    /** Stops the 25ms loop as soon as its direction reaches a content edge. */
    static boolean shouldStopAutoScrollAtContentEdge(
            int velocity,
            boolean canScrollUp,
            boolean canScrollDown
    ) {
        return (velocity < 0 && !canScrollUp) || (velocity > 0 && !canScrollDown);
    }

    private static int getOriginalAutoScrollSpeed(int depth) {
        return depth * depth / (1000 - depth / 2);
    }

    private void scheduleEditCursorUpdate(boolean ensureVisible) {
        if (!canModifyEditText()) {
            mBigCursorView.hideCursor();
            return;
        }
        mCursorUpdateNeedsVisibility |= ensureVisible;
        if (mCursorUpdatePending) {
            return;
        }
        mCursorUpdatePending = true;
        mBoomPage.post(new Runnable() {
            @Override
            public void run() {
                final boolean shouldEnsureVisible = mCursorUpdateNeedsVisibility;
                mCursorUpdatePending = false;
                mCursorUpdateNeedsVisibility = false;
                updateEditCursor(shouldEnsureVisible);
            }
        });
    }

    private void updateEditCursor(boolean ensureVisible) {
        if (!canModifyEditText()) {
            mBigCursorView.hideCursor();
            return;
        }
        final CursorAnchor cursorAnchor = findEditCursorAnchor();
        if (ensureVisible && scrollEditCursorIntoView(cursorAnchor)) {
            scheduleEditCursorUpdate(false);
            return;
        }
        final String clipboardText = getClipboardText();
        mBigCursorView.showCursor(
                cursorAnchor.x,
                cursorAnchor.top,
                cursorAnchor.bottom,
                !hasEditSelection() && clipboardText != null && clipboardText.length() > 0
        );
    }

    private boolean scrollEditCursorIntoView(CursorAnchor cursorAnchor) {
        final Rect viewport = new Rect();
        if (!mScroller.getGlobalVisibleRect(viewport)) {
            return false;
        }
        final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(mBoomPage);
        if (rootInsets != null) {
            viewport.bottom -= rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        }
        if (viewport.bottom <= viewport.top) {
            return false;
        }
        final int[] pageLocation = new int[2];
        mBoomPage.getLocationOnScreen(pageLocation);
        final int cursorTop = Math.round(cursorAnchor.top) + pageLocation[1];
        final int cursorBottom = Math.round(cursorAnchor.bottom) + pageLocation[1];
        final int margin = Math.round(24f * mActivity.getResources().getDisplayMetrics().density);
        int delta = 0;
        if (cursorTop < viewport.top + margin) {
            delta = cursorTop - viewport.top - margin;
        } else if (cursorBottom > viewport.bottom - margin) {
            delta = cursorBottom - viewport.bottom + margin;
        }
        if (delta == 0) {
            return false;
        }
        final int before = mScroller.getScrollY();
        mScroller.scrollBy(0, delta);
        return before != mScroller.getScrollY();
    }

    private CursorAnchor findEditCursorAnchor() {
        final String text = mEditSession.text;
        final int cursor = getEditCursorOffset();
        int previousWord = -1;
        int nextWord = -1;
        for (int i = 0; i < mLayout.getWordCount(); ++i) {
            final int wordStart = mLayout.getWordStart(i);
            final int wordEnd = mLayout.getWordEnd(i);
            if (wordEnd <= cursor) {
                previousWord = i;
            }
            if (nextWord == -1 && wordStart >= cursor) {
                nextWord = i;
            }
        }
        final boolean beforeLineBreak = cursor < text.length() && isLineBreak(text.charAt(cursor));
        final boolean afterLineBreak = cursor > 0 && isLineBreak(text.charAt(cursor - 1));
        if (beforeLineBreak && afterLineBreak) {
            // A cursor between consecutive line breaks belongs to the empty line, not either word row.
            return getLineBreakAnchor(text, cursor);
        }
        if (beforeLineBreak && previousWord >= 0) {
            return getChipAnchor(previousWord, true);
        }
        if (beforeLineBreak) {
            return getLineBreakAnchor(text, cursor);
        }
        if (afterLineBreak && nextWord >= 0) {
            return getChipAnchor(nextWord, false);
        }
        if (nextWord >= 0) {
            return getChipAnchor(nextWord, false);
        }
        if (previousWord >= 0) {
            if (afterLineBreak) {
                return getLineBreakAnchor(text, cursor);
            }
            return getChipAnchor(previousWord, true);
        }
        if (afterLineBreak) {
            return getLineBreakAnchor(text, cursor);
        }
        return getEmptyLineAnchor();
    }

    private boolean isLineBreak(char value) {
        return value == '\r' || value == '\n';
    }

    private CursorAnchor getChipAnchor(int wordIndex, boolean trailingEdge) {
        final BoomChip chip = findChipByIndex(wordIndex);
        if (chip == null) {
            return getEmptyLineAnchor();
        }
        final int[] wordLocation = new int[2];
        final int[] pageLocation = new int[2];
        chip.word.getLocationOnScreen(wordLocation);
        mBoomPage.getLocationOnScreen(pageLocation);
        final float x = wordLocation[0] - pageLocation[0]
                + (trailingEdge ? chip.word.getWidth() : 0);
        return new CursorAnchor(
                x,
                wordLocation[1] - pageLocation[1],
                wordLocation[1] - pageLocation[1] + chip.word.getHeight()
        );
    }

    private CursorAnchor getLineBreakAnchor(String text, int cursor) {
        final int[] contentLocation = new int[2];
        final int[] pageLocation = new int[2];
        mBoomConent.getLocationOnScreen(contentLocation);
        mBoomPage.getLocationOnScreen(pageLocation);
        final int lineHeight = getActiveChipRowHeight();
        float top = contentLocation[1] - pageLocation[1] + mBoomConent.getPaddingTop();
        final int completedBreaks = countLineBreaksBefore(text, cursor);
        if (completedBreaks > 0) {
            int seenBreaks = 0;
            for (int i = 0; i < mLayout.getRowCount(); ++i) {
                if (!mLayout.isGapRow(i)) {
                    continue;
                }
                ++seenBreaks;
                if (seenBreaks == completedBreaks) {
                    final View gapRow = mBoomConent.getChildAt(i);
                    if (gapRow != null) {
                        final int[] rowLocation = new int[2];
                        gapRow.getLocationOnScreen(rowLocation);
                        top = rowLocation[1] - pageLocation[1] + gapRow.getHeight();
                    }
                    break;
                }
            }
        }
        return new CursorAnchor(contentLocation[0] - pageLocation[0], top, top + lineHeight);
    }

    /** Pure offset helper: gap-row cursor anchors must count empty and trailing lines too. */
    static int countLineBreaksBefore(String text, int cursor) {
        int count = 0;
        for (int offset = 0; offset < cursor; ++offset) {
            final char value = text.charAt(offset);
            if (value == '\n') {
                ++count;
            } else if (value == '\r' && (offset + 1 >= text.length() || text.charAt(offset + 1) != '\n')) {
                ++count;
            }
        }
        return count;
    }

    private CursorAnchor getEmptyLineAnchor() {
        final int[] contentLocation = new int[2];
        final int[] pageLocation = new int[2];
        mBoomConent.getLocationOnScreen(contentLocation);
        mBoomPage.getLocationOnScreen(pageLocation);
        final int lineHeight = getActiveChipRowHeight();
        float top = contentLocation[1] - pageLocation[1] + mBoomConent.getPaddingTop();
        if (mLayout.getRowCount() > 0) {
            final View lastRow = mBoomConent.getChildAt(mLayout.getRowCount() - 1);
            if (lastRow != null) {
                final int[] rowLocation = new int[2];
                lastRow.getLocationOnScreen(rowLocation);
                top = rowLocation[1] - pageLocation[1] + lastRow.getHeight();
            }
        }
        return new CursorAnchor(
                contentLocation[0] - pageLocation[0],
                top,
                top + lineHeight
        );
    }

    private int findEditOffsetForScreenPosition(float screenX, float screenY) {
        View closestRow = null;
        int closestRowIndex = -1;
        float closestDistance = Float.MAX_VALUE;
        for (int i = 0; i < mBoomConent.getChildCount(); ++i) {
            final View row = mBoomConent.getChildAt(i);
            if (row == null || (!mLayout.isGapRow(i)
                    && (!(row instanceof LinearLayout) || ((LinearLayout) row).getChildCount() == 0))) {
                continue;
            }
            final int[] rowLocation = new int[2];
            row.getLocationOnScreen(rowLocation);
            final float rowTop = rowLocation[1];
            final float rowBottom = rowTop + row.getHeight();
            final float distance = screenY < rowTop ? rowTop - screenY
                    : screenY > rowBottom ? screenY - rowBottom : 0f;
            if (distance < closestDistance) {
                closestDistance = distance;
                closestRow = row;
                closestRowIndex = i;
            }
        }
        if (closestRow == null || closestRowIndex < 0) {
            return getEditCursorOffset();
        }
        if (mLayout.isGapRow(closestRowIndex)) {
            // Each gap row represents one source line break, including empty lines.
            return getEditOffsetAfterLineBreak(mEditSession.text, countGapRowsThrough(closestRowIndex));
        }
        final LinearLayout chipRow = (LinearLayout) closestRow;
        BoomChip firstChip = null;
        BoomChip lastChip = null;
        for (int i = 0; i < chipRow.getChildCount(); ++i) {
            final View child = chipRow.getChildAt(i);
            if (!(child.getTag() instanceof BoomChip)) {
                continue;
            }
            final BoomChip chip = (BoomChip) child.getTag();
            if (firstChip == null) {
                firstChip = chip;
            }
            lastChip = chip;
            final int[] chipLocation = new int[2];
            child.getLocationOnScreen(chipLocation);
            final float chipCenter = chipLocation[0] + child.getWidth() / 2f;
            if (screenX <= chipLocation[0]) {
                return mLayout.getWordStart(chip.index);
            }
            if (screenX <= chipLocation[0] + child.getWidth()) {
                return screenX < chipCenter ? mLayout.getWordStart(chip.index)
                        : mLayout.getWordEnd(chip.index);
            }
        }
        if (lastChip != null) {
            return mLayout.getWordEnd(lastChip.index);
        }
        return firstChip == null ? getEditCursorOffset() : mLayout.getWordStart(firstChip.index);
    }

    private int countGapRowsThrough(int rowIndex) {
        int count = 0;
        for (int i = 0; i <= rowIndex; ++i) {
            if (mLayout.isGapRow(i)) {
                ++count;
            }
        }
        return count;
    }

    private int getEditOffsetAfterLineBreak(String text, int targetBreak) {
        int completedBreaks = 0;
        for (int offset = 0; offset < text.length(); ++offset) {
            final char value = text.charAt(offset);
            if (value == '\r') {
                if (offset + 1 < text.length() && text.charAt(offset + 1) == '\n') {
                    ++completedBreaks;
                    if (completedBreaks == targetBreak) {
                        return offset + 2;
                    }
                    ++offset;
                } else if (++completedBreaks == targetBreak) {
                    return offset + 1;
                }
            } else if (value == '\n' && ++completedBreaks == targetBreak) {
                return offset + 1;
            }
        }
        return getEditCursorOffset();
    }

    private BoomChip findChipByIndex(int wordIndex) {
        for (int i = 0; i < mBoomConent.getChildCount(); ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            for (int j = 0; j < row.getChildCount(); ++j) {
                final View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    final BoomChip chip = (BoomChip) child.getTag();
                    if (chip.index == wordIndex) {
                        return chip;
                    }
                }
            }
        }
        return null;
    }

    private static final class CursorAnchor {
        final float x;
        final float top;
        final float bottom;

        CursorAnchor(float x, float top, float bottom) {
            this.x = x;
            this.top = top;
            this.bottom = bottom;
        }
    }

    public boolean enterEditMode() {
        if (isEditMode()) {
            return true;
        }
        final String text = mLayout.getOriText();
        final Serializable selectedState = captureSelectedState();
        if (!mLayout.layoutEditWords(text)) {
            return false;
        }
        // Entering edit mode starts at the paragraph tail, matching the original BigBang editor.
        final int initialCursorOffset = text.length();
        mEditCommitPending = false;
        mEditSession = new EditSessionState(
                text,
                text,
                initialCursorOffset,
                selectedState instanceof int[][] ? (int[][]) selectedState : null,
                new EditHistorySnapshot[0],
                new EditHistorySnapshot[0]
        );
        rebuildChips(selectedState);
        animateEditEntry();
        resetEditInputBuffer(initialCursorOffset);
        scheduleEditCursorUpdate(true);
        mBoomActionHandler.refreshToolbarForCurrentMode();
        notifyEditUiStateChanged();
        finishAdjacentPull();
        return true;
    }

    public String getEditText() {
        return mEditSession == null ? null : mEditSession.text;
    }

    public EditSessionState captureEditSession() {
        if (mEditSession == null) {
            return null;
        }
        final Serializable selectedState = captureSelectedState();
        return new EditSessionState(
                mEditSession.originalText,
                mEditSession.text,
                mEditSession.cursorOffset,
                selectedState instanceof int[][] ? (int[][]) selectedState : mEditSession.selectedRanges,
                mEditSession.getUndoSnapshots(),
                mEditSession.getRedoSnapshots()
        );
    }

    public boolean restoreEditSession(EditSessionState state) {
        if (state == null || !mLayout.layoutEditWords(state.text)) {
            return false;
        }
        mEditCommitPending = false;
        mEditSession = state;
        rebuildChips(state.selectedRanges);
        resetEditInputBuffer(getEditCursorOffset());
        scheduleEditCursorUpdate(true);
        mBoomActionHandler.refreshToolbarForCurrentMode();
        notifyEditUiStateChanged();
        finishAdjacentPull();
        return true;
    }

    public boolean commitEditMode(int[] segment) {
        // Keep edit-mode character ranges through re-segmentation, including spaces between selected words.
        final Serializable selectedState = captureSelectedState();
        if (mEditSession == null || !mLayout.layoutWords(segment, mEditSession.text, -1)) {
            return false;
        }
        endCursorDrag();
        hideEditorKeyboard();
        dismissSymbolPanel();
        mBigCursorView.hideCursor();
        resetEditInputBuffer(0);
        mEditCommitPending = false;
        mEditSession = null;
        rebuildChips(selectedState);
        mBoomActionHandler.refreshToolbarForCurrentMode();
        notifyEditUiStateChanged();
        finishAdjacentPull();
        return true;
    }

    public boolean discardEditMode(int[] segment, String text) {
        if (mEditSession == null || !mLayout.layoutWords(segment, text, -1)) {
            return false;
        }
        endCursorDrag();
        hideEditorKeyboard();
        dismissSymbolPanel();
        mBigCursorView.hideCursor();
        resetEditInputBuffer(0);
        mEditCommitPending = false;
        mEditSession = null;
        rebuildChips(null);
        mBoomActionHandler.refreshToolbarForCurrentMode();
        notifyEditUiStateChanged();
        finishAdjacentPull();
        return true;
    }

    private void rebuildChips(Serializable selectedState) {
        if (mBoomActionHandler != null) {
            mBoomActionHandler.clearSelectionStateForRelayout();
        }
        mSavedData = selectedState;
        mBoomConent.removeAllViews();
        initChips(false);
        if (selectedState != null || isEditMode()) {
            mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (selectedState != null) {
                        restoreSelectedState();
                    }
                    scheduleEditCursorUpdate(true);
                }
            });
        }
    }

    /**
     * The original editor lets its rebuilt chips softly emerge instead of
     * replacing the normal-mode grid in a single frame.  Mutations intentionally
     * skip this animation so typing remains immediate.
     */
    private void animateEditEntry() {
        mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                final int rows = Math.min(mBoomConent.getChildCount(), 12);
                for (int rowIndex = 0; rowIndex < rows; ++rowIndex) {
                    final View row = mBoomConent.getChildAt(rowIndex);
                    if (!(row instanceof LinearLayout)) {
                        continue;
                    }
                    for (int childIndex = 0; childIndex < ((LinearLayout) row).getChildCount(); ++childIndex) {
                        final View chip = ((LinearLayout) row).getChildAt(childIndex);
                        final long delay = Math.min(96L, rowIndex * 12L);
                        chip.setScaleX(0.92f);
                        chip.setScaleY(0.92f);
                        chip.setAlpha(0f);
                        chip.setTranslationY(6f * mActivity.getResources().getDisplayMetrics().density);
                        chip.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                BoomAnimator.makeBoomAnimation(chip);
                            }
                        }, delay);
                    }
                }
            }
        });
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
        if (isEditMode() && !canModifyEditText()) {
            return false;
        }
        final boolean handled = mBoomActionHandler != null && mBoomActionHandler.handleClick();
        if (handled && isEditMode()) {
            // handleClick clears the legacy handler before this page can
            // capture it, so explicitly clear the persisted edit selection.
            syncEditSelectionStateFromHandler();
            resetEditInputBuffer(getEditCursorOffset());
            scheduleEditCursorUpdate(true);
        }
        return handled;
    }

    public Serializable captureSelectedState() {
        if (mBoomActionHandler != null && mBoomActionHandler.hasSelection()) {
            TreeSet<Integer> wordSet = mBoomActionHandler.mSelectedId;
            int[][] ranges = new int[wordSet.size()][2];
            int rangeCount = 0;
            int previousWordIndex = -2;
            for (Integer wordIdx : wordSet) {
                if (wordIdx != previousWordIndex + 1) {
                    ranges[rangeCount][0] = mLayout.getWordStart(wordIdx);
                    ranges[rangeCount][1] = mLayout.getWordEnd(wordIdx);
                    rangeCount++;
                } else {
                    // Normal layout omits whitespace; preserve the source gap when adjacent chips are selected.
                    ranges[rangeCount - 1][1] = mLayout.getWordEnd(wordIdx);
                }
                previousWordIndex = wordIdx;
            }
            if (rangeCount == ranges.length) {
                return ranges;
            }
            int[][] compactRanges = new int[rangeCount][2];
            System.arraycopy(ranges, 0, compactRanges, 0, rangeCount);
            return compactRanges;
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
        if (isEditMode() && !canModifyEditText()) {
            return;
        }
        final int wordCount = mLayout.getWordCount();
        if (wordCount <= 0) {
            return;
        }
        if (mBoomActionHandler != null && mBoomActionHandler.isAllSelected()) {
            handleClick();
            return;
        }
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
        if (isEditMode()) {
            moveEditCursorToSelectionEnd();
        }
    }

    /** Ctrl/Cmd+A follows platform convention: selecting again does not cancel the selection. */
    public boolean selectAllForEditShortcut() {
        if (!canModifyEditText() || mLayout.getWordCount() <= 0) {
            return false;
        }
        if (!mBoomActionHandler.isAllSelected()) {
            selectAll();
        }
        return true;
    }

    /**
     * Auto-select the word that was touched / identified by the initial layout.
     * This is used when a third-party caller provides a character index via
     * {@code EXTRA_SELECTED_CHAR_INDEX} — after layout the touched word index
     * is known, and this method selects it.
     */
    public void selectTouchedWord() {
        final int touchedIndex = mLayout.getTouchedIndex();
        if (touchedIndex < 0 || touchedIndex >= mLayout.getWordCount()) {
            return;
        }
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    if (chip.index == touchedIndex) {
                        chip.setSelected(true);
                    }
                }
            }
        }
        mBoomActionHandler.onSelect(touchedIndex, touchedIndex);
        // Scroll the touched word to the centre of the viewport
        mScroller.post(new Runnable() {
            @Override
            public void run() {
                final int row = mLayout.getRowForIndex(touchedIndex);
                final View rowView = mBoomConent.getChildAt(row);
                if (rowView == null) {
                    return;
                }
                final int rowCentre = rowView.getTop() + rowView.getHeight() / 2;
                final int viewportCentre = mScroller.getHeight() / 2;
                final int targetScrollY = rowCentre - viewportCentre;
                mScroller.scrollTo(0, Math.max(0, targetScrollY));
            }
        });
    }

    public boolean splitSelectedWordsToChars() {
        if (mBoomActionHandler == null || !mBoomActionHandler.hasSelection()) {
            return false;
        }
        TreeSet<Integer> newSelection = mLayout.splitSelectedWordsToChars(
                new TreeSet<Integer>(mBoomActionHandler.mSelectedId)
        );
        if (newSelection == null || newSelection.isEmpty()) {
            return false;
        }
        mBoomActionHandler.clearSelectionStateForRelayout();
        mSavedData = newSelection;
        mBoomConent.removeAllViews();
        initChips(false);
        mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                updateScrollerInsetsForContent();
                restoreSelectedState();
            }
        });
        return true;
    }

    public void setOnAdjacentRequestListener(OnAdjacentRequestListener listener) {
        mOnAdjacentRequestListener = listener;
    }

    public boolean replaceWords(int[] segment, String text, int targetWordIndex, int charOffset) {
        if (isEditMode()) {
            return false;
        }
        // Save selection as char ranges before it gets cleared
        Serializable savedSelection = null;
        if (mBoomActionHandler != null && mBoomActionHandler.hasSelection()) {
            savedSelection = captureSelectedState();
        }
        if (mBoomActionHandler != null) {
            mBoomActionHandler.clearSelectionStateForRelayout();
        }
        mSavedData = null;
        mBoomConent.removeAllViews();
        if (!mLayout.layoutWords(segment, text, -1)) {
            finishAdjacentPull();
            return false;
        }
        initChips(false);
        // Restore selection with adjusted char ranges
        if (savedSelection instanceof int[][]) {
            int[][] ranges = (int[][]) savedSelection;
            if (charOffset != 0) {
                for (int[] range : ranges) {
                    range[0] += charOffset;
                    range[1] += charOffset;
                }
            }
            mSavedData = ranges;
            mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    updateScrollerInsetsForContent();
                    restoreSelectedState();
                }
            });
        }
        scrollToWord(targetWordIndex);
        finishAdjacentPull();
        return true;
    }

    public boolean replaceWords(int[] segment, String text, int targetWordIndex) {
        return replaceWords(segment, text, targetWordIndex, 0);
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
        mScroller.setEdgeDragEnabled(!isEditMode());
        animateContentOffset(0f);
        hideAdjacentHint(mAdjacentTopHint);
        hideAdjacentHint(mAdjacentBottomHint);
    }

    private void initChips(boolean animate) {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final int start = mLayout.getRowStart(i);
            final int count = mLayout.getColumnCount(i);
            if (mLayout.isGapRow(i)) {
                final int rowHeight = getActiveChipRowHeight();
                final int gapHeight = Math.round(
                        rowHeight * BigBangSettings.get(mActivity).getGapRowHeightPercent() / 100f
                );
                View spacer = new View(mActivity);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        gapHeight));
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
                final int wordIndex = start + j;
                BoomChip chip = new BoomChip(wordIndex, chipView);
                chipView.setTag(chip);
                if (mLayout.isEditHalfWidth(wordIndex)) {
                    // Constrain the root as well: its 9-patch background can otherwise widen the chip.
                    row.addView(chipView, new LinearLayout.LayoutParams(
                            mLayout.getEditHalfWidthChipWidth(),
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                } else {
                    row.addView(chipView);
                }
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

    private int getActiveChipRowHeight() {
        return mActivity.getResources().getDimensionPixelSize(isEditMode()
                ? R.dimen.chip_row_height_edit
                : R.dimen.chip_row_height);
    }

    private boolean restoreSelectedState() {
        if (mSavedData instanceof int[][]) {
            // Char-range based selection (stable across rotation)
            int[][] ranges = (int[][]) mSavedData;
            TreeSet<Integer> newWordSet = new TreeSet<Integer>();
            for (int[] range : ranges) {
                int charStart = range[0];
                int charEnd = range[1];
                for (int i = 0; i < mLayout.getWordCount(); i++) {
                    int wordStart = mLayout.getWordStart(i);
                    int wordEnd = mLayout.getWordEnd(i);
                    if (wordStart < charEnd && wordEnd > charStart) {
                        newWordSet.add(i);
                    }
                }
            }
            if (!newWordSet.isEmpty()) {
                for (int i = 0; i < mLayout.getRowCount(); ++i) {
                    final LinearLayout row = getChipRow(i);
                    if (row == null) continue;
                    for (int j = 0; j < row.getChildCount(); ++j) {
                        View child = row.getChildAt(j);
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            if (newWordSet.contains(chip.index)) {
                                chip.setSelected(true);
                            }
                        }
                    }
                }
                mBoomActionHandler.onSelect(newWordSet);
                return true;
            }
        } else if (mSavedData instanceof TreeSet) {
            // Legacy: word-index based (used by splitSelectedWordsToChars)
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
        if (isEditMode() || mAdjacentLoading) {
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
        if (isEditMode() || mAdjacentLoading) {
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
        view.setText(getHintTitle(direction));
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
        int extraInset = isEditMode() ? 0 : Math.max(0, (availableHeight - contentHeight) / 2);
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
            if (mLayout.isEditLayout()) {
                // The original edit 9-patch is exactly 40dp high.  Remove the
                // normal-mode row padding instead of shrinking that bitmap to 30dp.
                chipView.setPadding(
                        chipView.getPaddingLeft(),
                        0,
                        chipView.getPaddingRight(),
                        0
                );
                word.setBackgroundResource(mLayout.isEditHalfWidth(id)
                        ? R.drawable.boom_edit_chips_punctuate_space
                        : R.drawable.boom_edit_chips_bg);
                word.setTextColor(mActivity.getResources().getColorStateList(
                        R.color.boom_chip_text_color));
                final ViewGroup.LayoutParams editParams = word.getLayoutParams();
                editParams.height = getActiveChipRowHeight();
                word.setLayoutParams(editParams);
                if (!mLayout.isEditHalfWidth(id)) {
                    // Keep CJK/emoji at the original editor's 24dp minimum.
                    word.setMinWidth(mLayout.getEditWordMinWidth());
                }
            }
            if (mLayout.isEditHalfWidth(id)) {
                final int width = mLayout.getEditHalfWidthChipWidth();
                // Preserve vertical inset only so the compact chip still has room to render its glyph.
                word.setPadding(0, word.getPaddingTop(), 0, word.getPaddingBottom());
                final ViewGroup.LayoutParams params = word.getLayoutParams();
                params.width = width;
                word.setLayoutParams(params);
                word.setWidth(width);
            }
        }

        public void setSelected(boolean selected) {
            word.setShadowLayer(selected ? 1.0f : 0, 0, -3.0f, 0x1f000000);
            word.setSelected(selected);
        }
    }

    static final class EditTextMutation {
        final String text;
        final int cursorOffset;

        EditTextMutation(String text, int cursorOffset) {
            this.text = text;
            this.cursorOffset = cursorOffset;
        }
    }

    private static final class EditHistorySnapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        final String text;
        final int cursorOffset;
        final int[][] selectedRanges;

        EditHistorySnapshot(String text, int cursorOffset, int[][] selectedRanges) {
            this.text = text;
            this.cursorOffset = cursorOffset;
            this.selectedRanges = EditSessionState.copyRanges(selectedRanges);
        }

        EditHistorySnapshot copy() {
            return new EditHistorySnapshot(text, cursorOffset, selectedRanges);
        }
    }

    public static final class EditSessionState implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String originalText;
        public final String text;
        public final int cursorOffset;
        public final int[][] selectedRanges;
        /** Kept for compatibility with sessions saved before structured snapshots were added. */
        public final String[] undoHistory;
        public final String[] redoHistory;
        private final EditHistorySnapshot[] undoSnapshots;
        private final EditHistorySnapshot[] redoSnapshots;

        EditSessionState(
                String originalText,
                String text,
                int cursorOffset,
                int[][] selectedRanges,
                String[] undoHistory,
                String[] redoHistory
        ) {
            this(
                    originalText,
                    text,
                    cursorOffset,
                    selectedRanges,
                    snapshotsFromTextHistory(undoHistory, cursorOffset),
                    snapshotsFromTextHistory(redoHistory, cursorOffset)
            );
        }

        EditSessionState(
                String originalText,
                String text,
                int cursorOffset,
                int[][] selectedRanges,
                EditHistorySnapshot[] undoSnapshots,
                EditHistorySnapshot[] redoSnapshots
        ) {
            this.originalText = originalText;
            this.text = text;
            this.cursorOffset = cursorOffset;
            this.selectedRanges = copyRanges(selectedRanges);
            this.undoSnapshots = copySnapshots(undoSnapshots);
            this.redoSnapshots = copySnapshots(redoSnapshots);
            this.undoHistory = snapshotTexts(this.undoSnapshots);
            this.redoHistory = snapshotTexts(this.redoSnapshots);
        }

        EditHistorySnapshot[] getUndoSnapshots() {
            return undoSnapshots == null
                    ? snapshotsFromTextHistory(undoHistory, cursorOffset)
                    : copySnapshots(undoSnapshots);
        }

        EditHistorySnapshot[] getRedoSnapshots() {
            return redoSnapshots == null
                    ? snapshotsFromTextHistory(redoHistory, cursorOffset)
                    : copySnapshots(redoSnapshots);
        }

        private static EditHistorySnapshot[] snapshotsFromTextHistory(String[] history, int cursorOffset) {
            if (history == null || history.length == 0) {
                return new EditHistorySnapshot[0];
            }
            EditHistorySnapshot[] snapshots = new EditHistorySnapshot[history.length];
            for (int i = 0; i < history.length; ++i) {
                snapshots[i] = new EditHistorySnapshot(history[i], cursorOffset, null);
            }
            return snapshots;
        }

        private static String[] snapshotTexts(EditHistorySnapshot[] snapshots) {
            if (snapshots == null || snapshots.length == 0) {
                return new String[0];
            }
            String[] texts = new String[snapshots.length];
            for (int i = 0; i < snapshots.length; ++i) {
                texts[i] = snapshots[i].text;
            }
            return texts;
        }

        private static EditHistorySnapshot[] copySnapshots(EditHistorySnapshot[] snapshots) {
            if (snapshots == null || snapshots.length == 0) {
                return new EditHistorySnapshot[0];
            }
            EditHistorySnapshot[] copy = new EditHistorySnapshot[snapshots.length];
            for (int i = 0; i < snapshots.length; ++i) {
                copy[i] = snapshots[i] == null ? null : snapshots[i].copy();
            }
            return copy;
        }

        private static int[][] copyRanges(int[][] ranges) {
            if (ranges == null) {
                return null;
            }
            int[][] copy = new int[ranges.length][];
            for (int i = 0; i < ranges.length; ++i) {
                copy[i] = ranges[i] == null ? null : ranges[i].clone();
            }
            return copy;
        }
    }
}
