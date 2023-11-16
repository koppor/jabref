package org.jabref.logic.bst;

import org.jabref.model.database.BibDatabase;

import java.nio.file.Path;
import java.util.*;

public record BstVMContext(
        List<BstEntry> entries,
        Map<String, String> strings,
        Map<String, Integer> integers,
        Map<String, BstFunctions.BstFunction> functions,
        Deque<Object> stack,
        BibDatabase bibDatabase,
        Optional<Path> path) {
    public BstVMContext(List<BstEntry> entries, BibDatabase bibDatabase, Path path) {
        // LinkedList instead of ArrayDeque, because we (currently) need null support
        this(
                entries,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new LinkedList<>(),
                bibDatabase,
                Optional.ofNullable(path));
    }
}
