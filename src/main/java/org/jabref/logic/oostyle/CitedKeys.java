package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;

public class CitedKeys {

    /**
     * Order-preserving map from citation keys to associated data.
     */
    public LinkedHashMap<String, CitedKey> data;

    CitedKeys(LinkedHashMap<String, CitedKey> data) {
        this.data = data;
    }

    /**
     *  The cited keys in sorted order.
     */
    public List<CitedKey> values() {
        return new ArrayList<>(data.values());
    }

    /**
     * Sort entries for the bibliography.
     */
    void sortByComparator(Comparator<BibEntry> entryComparator) {
        List<CitedKey> cks = new ArrayList<>(data.values());
        cks.sort(new CitationSort.CitationComparator(entryComparator, true));
        LinkedHashMap<String, CitedKey> newData = new LinkedHashMap<>();
        for (CitedKey ck : cks) {
            newData.put(ck.citationKey, ck);
        }
        data = newData;
    }

    void numberCitedKeysInCurrentOrder() {
        int i = 1;
        for (CitedKey ck : data.values()) {
            ck.number = Optional.of(i); // was: -1 for UndefinedBibtexEntry
            i++;
        }
    }

    public void lookupInDatabases(List<BibDatabase> databases) {
        for (CitedKey ck : this.data.values()) {
            ck.lookupInDatabases(databases);
        }
    }

    void distributeDatabaseLookupResults(CitationGroups cgs) {
        for (CitedKey ck : this.data.values()) {
            ck.distributeDatabaseLookupResult(cgs);
        }
    }

    void distributeNumbers(CitationGroups cgs) {
        for (CitedKey ck : this.data.values()) {
            ck.distributeNumber(cgs);
        }
    }

    public void distributeUniqueLetters(CitationGroups cgs) {
        for (CitedKey ck : this.data.values()) {
            ck.distributeUniqueLetter(cgs);
        }
    }

} // class CitedKeys
