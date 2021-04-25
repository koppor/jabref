package org.jabref.logic.openoffice;

import com.sun.star.text.XTextRange;

/**
 * Describe a protected range for overlap checking and reporting.
 *
 * To check that our protected ranges do not overlap, we collect
 * these ranges. To check for overlaps between these, we need the
 * {@code range} itself. To report the results of overlap
 * checking, we need a {@code description} that can be understood
 * by the user.
 *
 * To be able to refer back to more extended data, we might need
 * to identify its {@code kind}, and index ({@code i}) in the
 * corresponding tables.
 *
 */
public class RangeForOverlapCheck<T> {
    public final static int REFERENCE_MARK_KIND = 0;
    public final static int FOOTNOTE_MARK_KIND = 1;

    public final XTextRange range;
    public final int kind;
    public final T i; // TODO: rename to content if identifier or cgid
    private final String description;

    public RangeForOverlapCheck(XTextRange range, T i, int kind, String description) {
        this.range = range;
        this.kind = kind;
        this.i = i;
        this.description = description;
    }

    public String format() {
        return description;
    }

}
