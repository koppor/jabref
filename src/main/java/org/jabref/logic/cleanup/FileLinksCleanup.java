package org.jabref.logic.cleanup;

import org.jabref.logic.bibtex.FileFieldWriter;
import org.jabref.model.FieldChange;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;
import org.jabref.model.entry.field.StandardField;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fixes the format of the file field. For example, if the file link is empty but the description wrongly contains the path.
 */
public class FileLinksCleanup implements CleanupJob {

    @Override
    public List<FieldChange> cleanup(BibEntry entry) {
        Optional<String> oldValue = entry.getField(StandardField.FILE);
        if (!oldValue.isPresent()) {
            return Collections.emptyList();
        }

        List<LinkedFile> fileList = entry.getFiles();

        // Parsing automatically moves a single description to link, so we just need to write the fileList back again
        String newValue = FileFieldWriter.getStringRepresentation(fileList);
        if (!oldValue.get().equals(newValue)) {
            entry.setField(StandardField.FILE, newValue);
            FieldChange change = new FieldChange(entry, StandardField.FILE, oldValue.get(), newValue);
            return Collections.singletonList(change);
        }
        return Collections.emptyList();
    }
}
