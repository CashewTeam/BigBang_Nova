package com.cashewteam.novatext.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Tests selection transactions plus the persisted edit-session decisions used by Stage C/D. */
public class BoomChipPageEditSelectionTest {

    @Test
    public void deleteSelectionKeepsCursorAtOriginalSelectionStart() {
        final BoomChipPage.EditTextMutation mutation = BoomChipPage.replaceSelectedText(
                "甲乙丙", new int[][]{{1, 2}}, "");

        assertEquals("甲丙", mutation.text);
        assertEquals(1, mutation.cursorOffset);
    }

    @Test
    public void pasteReplacesSelectionAndPlacesCursorAfterInsertedText() {
        final BoomChipPage.EditTextMutation mutation = BoomChipPage.replaceSelectedText(
                "甲乙丙", new int[][]{{1, 2}}, "XYZ");

        assertEquals("甲XYZ丙", mutation.text);
        assertEquals(4, mutation.cursorOffset);
    }

    @Test
    public void discontinuousSelectionInsertsReplacementOnlyAtFirstRange() {
        final BoomChipPage.EditTextMutation mutation = BoomChipPage.replaceSelectedText(
                "甲乙丙丁", new int[][]{{0, 1}, {2, 3}}, "X");

        assertEquals("X乙丁", mutation.text);
        assertEquals(1, mutation.cursorOffset);
    }

    @Test
    public void deleteSelectionDoesNotSplitEmojiSurrogatePair() {
        final BoomChipPage.EditTextMutation mutation = BoomChipPage.replaceSelectedText(
                "甲😀乙", new int[][]{{1, 3}}, "");

        assertEquals("甲乙", mutation.text);
        assertEquals(1, mutation.cursorOffset);
    }

    @Test
    public void deleteSelectedSpaceKeepsAdjacentWordsAndCursorOffset() {
        final BoomChipPage.EditTextMutation mutation = BoomChipPage.replaceSelectedText(
                "甲 乙", new int[][]{{1, 2}}, "");

        assertEquals("甲乙", mutation.text);
        assertEquals(1, mutation.cursorOffset);
    }

    @Test
    public void emptySelectionDoesNotCreateTransaction() {
        assertNull(BoomChipPage.replaceSelectedText("甲乙", new int[][]{{1, 1}}, "X"));
    }

    @Test
    public void dirtyStateDependsOnlyOnCurrentTextAndClearsAfterUndo() {
        assertFalse(BoomChipPage.hasEditTextChanged("原文", "原文"));
        assertTrue(BoomChipPage.hasEditTextChanged("原文", "原文已编辑"));
        // An undo that restores the original text must also remove the discard prompt.
        assertFalse(BoomChipPage.hasEditTextChanged("原文", "原文"));
    }

    @Test
    public void savedEditSessionKeepsTextAndHistoryForRotationRestore() {
        final BoomChipPage.EditSessionState state = new BoomChipPage.EditSessionState(
                "初始文本", "初始文本A", 5, null,
                new String[]{"初始文本"}, new String[]{"初始文本AB"});

        assertEquals("初始文本", state.originalText);
        assertEquals("初始文本A", state.text);
        assertEquals(1, state.undoHistory.length);
        assertEquals("初始文本", state.undoHistory[0]);
        assertEquals(1, state.redoHistory.length);
        assertEquals("初始文本AB", state.redoHistory[0]);
    }

    @Test
    public void originalSymbolPanelKeepsExactChineseAndEnglishCharacterSets() {
        assertArrayEquals(new String[]{
                "，", "。", "？", "！", "@", "、", "\\", "：", "；", " ", "~", "…"
        }, SymbolPanelPopup.CHINESE_SYMBOLS);
        assertArrayEquals(new String[]{
                ",", ".", "?", "!", "@", "/", "\\", ":", ";", " ", "~", "`"
        }, SymbolPanelPopup.ENGLISH_SYMBOLS);
    }

    @Test
    public void compactEditUnitsIncludeAsciiSpaceAndPunctuationOnly() {
        assertTrue(BoomWordsLayout.isEditHalfWidthUnit("A"));
        assertTrue(BoomWordsLayout.isEditHalfWidthUnit(" "));
        assertTrue(BoomWordsLayout.isEditHalfWidthUnit("，"));
        assertTrue(BoomWordsLayout.isEditHalfWidthUnit("　"));
        assertTrue(BoomWordsLayout.isEditHalfWidthUnit("?"));
        assertTrue(BoomWordsLayout.isEditHalfWidthUnit("｡"));
        assertFalse(BoomWordsLayout.isEditHalfWidthUnit("中"));
        assertFalse(BoomWordsLayout.isEditHalfWidthUnit("😀"));
    }

