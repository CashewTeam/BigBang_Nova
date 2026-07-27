package com.cashewteam.novatext.android;

import android.content.Context;
import android.content.res.Resources;
import android.icu.text.BreakIterator;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.TreeSet;

public class BoomWordsLayout {

    private final static String TAG = "BoomWordsLayout";

    private final int mMaxRowNumber;
    private final int mBoomPageWidth;
    private final int mWordMinWidth;
    private final int mWordBaseWidth;
    private final int mPuncMinWidth;
    private final int mPuncBaseWidth;
    private final int mEditHalfWidthChipWidth;
    private final int mEditWordMinWidth;
    private final int mEditWordBaseWidth;
    private final TextPaint mWordPaint;
    private final TextPaint mPuncPaint;

    private RangeList<Word> mWords = new RangeList<Word>();
    private ArrayList<Integer> mRowStart = new ArrayList<Integer>();
    private ArrayList<Integer> mRowCount = new ArrayList<Integer>();
    private ArrayList<Boolean> mRowIsGap = new ArrayList<Boolean>();
    private ArrayList<Boolean> mRowIsEmpty = new ArrayList<Boolean>();
    private ArrayList<Integer> mHardBreaks = new ArrayList<Integer>();
    private int[] mIdToRow;
    private int mTouchedIndex;
    private String mOriText;
    private boolean mEditLayout;

    private class RangeList<E> extends ArrayList<E> {
        public void remove(int fromIndex, int toIndex) {
            if (fromIndex < toIndex) {
                removeRange(fromIndex, toIndex);
            }
        }
    }

    private class Word {
        public final String word;
        public final int start;
        public final boolean punc;

        public Word(String w, int s, boolean isPunc) {
            word = w;
            start = s;
            punc = isPunc;
        }
    }

    public BoomWordsLayout(Context context) {
        Resources res = context.getResources();
        final int displayWidth = res.getDisplayMetrics().widthPixels;
        mBoomPageWidth = displayWidth - res.getDimensionPixelSize(R.dimen.page_margin_left)
                - res.getDimensionPixelSize(R.dimen.page_margin_right);
        //mMaxRowNumber = displayWidth > 1080 ? 11 : 10;
        mMaxRowNumber = 1000;
        mWordMinWidth = res.getDimensionPixelSize(R.dimen.word_min_width);
        mWordBaseWidth = res.getDimensionPixelSize(R.dimen.word_base_width);
        mPuncMinWidth = res.getDimensionPixelSize(R.dimen.punc_min_width);
        mPuncBaseWidth = res.getDimensionPixelSize(R.dimen.punc_base_width);
        mEditHalfWidthChipWidth = res.getDimensionPixelSize(R.dimen.edit_half_width_chip_width);
        mEditWordMinWidth = res.getDimensionPixelSize(R.dimen.edit_word_min_width);
        mEditWordBaseWidth = res.getDimensionPixelSize(R.dimen.edit_word_base_width);
        mWordPaint = ((TextView) View.inflate(context, R.layout.boom_chip_layout, null)
                .findViewById(R.id.word)).getPaint();
        mPuncPaint = ((TextView) View.inflate(context, R.layout.boom_punc_layout, null)
                .findViewById(R.id.punc)).getPaint();
    }

