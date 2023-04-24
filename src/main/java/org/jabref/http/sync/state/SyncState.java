package org.jabref.http.sync.state;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.http.dto.BibEntryDTO;

public class SyncState {
    private final BibDatabaseContext context;
    // mapping from the shared ID to the DTO
    private Map<Integer, BibEntryDTO> lastStateOfEntries = new HashMap<>();

    // globalRevisionId -> set of IDs
    private Map<Integer, Set<Integer>> idsUpdated = new HashMap<>();

    public SyncState(BibDatabaseContext context) {
        this.context = context;
    }

    /**
     * Adds or updates an entry. Caller has to ensure consistent state with BibDatabaseContext
     */
    public void putEntry(Integer globalRevision, BibEntry entry) {
        int sharedID = entry.getSharedBibEntryData().getSharedID();
        assert sharedID >= 0;
        lastStateOfEntries.put(sharedID, new BibEntryDTO(entry));
        idsUpdated.computeIfAbsent(globalRevision, k -> new HashSet<>()).add(sharedID);
    }

    /**
     * Returns all changes between the given revisions.
     * It also contains the hash values of all BibEntries of the server to enable a client to flag its view as dirty.
     *
     * @param fromRevision the revision to start from (exclusive)
     */
    public ChangesAndServerView changesAndServerView(Integer fromRevision) {
        List<BibEntryDTO> changes = idsUpdated.entrySet().stream()
                .filter(entry -> entry.getKey() > fromRevision)
                .flatMap(entry -> entry.getValue().stream())
                .distinct()
                .sorted()
                .map(sharedId -> lastStateOfEntries.get(sharedId))
                .collect(Collectors.toList());
        List<HashInfo> hashInfos = context.getEntries().stream()
                .map(entry -> new HashInfo(entry))
                .toList();
        return new ChangesAndServerView(changes, hashInfos);
    }

    /**
     * Required at testing to work around the single instance.
     * May be used at testing only.
     */
    public void reset() {
        lastStateOfEntries = new HashMap<>();
        idsUpdated = new HashMap<>();
    }
}
