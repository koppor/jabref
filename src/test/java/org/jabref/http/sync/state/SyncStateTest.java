package org.jabref.http.sync.state;

import java.util.ArrayList;
import java.util.List;

import org.jabref.gui.importer.ImportEntriesViewModel;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.http.dto.BibEntryDTO;
import org.jabref.model.metadata.MetaData;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncStateTest {
    @Test
    void test() {
        BibEntry entryE1V1 = new BibEntry().withCitationKey("e1.v1").withSharedBibEntryData(1, 1);
        BibEntry entryE1V2 = new BibEntry().withCitationKey("e1.v2").withSharedBibEntryData(1, 2);
        BibEntry entryE2V1 = new BibEntry().withCitationKey("e2.v1").withSharedBibEntryData(2, 1);
        BibDatabaseContext bibDatabaseContext = new BibDatabaseContext(new BibDatabase(), new MetaData());
        bibDatabaseContext.getDatabase().insertEntries(entryE1V2, entryE2V1);
        SyncState syncState = new SyncState(bibDatabaseContext);

        syncState.putEntry(
                1, entryE1V1);
        syncState.putEntry(
                1, entryE2V1);
        syncState.putEntry(
                2, entryE1V2);

        ChangesAndServerView changes = syncState.changesAndServerView(0);
        assertEquals(new ChangesAndServerView(
                List.of(new BibEntryDTO(entryE1V2), new BibEntryDTO(entryE2V1)),
                List.of(
                        new HashInfo(1, entryE1V2.hashCode()),
                        new HashInfo(2, entryE2V1.hashCode()))),
                changes);
    }
}