    public boolean layoutWords(int[] segment, String text, int touchedIndex) {
        if (TextUtils.isEmpty(text)) {
            mEditLayout = false;
            mOriText = "";
            mWords.clear();
            mHardBreaks.clear();
            mTouchedIndex = -1;
            generateLayout();
            return true;
        }
        int puncIndexStart = -1;
        for (int i = 0; i < segment.length; ++i) {
            if (segment[i] == -1) {
                puncIndexStart = i;
                break;
            }
        }
        if (puncIndexStart == -1) return false;
        int[] newSeg = new int[puncIndexStart];
        int wordIndexStart = 0;
        ++puncIndexStart;
        int garbageOffset = 0;
        int touchIndexOffset = 0;
        StringBuilder newText = new StringBuilder();
        for (int i = 0; i < newSeg.length; i += 2) {
            int curWordStart = segment[i];
            int curPuncStart = puncIndexStart == segment.length ? text.length() : segment[puncIndexStart];
            if (curWordStart < curPuncStart) {
                if (curWordStart > wordIndexStart) {
                    int removedDiff = appendFilteredGap(newText, text, wordIndexStart, curWordStart);
                    if (touchedIndex > curWordStart) {
                        touchIndexOffset += removedDiff;
                    }
                    garbageOffset += removedDiff;
                } else if (curWordStart < wordIndexStart) {
                    Log.e(TAG, "Something wrong with rebuild segment curWordStart=" + curWordStart + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                newSeg[i] = segment[i] - garbageOffset;
                newSeg[i + 1] = segment[i + 1] - garbageOffset;
                wordIndexStart = segment[i + 1] + 1;
                newText.append(text.substring(segment[i], segment[i + 1] + 1));
            } else {
                if (curPuncStart > wordIndexStart) {
                    int removedDiff = appendFilteredGap(newText, text, wordIndexStart, curPuncStart);
                    if (touchedIndex > curPuncStart) {
                        touchIndexOffset += removedDiff;
                    }
                    garbageOffset += removedDiff;
                } else if (curPuncStart < wordIndexStart) {
                    Log.e(TAG, "Something wrong with rebuild segment curPuncStart=" + curPuncStart + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                wordIndexStart = segment[puncIndexStart + 1] + 1;
                newText.append(text.substring(segment[puncIndexStart], segment[puncIndexStart + 1] + 1));
                puncIndexStart += 2;
                i -= 2;
            }
        }
        if (puncIndexStart < segment.length) {
            for (int i = puncIndexStart; i < segment.length; i += 2) {
                int curPuncStart = segment[i];
                if (curPuncStart > wordIndexStart) {
                    int removedDiff = appendFilteredGap(newText, text, wordIndexStart, curPuncStart);
                    if (touchedIndex > curPuncStart) {
                        touchIndexOffset += removedDiff;
                    }
                } else if (curPuncStart < wordIndexStart) {
                    Log.e(TAG, "Something wrong with add ending punc curPuncStart=" + curPuncStart + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                wordIndexStart = segment[i + 1] + 1;
                newText.append(text.substring(segment[i], segment[i + 1] + 1));
            }
        }
        if (wordIndexStart < text.length()) {
            // Keep editable trailing spaces/newlines when returning to normal BigBang layout.
            int removedDiff = appendFilteredGap(newText, text, wordIndexStart, text.length());
            if (touchedIndex > wordIndexStart) {
                touchIndexOffset += removedDiff;
            }
        }
        return layoutWordsAfterFilter(newSeg, newText.toString(), touchedIndex - touchIndexOffset);
    }

    /**
     * Rebuild the layout as editable units. The editor owns the full text, so
     * unlike normal segmentation no non-whitespace characters are filtered.
     */
    public boolean layoutEditWords(String text) {
        mEditLayout = true;
        mOriText = text;
        mWords.clear();
        mHardBreaks.clear();
        mTouchedIndex = -1;
        for (int offset = 0; offset < text.length();) {
            final int codePoint = text.codePointAt(offset);
            final int next = offset + Character.charCount(codePoint);
            if (codePoint == '\r' || codePoint == '\n') {
                addHardBreak();
                offset = codePoint == '\r' && next < text.length() && text.charAt(next) == '\n'
                        ? next + 1 : next;
                continue;
            }
            final boolean isPunctuation = !Character.isLetterOrDigit(codePoint)
                    && !Character.isWhitespace(codePoint)
                    && !Character.isSpaceChar(codePoint);
            mWords.add(new Word(text.substring(offset, next), offset, isPunctuation));
            offset = next;
        }
        generateLayout();
        return true;
    }

    private int appendFilteredGap(StringBuilder newText, String text, int start, int end) {
        // Text not covered by cppjieba is still user content. In particular,
        // symbol-only and emoji sequences must keep their UTF-16 offsets so
        // punctuation layout can rebuild them as complete grapheme clusters.
        newText.append(text, start, end);
        return 0;
    }

    private boolean layoutWordsAfterFilter(int[] segment, String text, int touchedIndex) {
        mEditLayout = false;
        mOriText = text;
        mWords.clear();
        mHardBreaks.clear();
        mTouchedIndex = -1;
        int start;
        int end;
        int prev = 0;
        for (int i = 0; i < segment.length; i += 2) {
            start = segment[i];
            end = segment[i + 1] + 1;
            addGapIntoChips(prev, start);
            String trim = text.substring(start, end).replaceAll("\\p{Z}", " ").trim();
            if (!TextUtils.isEmpty(trim)) {
                if (touchedIndex >= start && touchedIndex < end) {
                    mTouchedIndex = mWords.size();
                }
                // A lone Latin letter or digit uses the same compact symbol
                // chip as punctuation. Multi-character words/numbers retain
                // the regular word width.
                mWords.add(new Word(trim, start, isCompactNormalToken(trim)));
            }
            prev = end;
        }
        addGapIntoChips(prev, text.length());

        final int wordCount = mWords.size();
        if (wordCount == 0) {
            // An edited document may intentionally contain only spaces/newlines.
            // It has no selectable chips in normal mode, but must still be committable.
            generateLayout();
            return true;
        }
        generateLayout();
        final int rowCount = mRowCount.size();
        if (rowCount > mMaxRowNumber) {
            if (mTouchedIndex == -1) {
                start = 0;
                end = mRowStart.get(mMaxRowNumber);
            } else {
                final int row = getRowForIndex(mTouchedIndex);
                if (row < mMaxRowNumber / 2) {
                    start = 0;
                    end = getRowStart(mMaxRowNumber);
                } else if (row >= rowCount - mMaxRowNumber / 2) {
                    start = getRowStart(rowCount - mMaxRowNumber);
                    end = wordCount;
                } else {
                    start = getRowStart(row - mMaxRowNumber / 2);
                    end = getRowStart(row + mMaxRowNumber / 2);
                }
            }
            mWords.remove(end, wordCount);
            mWords.remove(0, start);
            generateLayout();
        }
        return true;
    }

    private void addGapIntoChips(int start, int end) {
        BreakIterator characterBreaks = BreakIterator.getCharacterInstance();
        characterBreaks.setText(mOriText);
        for (int index = start; index < end;) {
            int next = characterBreaks.following(index);
            if (next == BreakIterator.DONE || next > end) {
                next = end;
            }
            final String unit = mOriText.substring(index, next);
            if (unit.indexOf('\n') >= 0) {
                addHardBreak();
            } else if (!isWhitespaceUnit(unit)) {
                mWords.add(new Word(unit, index, true));
            }
            index = next;
        }
    }

    private static boolean isWhitespaceUnit(String text) {
        for (int index = 0; index < text.length();) {
            final int codePoint = text.codePointAt(index);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
            index += Character.charCount(codePoint);
        }
        return true;
    }

    private void addHardBreak() {
        final int breakIndex = mWords.size();
        if (!mEditLayout && mHardBreaks.size() > 0
                && mHardBreaks.get(mHardBreaks.size() - 1) == breakIndex) {
            return;
        }
        mHardBreaks.add(breakIndex);
    }

    private static boolean isCompactNormalToken(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        final int codePoint = text.codePointAt(0);
        if (text.length() != Character.charCount(codePoint)) {
            return false;
        }
        return Character.isDigit(codePoint)
                || (Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN);
    }

    private int measureChip(int index) {
        final Word word = mWords.get(index);
        if (isEditHalfWidth(index)) {
            // Keep row calculation aligned with the compact chip's rendered width.
            return mEditHalfWidthChipWidth;
        }
        if (mEditLayout) {
            // The original editor uses a tighter 24dp minimum than normal-mode's 33dp chips.
            return Math.max(mEditWordMinWidth,
                    mEditWordBaseWidth + (int) mWordPaint.measureText(word.word));
        }
        if (word.punc) {
            final int minimumWidth = word.word.codePointCount(0, word.word.length()) > 1
                    ? mWordMinWidth : mPuncMinWidth;
            return Math.max(minimumWidth,
                    mPuncBaseWidth + (int)mPuncPaint.measureText(word.word));
        } else {
            return Math.max(mWordMinWidth, mWordBaseWidth + (int)mWordPaint.measureText(word.word));
        }
    }

    private void generateLayout() {
        int count = 0;
        int start = 0;
        int remain = mBoomPageWidth;
        mRowCount.clear();
        mRowStart.clear();
        mRowIsGap.clear();
        mRowIsEmpty.clear();
        mIdToRow = new int[mWords.size()];
        int hardBreakCursor = 0;
        for (int i = 0; i < mWords.size(); ++i) {
            while (hardBreakCursor < mHardBreaks.size()
                    && mHardBreaks.get(hardBreakCursor) == i) {
                if (count > 0) {
                    addRow(start, count, false);
                }
                addRow(i, 0, true, isEmptyHardBreak(hardBreakCursor, i));
                start = i;
                count = 0;
                remain = mBoomPageWidth;
                ++hardBreakCursor;
            }
            final int chipWidth = measureChip(i);
            if (chipWidth > remain) {
                if (count == 0) {
                    mIdToRow[i] = mRowCount.size();
                    addRow(i, 1, false);
                    start = i + 1;
                } else {
                    addRow(start, count, false);
                    start = i;
                    count = 0;
                    remain = mBoomPageWidth;
                    --i;
                }
            } else {
                ++count;
                remain -= chipWidth;
                mIdToRow[i] = mRowCount.size();
            }
        }
        if (count > 0) {
            addRow(start, count, false);
        }
        if (mEditLayout) {
            // A trailing or standalone newline is still an editable empty line.
            while (hardBreakCursor < mHardBreaks.size()
                    && mHardBreaks.get(hardBreakCursor) == mWords.size()) {
                addRow(mWords.size(), 0, true, isEmptyHardBreak(hardBreakCursor, mWords.size()));
                ++hardBreakCursor;
            }
        }
    }

    private void addRow(int start, int count, boolean isGap) {
        addRow(start, count, isGap, false);
    }

    private void addRow(int start, int count, boolean isGap, boolean isEmpty) {
        mRowStart.add(start);
        mRowCount.add(count);
        mRowIsGap.add(isGap);
        mRowIsEmpty.add(isEmpty);
    }

    /** A repeated hard-break index denotes an actual empty line, not line spacing. */
    static boolean isEmptyHardBreak(int hardBreakCursor, int currentWordIndex,
                                    ArrayList<Integer> hardBreaks) {
        return hardBreakCursor > 0
                && hardBreaks.get(hardBreakCursor - 1) == currentWordIndex;
    }

    private boolean isEmptyHardBreak(int hardBreakCursor, int currentWordIndex) {
        return isEmptyHardBreak(hardBreakCursor, currentWordIndex, mHardBreaks);
    }

    private boolean isHardBreakIndex(int index) {
        return mHardBreaks.contains(index);
    }

    public int getRowCount() {
        return mRowCount.size();
    }

    public int getRowStart(int row) {
        return mRowStart.get(row);
    }

    public int getColumnCount(int row) {
        return mRowCount.get(row);
    }

    public boolean isGapRow(int row) {
        return mRowIsGap.get(row);
    }

    /** True for the blank row introduced by a second consecutive line break. */
    public boolean isEmptyGapRow(int row) {
        return mRowIsEmpty.get(row);
    }

    public int getRowForIndex(int index) {
        if (index < 0 || index >= mIdToRow.length) {
            return 0;
        }
        return mIdToRow[index];
    }

    public boolean isPunc(int index) {
        return mWords.get(index).punc;
    }

    /**
     * Returns whether an editable unit is compact: ASCII, whitespace or a
     * punctuation code point. Chinese ideographs and emoji keep word width.
     */
    public boolean isEditHalfWidth(int index) {
        if (!mEditLayout || index < 0 || index >= mWords.size()) {
            return false;
        }
        return isEditHalfWidthUnit(mWords.get(index).word);
    }

    /** True only for an editable whitespace unit, never merely a narrow one. */
    public boolean isEditWhitespace(int index) {
        if (!mEditLayout || index < 0 || index >= mWords.size()) {
            return false;
        }
        return isEditWhitespaceUnit(mWords.get(index).word);
    }

    /** Package-private so the editor's compact ASCII/space policy stays unit-testable. */
    static boolean isEditHalfWidthUnit(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        final int codePoint = text.codePointAt(0);
        if (text.length() != Character.charCount(codePoint)) {
            return false;
        }
        if ((codePoint >= 0x20 && codePoint <= 0x7e)
                || (codePoint >= 0xff61 && codePoint <= 0xff9f)
                || Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)) {
            return true;
        }
        switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
                return true;
            default:
                return false;
        }
    }

    /** Package-private so background selection for spaces stays unit-testable. */
    static boolean isEditWhitespaceUnit(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        final int codePoint = text.codePointAt(0);
        return text.length() == Character.charCount(codePoint)
                && (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint));
    }

    public int getEditHalfWidthChipWidth() {
        return mEditHalfWidthChipWidth;
    }

    public int getEditWordMinWidth() {
        return mEditWordMinWidth;
    }

    public int getEditChipWidth(int index) {
        // Match the original editor: row layout and rendering use the same
        // measured width instead of treating it only as a TextView minimum.
        return measureChip(index);
    }

    public int getChipWidth(int index) {
        return measureChip(index);
    }

    public boolean isEditLayout() {
        return mEditLayout;
    }

    public String getWord(int index) {
        return mWords.get(index).word;
    }

    public int getWordEnd(int index) {
        return mWords.get(index).word.length() + mWords.get(index).start;
    }

    public int getWordStart(int index) {
        return mWords.get(index).start;
    }

    public String getOriText(int start, int end) {
        return mOriText.substring(start, end);
    }

    public String getOriText() {
        return mOriText;
    }

    public int getWordCount() {
        return mWords.size();
    }

    public int getTouchedIndex() {
        return mTouchedIndex;
    }

    public TreeSet<Integer> splitSelectedWordsToChars(TreeSet<Integer> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return null;
        }
        RangeList<Word> newWords = new RangeList<Word>();
        ArrayList<Integer> newHardBreaks = new ArrayList<Integer>();
        TreeSet<Integer> newSelected = new TreeSet<Integer>();
        boolean changed = false;
        for (int i = 0; i < mWords.size(); ++i) {
            if (isHardBreakIndex(i)) {
                newHardBreaks.add(newWords.size());
            }
            final Word word = mWords.get(i);
            final boolean selected = selectedIds.contains(i);
            if (!selected || word.punc || word.word.length() <= 1) {
                newWords.add(word);
                if (selected) {
                    newSelected.add(newWords.size() - 1);
                }
                continue;
            }
            changed = true;
            for (int offset = 0; offset < word.word.length();) {
                final int codePoint = word.word.codePointAt(offset);
                final int next = offset + Character.charCount(codePoint);
                final String unit = word.word.substring(offset, next);
                // Split Latin letters and digits must keep the same compact
                // symbol width as a single-token normal layout.
                newWords.add(new Word(unit, word.start + offset, isCompactNormalToken(unit)));
                newSelected.add(newWords.size() - 1);
                offset = next;
            }
        }
        if (!changed) {
            return null;
        }
        mWords = newWords;
        mHardBreaks = newHardBreaks;
        generateLayout();
        return newSelected;
    }
}
