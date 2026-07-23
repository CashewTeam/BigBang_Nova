package com.cashewteam.novatext.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Tests the text transaction shared by delete, cut, and paste selection actions. */
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
}
