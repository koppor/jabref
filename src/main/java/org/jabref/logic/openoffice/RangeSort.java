package org.jabref.logic.openoffice;

import com.sun.star.text.XTextRange;

public class RangeSort {

    /**
     * This is what {@code visualSort} needs in its input.
     *
     * But actually there is nothing visual in it.
     * Maybe we could reuse it for other sorters.
     *
     */
    public interface RangeSortable<T> {
        public XTextRange getRange();

        public int getIndexInPosition();

        public T getContent();
    }

    /**
     * A simple implementation of {@code RangeSortable}
     */
    public static class RangeSortEntry<T> implements RangeSortable<T> {
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

}
