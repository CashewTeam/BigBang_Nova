package com.cashewteam.novatext.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
}
