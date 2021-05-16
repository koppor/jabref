package org.jabref.model.openoffice;

import com.sun.star.text.XTextRange;

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
