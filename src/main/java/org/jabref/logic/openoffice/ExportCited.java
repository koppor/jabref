package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jabref.logic.oostyle.CitedKey;
import org.jabref.logic.oostyle.CitedKeys;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.openoffice.NoDocumentException;

import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextDocument;

public class ExportCited {

    public static class GenerateDatabaseResult {
        /**
         * null: not done; isEmpty: no unresolved
         */
        public final List<String> unresolvedKeys;
        public final BibDatabase newDatabase;

        GenerateDatabaseResult(List<String> unresolvedKeys, BibDatabase newDatabase) {
            this.unresolvedKeys = unresolvedKeys;
            this.newDatabase = newDatabase;
        }
    }

    /**
     *
     * @param databases The databases to look up the citation keys in the document from.
     * @return A new database, with cloned entries.
     *
     * If a key is not found, it is added to result.unresolvedKeys
     *
     * Cross references (in StandardField.CROSSREF) are followed (not recursively):
     * if the referenced entry is found, it is included in the result.
     * If it is not found, it is silently ignored.
     */
    public static GenerateDatabaseResult generateDatabase(XTextDocument doc, List<BibDatabase> databases)
        throws
        NoDocumentException,
        NoSuchElementException,
        UnknownPropertyException,
        WrappedTargetException {

        OOFrontend fr = new OOFrontend(doc);
        CitedKeys cks = fr.citationGroups.getCitedKeysUnordered();
        cks.lookupInDatabases(databases);

        List<String> unresolvedKeys = new ArrayList<>();
        BibDatabase resultDatabase = new BibDatabase();

        List<BibEntry> entriesToInsert = new ArrayList<>();
        Set<String> seen = new HashSet<>(); // Only add crossReference once.

        for (CitedKey ck : cks.values()) {
            if (ck.db.isEmpty()) {
                unresolvedKeys.add(ck.citationKey);
                continue;
            } else {
                BibEntry entry = ck.db.get().entry;
                BibDatabase loopDatabase = ck.db.get().database;

                // If entry found
                BibEntry clonedEntry = (BibEntry) entry.clone();

                // Insert a copy of the entry
                entriesToInsert.add(clonedEntry);

                // Check if the cloned entry has a cross-reference field
                clonedEntry
                    .getField(StandardField.CROSSREF)
                    .ifPresent(crossReference -> {
                            boolean isNew = !seen.contains(crossReference);
                            if (isNew) {
                                // Add it if it is in the current library
                                loopDatabase
                                    .getEntryByCitationKey(crossReference)
                                    .ifPresent(entriesToInsert::add);
                                seen.add(crossReference);
                            }
                        });
            }
        }

        resultDatabase.insertEntries(entriesToInsert);
        return new GenerateDatabaseResult(unresolvedKeys, resultDatabase);
    }

}
