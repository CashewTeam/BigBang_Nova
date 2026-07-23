package com.cashewteam.novatext.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.cashewteam.novatext.android.data.BigBangSettings;

import java.util.TreeSet;

public class BoomActionHandler implements CustomScrollView.OnScrollListener {

    private final BoomChipPage mBoomPage;
    private final Toast mToast;

    private final int mRowMoveUpOffset;
    private final int mRowMoveDownOffset;
    private final int mSelectRectMarginTop;
    private final int mSelectRectTopOffset;
    private final int mSelectBarYOffset;

    int mSelectedTopRow = -1;
    int mSelectedBottomRow = -1;
    RelativeLayout mSelectBar;
    RelativeLayout mFakeSelectBar;
    LinearLayout mSelectRect;
    TreeSet<Integer> mSelectedId = new TreeSet<Integer>();
    private Rect mSelectBarRect = new Rect();
    private final boolean mEnableFakeSelectBar;

    public BoomActionHandler(BoomChipPage boomPage, boolean enableFakeSelectBar) {
        mBoomPage = boomPage;
        mEnableFakeSelectBar = enableFakeSelectBar;
        mToast = Toast.makeText(boomPage.mActivity, "", Toast.LENGTH_SHORT);

        final Resources res = boomPage.mActivity.getResources();
        mRowMoveUpOffset = res.getDimensionPixelOffset(R.dimen.chip_row_move_up_offset);
        mRowMoveDownOffset = res.getDimensionPixelOffset(R.dimen.chip_row_move_down_offset);
        if (mEnableFakeSelectBar) {
            mSelectRectMarginTop = res.getDimensionPixelOffset(R.dimen.select_rect_margin_top);
            mSelectRectTopOffset = res.getDimensionPixelOffset(R.dimen.select_rect_top_offset);
            mSelectBarYOffset = 0;
        } else {
            int expand = res.getDimensionPixelOffset(R.dimen.chip_row_padding_top)
                    + (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    5,
                    res.getDisplayMetrics()
            );
            mSelectRectMarginTop = -expand;
            mSelectRectTopOffset = expand * 2;
            mSelectBarYOffset = mRowMoveUpOffset;
        }

        initViews(mBoomPage.mBoomTable);
        initFakeViews(mBoomPage.mBoomPage);
    }

    public void onSelect(TreeSet<Integer> savedState) {
        mSelectedId.clear();
        final int wordCount = mBoomPage.mLayout.getWordCount();
        for (Integer id : savedState) {
            if (id < wordCount) {
                mSelectedId.add(id);
            }
        }
        if (!mSelectedId.isEmpty()) {
            onSelectInternal(mSelectedId.first(), mSelectedId.last());
        }
    }

    public void onSelect(int start, int end) {
        for (int i = start; i <= end; ++i) {
            mSelectedId.add(new Integer(i));
        }
        onSelectInternal(start, end);
    }

    private void onSelectInternal(int start, int end) {
        refreshToolbarForCurrentMode();
        final int topRow = mBoomPage.mLayout.getRowForIndex(start);
        final int bottomRow = mBoomPage.mLayout.getRowForIndex(end);

        if (mSelectedTopRow == -1) {
            mSelectedTopRow = topRow;
            mSelectedBottomRow = bottomRow;
            showSelBarAndBgRect(topRow);
        } else {
            if (topRow < mSelectedTopRow) {
                mSelectedTopRow = topRow;
            }
            if (bottomRow > mSelectedBottomRow) {
                mSelectedBottomRow = bottomRow;
            }
            positionSelBar(mSelectedTopRow);
            positionSelectRect(mSelectedTopRow);
        }

        moveChipRows();
        mBoomPage.syncEditSelectionStateFromHandler();

        mSelectBar.post(new Runnable() {
            @Override
            public void run() {
                if (hasSelection() && mSelectedTopRow != -1) {
                    positionSelBar(mSelectedTopRow);
                    positionSelectRect(mSelectedTopRow);
                }
            }
        });
        notifyEditUiStateChanged();
    }

    public void deSelect(int stat, int end) {
        for (int i = stat; i <= end; ++i) {
            mSelectedId.remove(new Integer(i));
        }
        if (mSelectedId.size() > 0) {
            final int min = mBoomPage.mLayout.getRowForIndex(mSelectedId.first());
            final int max = mBoomPage.mLayout.getRowForIndex(mSelectedId.last());
            if (min > mSelectedTopRow) {
                mSelectedTopRow = min;
                positionSelBar(min);
                positionSelectRect(min);
            } else if (max < mSelectedBottomRow) {
                mSelectedBottomRow = max;
                positionSelBar(min);
                positionSelectRect(min);
            }
        } else {
            hideSelectBarAndRect();
            mBoomPage.resetChips();
        }
        moveChipRows();
        mBoomPage.syncEditSelectionStateFromHandler();
        notifyEditUiStateChanged();
    }

