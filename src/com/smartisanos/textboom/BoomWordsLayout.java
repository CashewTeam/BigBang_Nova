package com.cashewteam.novatext.android;

import android.content.Context;
import android.content.res.Resources;
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
    private final TextPaint mWordPaint;
    private final TextPaint mPuncPaint;

    private RangeList<Word> mWords = new RangeList<Word>();
    private ArrayList<Integer> mRowStart = new ArrayList<Integer>();
    private ArrayList<Integer> mRowCount = new ArrayList<Integer>();
    private ArrayList<Boolean> mRowIsGap = new ArrayList<Boolean>();
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
        int preserved = 0;
        for (int i = start; i < end; ++i) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || Character.isSpaceChar(ch)) {
                newText.append(ch);
                ++preserved;
            }
        }
        return (end - start) - preserved;
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
                mWords.add(new Word(trim, start, false));
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
        for (int i = start; i < end; ++i) {
            char punc = mOriText.charAt(i);
            if (punc == '\n') {
                addHardBreak();
            } else if (!Character.isWhitespace(punc) && !Character.isSpaceChar(punc)) {
                mWords.add(new Word(String.valueOf(punc), i, true));
            }
        }
    }

    private void addHardBreak() {
        final int breakIndex = mWords.size();
        if (!mEditLayout && mHardBreaks.size() > 0
                && mHardBreaks.get(mHardBreaks.size() - 1) == breakIndex) {
            return;
        }
        mHardBreaks.add(breakIndex);
    }

    private int measureChip(int index) {
        final Word word = mWords.get(index);
        if (isEditHalfWidth(index)) {
            // Keep row calculation aligned with the compact chip's rendered width.
            return mEditHalfWidthChipWidth;
        }
        if (word.punc) {
            return Math.max(mPuncMinWidth, mPuncBaseWidth + (int)mPuncPaint.measureText(word.word));
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
        mIdToRow = new int[mWords.size()];
        int hardBreakCursor = 0;
        for (int i = 0; i < mWords.size(); ++i) {
            while (hardBreakCursor < mHardBreaks.size()
                    && mHardBreaks.get(hardBreakCursor) == i) {
                if (count > 0) {
                    addRow(start, count, false);
                }
                addRow(i, 0, true);
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
                addRow(mWords.size(), 0, true);
                ++hardBreakCursor;
            }
        }
    }

    private void addRow(int start, int count, boolean isGap) {
        mRowStart.add(start);
        mRowCount.add(count);
        mRowIsGap.add(isGap);
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
     * Returns whether an editable unit is one ASCII printable character
     * (including space/punctuation) or one half-width Katakana code point.
     */
    public boolean isEditHalfWidth(int index) {
        if (!mEditLayout || index < 0 || index >= mWords.size()) {
            return false;
        }
        final Word word = mWords.get(index);
        final String text = word.word;
        final int codePoint = text.codePointAt(0);
        return text.length() == Character.charCount(codePoint)
                && ((codePoint >= 0x20 && codePoint <= 0x7e)
                || (codePoint >= 0xff61 && codePoint <= 0xff9f));
    }

    public int getEditHalfWidthChipWidth() {
        return mEditHalfWidthChipWidth;
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
                newWords.add(new Word(word.word.substring(offset, next), word.start + offset, false));
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
