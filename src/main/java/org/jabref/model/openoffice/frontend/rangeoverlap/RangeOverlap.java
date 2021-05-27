package org.jabref.model.openoffice.frontend.rangeoverlap;

import java.util.List;

/**
 *  Used in reporting range overlaps.
 *
 *  You probably want {@code V} to include information
 *  identifying the ranges.
 */
public class RangeOverlap<V> {
    public final RangeOverlapKind kind;
    public final List<V> valuesForOverlappingRanges;

    public RangeOverlap(RangeOverlapKind kind, List<V> valuesForOverlappingRanges) {
        this.kind = kind;
        this.valuesForOverlappingRanges = valuesForOverlappingRanges;
    }
}
