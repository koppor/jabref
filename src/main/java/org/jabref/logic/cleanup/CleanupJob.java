package org.jabref.logic.cleanup;

import org.jabref.model.FieldChange;
import org.jabref.model.entry.BibEntry;

import java.util.List;

@FunctionalInterface
public interface CleanupJob {

    /**
     * Cleanup the entry.
     */
    List<FieldChange> cleanup(BibEntry entry);
}
