package org.jabref.logic.openoffice.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.jabref.model.openoffice.frontend.rangesort.RangeSortable;
import org.jabref.model.openoffice.uno.NoDocumentException;
import org.jabref.model.openoffice.uno.UnoScreenRefresh;

import com.sun.star.awt.Point;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextViewCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sort XTextRange values visually (top-down,left-to-right).
 *
 * Requires functional XTextViewCursor.
 *
 * Problem: for multicolumn layout and view pages side-by-side mode of
 *          LO, the (top-down,left-to-right) order interpreted
 *          as-on-the-screen: an XTextRange at the top of the second
 *          column or second page is sorted before one at the bottom
 *          of the first column of the first page.
 *
 */
class RangeSortVisual {

    private static final Logger LOGGER = LoggerFactory.getLogger(RangeSortVisual.class);

    /* first appearance order, based on visual order */

    /**
     *  Given a location, return its position: coordinates relative to
     *  the top left position of the first page of the document.
     *
     * Note: for text layouts with two or more columns, this gives the
     *       wrong order: top-down/left-to-right does not match
     *       reading order.
     *
     * Note: The "relative to the top left position of the first page"
     *       is meant "as it appears on the screen".
     *
     *       In particular: when viewing pages side-by-side, the top
     *       half of the right page is higher than the lower half of
     *       the left page. Again, top-down/left-to-right does not
     *       match reading order.
     *
     * @param range  Location.
     * @param cursor To get the position, we need az XTextViewCursor.
     *               It will be moved to the range.
     */
    private static Point findPositionOfTextRange(XTextRange range, XTextViewCursor cursor) {
        cursor.gotoRange(range, false);
        return cursor.getPosition();
    }

    /**
     * A reference mark name paired with its visual position.
     *
     * Comparison is based on (Y,X,indexInPosition): vertical compared
     * first, horizontal second, indexInPosition third.
     *
     * Used for sorting reference marks by their visual positions.
     *
     *
     *
     */
    private static class ComparableMark<T> implements Comparable<ComparableMark<T>> {

        private final Point position;
        private final int indexInPosition;
        private final T content;

        public ComparableMark(Point position, int indexInPosition, T content) {
            this.position = position;
            this.indexInPosition = indexInPosition;
            this.content = content;
        }

        @Override
        public int compareTo(ComparableMark other) {

            if (position.Y != other.position.Y) {
                return position.Y - other.position.Y;
            }
            if (position.X != other.position.X) {
                return position.X - other.position.X;
            }
            return indexInPosition - other.indexInPosition;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o instanceof ComparableMark) {
                ComparableMark other = (ComparableMark) o;
                return ((this.position.X == other.position.X)
                        && (this.position.Y == other.position.Y)
                        && (this.indexInPosition == other.indexInPosition)
                        && Objects.equals(this.content, other.content));
            }
            return false;
        }

        public T getContent() {
            return content;
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, indexInPosition, content);
        }
    }

    /**
     * Sort its input {@code vses} visually.
     *
     * Requires a functional {@code XTextViewCursor}.
     *
     * @return The input, sorted by the elements XTextRange and
     *          getIndexInPosition.
     */
    public static <T> List<RangeSortable<T>>
    visualSort(List<RangeSortable<T>> vses,
               XTextDocument doc,
               FunctionalTextViewCursor fcursor)
        throws
        WrappedTargetException,
        NoDocumentException {

        final int inputSize = vses.size();

        if (UnoScreenRefresh.hasControllersLocked(doc)) {
            LOGGER.warn("visualSort:"
                        + " with ControllersLocked, viewCursor.gotoRange"
                        + " is probably useless");
        }

        XTextViewCursor viewCursor = fcursor.getViewCursor();

        // find coordinates
        List<Point> positions = new ArrayList<>(vses.size());

        for (RangeSortable<T> v : vses) {
            positions.add(findPositionOfTextRange(v.getRange(),
                                                  viewCursor));
        }

        fcursor.restore(doc);

        if (positions.size() != inputSize) {
            throw new RuntimeException("visualSort: positions.size() != inputSize");
        }

        // order by position
        Set<ComparableMark<RangeSortable<T>>> set = new TreeSet<>();
        for (int i = 0; i < vses.size(); i++) {
            set.add(new ComparableMark<>(positions.get(i),
                                         vses.get(i).getIndexInPosition(),
                                         vses.get(i)));
        }

        if (set.size() != inputSize) {
            throw new RuntimeException("visualSort: set.size() != inputSize");
        }

        // collect ordered result
        List<RangeSortable<T>> result = new ArrayList<>(set.size());
        for (ComparableMark<RangeSortable<T>> mark : set) {
            result.add(mark.getContent());
        }

        if (result.size() != inputSize) {
            throw new RuntimeException("visualSort: result.size() != inputSize");
        }

        return result;
    }

}
