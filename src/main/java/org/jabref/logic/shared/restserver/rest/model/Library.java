package org.jabref.logic.shared.restserver.rest.model;

import java.util.List;

public class Library {
    public List<BibEntryDTO> bibEntries;

    public Library(List<BibEntryDTO> bibEntries) {
        this.bibEntries = bibEntries;
    }
}
