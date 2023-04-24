package org.jabref.http.sync.state;

import org.jabref.http.dto.BibEntryDTO;

import java.util.List;

public record ChangesAndServerView(List<BibEntryDTO> changes, List<HashInfo> hashes) {
}
