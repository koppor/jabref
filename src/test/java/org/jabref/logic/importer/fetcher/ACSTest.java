package org.jabref.logic.importer.fetcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jabref.logic.importer.FulltextFetcher;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.support.DisabledOnCIServer;
import org.jabref.testutils.category.FetcherTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

@FetcherTest
class ACSTest {
    private FulltextFetcher fetcher = new ACS();

    @Test
    @DisabledOnCIServer("CI server is unreliable")
    void findByDOI() throws Exception {
        // DOI randomly chosen from https://pubs.acs.org/toc/acscii/0/0
        BibEntry entry = new BibEntry().withField(StandardField.DOI, "10.1021/acscentsci.4c00971");
        assertEquals(
                Optional.of(
                        URI.create("https://pubs.acs.org/doi/pdf/10.1021/acscentsci.4c00971")
                                .toURL()),
                fetcher.findFullText(entry));
    }

    @Test
    @DisabledOnCIServer("CI server is unreliable")
    void notFoundByDOI() throws Exception {
        BibEntry entry = new BibEntry().withField(StandardField.DOI, "10.1021/bk-2006-WWW.ch014");
        assertEquals(Optional.empty(), fetcher.findFullText(entry));
    }

    @Test
    void entityWithoutDoi() throws Exception {
        assertEquals(Optional.empty(), fetcher.findFullText(new BibEntry()));
    }

    @Test
    void trustLevel() {
        assertEquals(TrustLevel.PUBLISHER, fetcher.getTrustLevel());
    }
}