    @Test
    public void emptyAndTrailingLinesKeepDistinctCursorAnchorOffsets() {
        // The editor maps these break counts to actual 40dp gap rows after relayout.
        assertEquals(1, BoomChipPage.countLineBreaksBefore("甲\n", 2));
        assertEquals(2, BoomChipPage.countLineBreaksBefore("甲\n\n", 3));
        assertEquals(2, BoomChipPage.countLineBreaksBefore("\n\n乙", 2));
    }

    @Test
    public void cursorControlOrderMovesTheHandleAcrossAllFiveOriginalSlots() {
        // xxhdpi originals: 47dp actions, 7dp gaps and a 17/16dp content margin.
        final int actionWidth = 141;
        final int actionGap = 21;
        final int controlsWidth = 5 * actionWidth + 4 * actionGap;

        assertEquals(0, BigCursorView.getCursorHandleSlot(
                100f, 1080, controlsWidth, actionWidth, actionGap, 51, 48));
        assertEquals(1, BigCursorView.getCursorHandleSlot(
                300f, 1080, controlsWidth, actionWidth, actionGap, 51, 48));
        assertEquals(2, BigCursorView.getCursorHandleSlot(
                540f, 1080, controlsWidth, actionWidth, actionGap, 51, 48));
        assertEquals(3, BigCursorView.getCursorHandleSlot(
                780f, 1080, controlsWidth, actionWidth, actionGap, 51, 48));
        assertEquals(4, BigCursorView.getCursorHandleSlot(
                980f, 1080, controlsWidth, actionWidth, actionGap, 51, 48));
    }

    @Test
    public void cursorAnchorDoesNotMoveWhenTheControlStripReachesEitherScreenEdge() {
        final int actionWidth = 141;
        final int actionGap = 21;
        final int leftSlot = BigCursorView.getCursorHandleSlot(
                47f, 1080, 5 * actionWidth + 4 * actionGap,
                actionWidth, actionGap, 51, 48);
        final int rightSlot = BigCursorView.getCursorHandleSlot(
                1033f, 1080, 5 * actionWidth + 4 * actionGap,
                actionWidth, actionGap, 51, 48);

        final int left = BigCursorView.getCursorControlsLeft(
                47f, actionWidth, actionGap, leftSlot);
        final int right = BigCursorView.getCursorControlsLeft(
                1033f, actionWidth, actionGap, rightSlot);
        assertEquals(47f, left + leftSlot * (actionWidth + actionGap) + actionWidth / 2f, 0.5f);
        assertEquals(1033f,
                right + rightSlot * (actionWidth + actionGap) + actionWidth / 2f, 0.5f);
    }

    @Test
    public void handleDragKeepsTheBlueInsertionAnchorUnderTheSameFingerDelta() {
        assertEquals(420f,
                BigCursorView.mapHandleDragCoordinate(700f, 700f, 420f), 0f);
        assertEquals(456f,
                BigCursorView.mapHandleDragCoordinate(700f, 736f, 420f), 0f);
        assertEquals(384f,
                BigCursorView.mapHandleDragCoordinate(700f, 664f, 420f), 0f);
    }

    @Test
    public void handleMotionUsesSystemTouchSlopBeforeSuppressingPasteTap() {
        assertFalse(BigCursorView.exceedsTouchSlop(100f, 100f, 106f, 108f, 10));
        assertTrue(BigCursorView.exceedsTouchSlop(100f, 100f, 111f, 100f, 10));
    }

    @Test
    public void originalCursorEdgeScrollUsesSymmetricQuadraticVelocity() {
        assertEquals(0, BoomChipPage.getOriginalAutoScrollVelocity(100f, 100, 900));
        assertEquals(0, BoomChipPage.getOriginalAutoScrollVelocity(500f, 100, 900));
        assertEquals(-10, BoomChipPage.getOriginalAutoScrollVelocity(0f, 100, 900));
        assertEquals(10, BoomChipPage.getOriginalAutoScrollVelocity(1000f, 100, 900));
        assertTrue(BoomChipPage.shouldStopAutoScrollAtContentEdge(-10, false, true));
        assertTrue(BoomChipPage.shouldStopAutoScrollAtContentEdge(10, true, false));
        assertFalse(BoomChipPage.shouldStopAutoScrollAtContentEdge(10, true, true));
    }
}