    public boolean handleClick() {
        if (mSelectedId.size() > 0) {
            mSelectedId.clear();
            mBoomPage.resetChips();
            hideSelectBarAndRect();
            mBoomPage.syncEditSelectionStateFromHandler();
            notifyEditUiStateChanged();
            return true;
        }
        return false;
    }

    void clearSelectionStateForRelayout() {
        mSelectedId.clear();
        mSelectedTopRow = -1;
        mSelectedBottomRow = -1;
        if (mSelectBar != null) {
            mSelectBar.setVisibility(View.INVISIBLE);
        }
        if (mSelectRect != null) {
            ViewGroup.LayoutParams params = mSelectRect.getLayoutParams();
            params.height = 0;
            mSelectRect.setLayoutParams(params);
            mSelectRect.setVisibility(View.INVISIBLE);
        }
        if (mFakeSelectBar != null && mFakeSelectBar.getVisibility() == View.VISIBLE) {
            mFakeSelectBar.setVisibility(View.INVISIBLE);
        }
        notifyEditUiStateChanged();
    }

    private boolean isChineseWord(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
            return true;
        }
        return false;
    }

    private int getContentType(String text) {
        int type = 0;
        for (int i = 0; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                type |= 1;
            } else if (isChineseWord(ch)) {
                type |= 2;
            } else {
                return 3;
            }
        }
        return type == 0 ? 3 : type - 1;
    }

    private void copy(String text) {
        mToast.setText(mBoomPage.mActivity.getResources().getString(R.string.copy_tips));
        mToast.show();
        ClipboardManager clipboard = (ClipboardManager) mBoomPage.mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text));
    }

    public void search(String text, int type) {
        Intent intent = BoomSearchOverlayActivity.createIntent(mBoomPage.mActivity, text, type);
        mBoomPage.mActivity.startActivity(intent);
    }

    private void share() {
        final String shareText = getSelectedText();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, shareText);
        Intent i = Intent.createChooser(send, null);
        i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        mBoomPage.mActivity.startActivity(i);
    }

    private void initViews(View contentView) {
        mSelectRect = (LinearLayout) contentView.findViewById(R.id.boom_multi_selected_bg);
        mSelectBar = (RelativeLayout) contentView.findViewById(R.id.multi_selected_bar);
        mSelectBar.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                onScrollChanged();
            }
        });
        bindToolbarActions(mSelectBar);
    }

    private void initFakeViews(View contentView) {
        mFakeSelectBar = (RelativeLayout) contentView.findViewById(R.id.fake_multi_selected_bar);
        bindToolbarActions(mFakeSelectBar);
        refreshToolbarForCurrentMode();
    }

    private void bindToolbarActions(RelativeLayout toolbar) {
        final int[] actionIds = {
                R.id.all_search,
                R.id.all_dict,
                R.id.all_cut,
                R.id.all_share,
                R.id.all_copy
        };
        for (int actionId : actionIds) {
            final View actionView = toolbar.findViewById(actionId);
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dispatchToolbarAction(v.getId());
                }
            });
        }
    }

    private void dispatchToolbarAction(int actionId) {
        if (mBoomPage.isEditMode()) {
            if (!mBoomPage.canModifyEditText()) {
                return;
            }
            if (actionId == R.id.all_search) {
                mBoomPage.deleteEditSelection();
            } else if (actionId == R.id.all_dict) {
                mBoomPage.clearEditSelection();
            } else if (actionId == R.id.all_cut) {
                mBoomPage.cutEditSelection();
            } else if (actionId == R.id.all_share) {
                mBoomPage.copyEditSelection();
            } else if (actionId == R.id.all_copy) {
                mBoomPage.pasteEditSelection();
            }
            return;
        }
        if (actionId == R.id.all_search) {
            search(getSelectedText(), BigBangSettings.get(mBoomPage.mActivity).getWebSearchType());
        } else if (actionId == R.id.all_dict) {
            search(getSelectedText(), BigBangSettings.get(mBoomPage.mActivity).getDictSearchType());
        } else if (actionId == R.id.all_cut) {
            mBoomPage.splitSelectedWordsToChars();
        } else if (actionId == R.id.all_share) {
            share();
        } else if (actionId == R.id.all_copy) {
            copy(getSelectedText());
        }
    }

    /** Reuses the same five slots while preventing normal-mode actions from leaking into edit mode. */
    public void refreshToolbarForCurrentMode() {
        updateFakeToolbarContainerForCurrentMode();
        configureToolbar(mSelectBar);
        configureToolbar(mFakeSelectBar);
    }

    private void updateFakeToolbarContainerForCurrentMode() {
        if (mFakeSelectBar == null || !(mFakeSelectBar.getParent() instanceof View)) {
            return;
        }
        final View container = (View) mFakeSelectBar.getParent();
        final ViewGroup.LayoutParams layoutParams = container.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final ViewGroup.MarginLayoutParams marginParams =
                (ViewGroup.MarginLayoutParams) layoutParams;
        final int expectedTopMargin = mBoomPage.isEditMode()
                ? 0
                : mBoomPage.mActivity.getResources().getDimensionPixelOffset(
                        R.dimen.fake_select_bar_margin_top);
        if (marginParams.topMargin == expectedTopMargin) {
            return;
        }
        // A pinned edit toolbar belongs in the header gap. The normal toolbar
        // retains its legacy inset, while this prevents it covering row one.
        marginParams.topMargin = expectedTopMargin;
        container.setLayoutParams(marginParams);
    }

    private void configureToolbar(RelativeLayout toolbar) {
        if (toolbar == null) {
            return;
        }
        final ImageView first = (ImageView) toolbar.findViewById(R.id.all_search);
        final ImageView second = (ImageView) toolbar.findViewById(R.id.all_dict);
        final ImageView third = (ImageView) toolbar.findViewById(R.id.all_cut);
        final ImageView fourth = (ImageView) toolbar.findViewById(R.id.all_share);
        final ImageView fifth = (ImageView) toolbar.findViewById(R.id.all_copy);
        if (mBoomPage.isEditMode()) {
            final boolean editActionsEnabled = mBoomPage.canModifyEditText();
            setToolbarButton(first, R.drawable.boom_edit_selection_delete,
                    R.string.bigbang_edit_selection_delete, editActionsEnabled);
            setToolbarButton(second, R.drawable.boom_edit_selection_cancel,
                    R.string.bigbang_edit_selection_cancel, editActionsEnabled);
            setToolbarButton(third, R.drawable.boom_edit_selection_cut,
                    R.string.bigbang_edit_selection_cut, editActionsEnabled);
            setToolbarButton(fourth, R.drawable.boom_edit_selection_copy,
                    R.string.bigbang_edit_selection_copy, editActionsEnabled);
            setToolbarButton(fifth, R.drawable.boom_edit_selection_paste,
                    R.string.bigbang_edit_selection_paste,
                    editActionsEnabled && mBoomPage.hasEditClipboardText());
            return;
        }
        setToolbarButton(first, R.drawable.boom_chips_all_search, 0, true);
        setToolbarButton(second, R.drawable.boom_chips_all_dict, 0, true);
        setToolbarButton(third, R.drawable.boom_chips_all_cut, 0, true);
        setToolbarButton(fourth, R.drawable.boom_chips_all_share, 0, true);
        setToolbarButton(fifth, R.drawable.boom_chips_all_copy, 0, true);
    }

    private void setToolbarButton(ImageView button, int drawableRes, int descriptionRes, boolean enabled) {
        button.setImageResource(drawableRes);
        button.setContentDescription(descriptionRes == 0
                ? null : mBoomPage.mActivity.getString(descriptionRes));
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.38f);
    }

    private void notifyEditUiStateChanged() {
        if (mBoomPage.isEditMode()) {
            mBoomPage.notifyEditUiStateChanged();
        }
    }

    public boolean hasSelection() {
        return mSelectedId.size() > 0;
    }

    public boolean isAllSelected() {
        return mSelectedId.size() == mBoomPage.mLayout.getWordCount() && mSelectedId.size() > 0;
    }

    public String getSelectedText() {
        final int wordCount = mBoomPage.mLayout.getWordCount();
        if (mSelectedId.size() == wordCount) {
            return mBoomPage.mLayout.getOriText();
        }
        StringBuilder res = new StringBuilder();
        int last = -1;
        for (Integer cur : mSelectedId) {
            if (last == -1) {
                res.append(mBoomPage.mLayout.getWord(cur));
            } else if (cur == last + 1) {
                final int end = cur == wordCount - 1 ?
                        mBoomPage.mLayout.getOriText().length() : mBoomPage.mLayout.getWordEnd(cur);
                res.append(mBoomPage.mLayout.getOriText(mBoomPage.mLayout.getWordEnd(last), end));
            } else {
                res.append(mBoomPage.mLayout.getWord(cur));
            }
            last = cur;
        }
        return res.toString();
    }

    private int getSelectRectHeight() {
        return mBoomPage.getRowsHeight(mSelectedTopRow, mSelectedBottomRow) + mSelectRectTopOffset;
    }

    private int getSelectRectY(int row) {
        return mBoomPage.getRowTop(row) + mSelectRectMarginTop;
    }

    private int getSelectBarY(int row) {
        if (mBoomPage.isEditMode() && mSelectBar.getHeight() > 0) {
            // In edit mode the action row belongs in the gap immediately
            // above the selection, never on top of selected word chips.
            return mBoomPage.getRowTop(row) - mSelectBar.getHeight();
        }
        return mBoomPage.getRowTop(row) + mSelectBarYOffset;
    }

    private void showSelBarAndBgRect(int row) {
        mSelectBar.setVisibility(View.VISIBLE);
        mSelectRect.setVisibility(View.VISIBLE);
        mSelectBar.setTranslationY(getSelectBarY(row));
        mSelectRect.setTranslationY(getSelectRectY(row));
        BoomAnimator.makeBarAndRectShowAnimation(mSelectBar, mSelectRect, getSelectRectHeight());
    }

    private void hideSelectBarAndRect() {
        mSelectedTopRow = -1;
        mSelectedBottomRow = -1;
        BoomAnimator.makeBarAndRectHideAnimation(mSelectBar, mSelectRect);
        if (mFakeSelectBar != null && mFakeSelectBar.getVisibility() == View.VISIBLE) {
            mFakeSelectBar.setVisibility(View.INVISIBLE);
        }
    }

    private void positionSelBar(int row) {
        BoomAnimator.makeMoveAnimation(mSelectBar, mSelectBar.getTranslationY(), getSelectBarY(row));
    }

    private void positionSelectRect(int row) {
        BoomAnimator.makeHeightAnimation(mSelectRect, getSelectRectHeight(), mSelectRect.getTranslationY(), getSelectRectY(row));
    }

    private void moveChipRows() {
        final int rowCount = mBoomPage.mLayout.getRowCount();
        final int editToolbarHeight = mBoomPage.isEditMode() ? mSelectBar.getHeight() : 0;
        if (mSelectedTopRow == -1 || mSelectedBottomRow == -1) {
            for (int i = 0; i < rowCount; ++i) {
                mBoomPage.moveChipRow(i, 0);
            }
        } else {
            for (int i = 0; i < rowCount; ++i) {
                float end;
                if (i < mSelectedTopRow) {
                    end = editToolbarHeight > 0 ? -editToolbarHeight : mRowMoveUpOffset;
                } else if (i > mSelectedBottomRow) {
                    end = editToolbarHeight > 0 ? editToolbarHeight : mRowMoveDownOffset;
                } else {
                    end = 0;
                }
                mBoomPage.moveChipRow(i, end);
            }
        }
    }

    @Override
    public void onScrollChanged() {
        mBoomPage.onScrollerChanged();
        if (!hasSelection()) return;
        if (mSelectBar != null && mFakeSelectBar != null) {
            final View pinnedBarContainer = (View) mFakeSelectBar.getParent();
            final Rect pinnedBarRect = new Rect();
            if (pinnedBarContainer == null || !pinnedBarContainer.getGlobalVisibleRect(pinnedBarRect)) {
                return;
            }
            final int[] selectBarLocation = new int[2];
            mSelectBar.getLocationOnScreen(selectBarLocation);
            mSelectBarRect.set(
                    selectBarLocation[0],
                    selectBarLocation[1],
                    selectBarLocation[0] + mSelectBar.getWidth(),
                    selectBarLocation[1] + mSelectBar.getHeight()
            );
            if (mSelectBarRect.top <= pinnedBarRect.top) {
                showFakeSelectBar(true);
            } else if (mSelectBarRect.bottom >= pinnedBarRect.bottom) {
                showFakeSelectBar(false);
            } else {
                mFakeSelectBar.setVisibility(View.INVISIBLE);
                mSelectBar.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showFakeSelectBar(boolean alignTop) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mFakeSelectBar.getLayoutParams();
        final int expectedRule = alignTop
                ? RelativeLayout.ALIGN_PARENT_TOP : RelativeLayout.ALIGN_PARENT_BOTTOM;
        final int oppositeRule = alignTop
                ? RelativeLayout.ALIGN_PARENT_BOTTOM : RelativeLayout.ALIGN_PARENT_TOP;
        // This method runs from scroll and global-layout callbacks. Reapplying
        // identical rules would schedule another layout and make an off-screen
        // selection bar continually relayout, so only move it when needed.
        if (mFakeSelectBar.getVisibility() == View.VISIBLE
                && mSelectBar.getVisibility() == View.INVISIBLE
                && params.getRule(expectedRule) != 0
                && params.getRule(oppositeRule) == 0) {
            return;
        }
        if (alignTop) {
            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        } else {
            params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        }
        mFakeSelectBar.setLayoutParams(params);
        refreshToolbarForCurrentMode();
        mSelectBar.setVisibility(View.INVISIBLE);
        mFakeSelectBar.setVisibility(View.VISIBLE);
    }
}
