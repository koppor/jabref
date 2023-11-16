package org.jabref.logic.bst;

import org.jabref.model.entry.BibEntry;

import java.util.HashMap;
import java.util.Map;

public class BstEntry {

    public final BibEntry entry;

    public final Map<String, String> localStrings = new HashMap<>();

    public final Map<String, String> fields = new HashMap<>();

    public final Map<String, Integer> localIntegers = new HashMap<>();

    public BstEntry(BibEntry e) {
        this.entry = e;
    }
}
