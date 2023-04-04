package org.jabref.http.sync.state;

import java.util.List;

import org.jabref.model.entry.BibEntry;
import org.jabref.http.dto.BibEntryDTO;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncStateTest {
    @Test void test() {
        BibEntry entryE1V1 = new BibEntry().withCitationKey("e1.v1").withSharedBibEntryData(1, 1);
        BibEntry entryE1V2 = new BibEntry().withCitationKey("e1.v2").withSharedBibEntryData(1, 2);
        BibEntry entryE2V1 = new BibEntry().withCitationKey("e2.v1").withSharedBibEntryData(2, 1);

        SyncState.INSTANCE.putEntry(
                1, entryE1V1);
        SyncState.INSTANCE.putEntry(
                1, entryE2V1);
        SyncState.INSTANCE.putEntry(
                2, entryE1V2);

        List<BibEntryDTO> changes = SyncState.INSTANCE.changes(0);
        assertEquals(List.of(new BibEntryDTO(entryE1V2), new BibEntryDTO(entryE2V1)), changes);
    }
}
