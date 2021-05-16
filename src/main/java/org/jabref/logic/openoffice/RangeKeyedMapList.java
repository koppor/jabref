package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.sun.star.text.XTextRange;

public class RangeKeyedMapList<V> {
    RangeKeyedMap<List<V>> partitions;

    public RangeKeyedMapList() {
        this.partitions = new RangeKeyedMap<>();
    }

    public boolean containsKey(XTextRange range) {
        return partitions.containsKey(range);
    }

    public List<V> get(XTextRange range) {
        return partitions.get(range);
    }

    public void add(XTextRange range, V value) {
        List<V> values = partitions.get(range);
        if (values == null) {
            values = new ArrayList<>();
            values.add(value);
            partitions.put(range, values);
        } else {
            values.add(value);
        }
    }

    /**
     * @return A list of the partitions.
     */
    public List<TreeMap<XTextRange, List<V>>> partitionValues() {
        return this.partitions.partitionValues();
    }

    /**
     * Lis of all values: partitions in arbitrary order, ranges are
     * sorted within partitions, values under the same range are in
     * the order they were added.
     */
    public List<V> flatListOfValues() {
        List<V> result = new ArrayList<>();
        for (TreeMap<XTextRange, List<V>> partition : partitionValues()) {
            for (List<V> valuesUnderARange : partition.values()) {
                result.addAll(valuesUnderARange);
            }
        }
        return result;
    }

    public enum OverlapKind {
        TOUCH,
        OVERLAP,
        EQUAL_RANGE
    }

    /**
     *  Used in reporting range overlaps.
     *
     *  You probably want {@code V} to include information
     *  identifying the ranges.
     */
    public class RangeOverlap {
        public final OverlapKind kind;
        public final List<V> valuesForOverlappingRanges;

        public RangeOverlap(OverlapKind kind, List<V> valuesForOverlappingRanges) {
            this.kind = kind;
            this.valuesForOverlappingRanges = valuesForOverlappingRanges;
        }
    }

    /**
     * Report identical, overlapping or touching ranges.
     *
     * For overlapping and touching, only report consecutive ranges
     * and only with a single sample of otherwise identical ranges.
     *
     * @param atMost Limit the number of records returneed to atMost.
     *        Zero or negative {@code atMost} means no limit.
     */
    public List<RangeOverlap> findOverlappingRanges(int atMost, boolean includeTouching) {
        List<RangeOverlap> result = new ArrayList<>();
        for (TreeMap<XTextRange, List<V>> partition : partitions.partitionValues()) {
            List<XTextRange> orderedRanges = new ArrayList<>(partition.keySet());
            for (int i = 0; i < orderedRanges.size(); i++) {
                XTextRange aRange = orderedRanges.get(i);
                List<V> aValues = partition.get(aRange);
                if (aValues.size() > 1) {
                    result.add(new RangeOverlap(OverlapKind.EQUAL_RANGE, aValues));
                    if (atMost > 0 && result.size() >= atMost) {
                        return result;
                    }
                }
                if ((i + 1) < orderedRanges.size()) {
                    XTextRange bRange = orderedRanges.get(i + 1);
                    int cmp = UnoTextRange.compareStarts(aRange.getEnd(), bRange.getStart());
                    if (cmp > 0 || (includeTouching && (cmp == 0))) {
                        // found overlap or touch
                        List<V> bValues = partition.get(bRange);
                        List<V> valuesForOverlappingRanges = new ArrayList<>();
                        valuesForOverlappingRanges.add(aValues.get(0));
                        valuesForOverlappingRanges.add(bValues.get(0));
                        result.add(new RangeOverlap((cmp == 0) ? OverlapKind.TOUCH : OverlapKind.OVERLAP,
                                                    valuesForOverlappingRanges));
                    }
                    if (atMost > 0 && result.size() >= atMost) {
                        return result;
                    }
                }
            }
        }
        return result;
    }
}
