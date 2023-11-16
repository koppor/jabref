package org.jabref.logic.preview;

import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;

import java.util.Locale;

/**
 * Used for displaying a rendered entry in the UI. Due to historical reasons, "rendering" is called "layout".
 */
public interface PreviewLayout {

    String generatePreview(BibEntry entry, BibDatabaseContext databaseContext);

    String getDisplayName();

    String getName();

    default boolean containsCaseIndependent(String searchTerm) {
        return this.getDisplayName().toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT));
    }
}
