package org.jabref.logic.openoffice;

import com.sun.star.text.XTextRange;

/**
 * A simple implementation of {@code RangeSortable}
 */
public class RangeSortEntry<T> implements RangeSortable<T> {
    public XTextRange range;
    public int indexInPosition;
    public T content;

    public RangeSortEntry(XTextRange range,
                          int indexInPosition,
                          T content) {
        this.range = range;
        this.indexInPosition = indexInPosition;
        this.content = content;
    }

    @Override
    public XTextRange getRange() {
        return range;
    }

    @Override
    public int getIndexInPosition() {
        return indexInPosition;
    }

    @Override
    public T getContent() {
        return content;
    }
}
