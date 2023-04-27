package org.jabref.http.sync.state;

import com.google.common.base.Strings;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.SharedBibEntryData;
import org.jabref.model.strings.StringUtil;

public record HashInfo(String id, Integer hash) {
    /**
     * Converts the {@link SharedBibEntryData#sharedID} to a string following CUID2 for the structure.
     * We need to convert from int (covering 64k entries) to CUID2 to be able to serve endless numbers of entries
     */
    public HashInfo(int id, Integer hash) {
        this(Strings.padStart(Integer.toString(id), 10, '0'), hash);
    }

    public HashInfo(BibEntry entry) {
        this(entry.getSharedBibEntryData().getSharedID(), entry.hashCode());
    }
}
