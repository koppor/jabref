package org.jabref.model.entry;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

class IdGeneratorTest {

    @Test
    void createNeutralId() {
        HashSet<String> set = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            String string = IdGenerator.next();
            assertFalse(set.contains(string));
            set.add(string);
        }
    }
}
