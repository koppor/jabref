package org.jabref.model.openoffice;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.sun.star.text.XTextRange;

public class RangeKeyedMapList<V> {
    public RangeKeyedMap<List<V>> partitions;

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

}
