package org.jabref.logic.integrity;

import java.util.Collections;
import java.util.List;

import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DoiDuplicationCheckerTest {

    private final DoiDuplicationChecker checker = new DoiDuplicationChecker();
    private String doiA = "10.1023/A:1022883727209";
    private String doiB = "10.1177/1461444811422887";
    private String doiC = "10.1145/2568225.2568315";
    private BibEntry doiAEntry1 = new BibEntry().withField(StandardField.DOI, doiA);
    private BibEntry doiAEntry2 = new BibEntry().withField(StandardField.DOI, doiA);
    private BibEntry doiBEntry1 = new BibEntry().withField(StandardField.DOI, doiB);
    private BibEntry doiBEntry2 = new BibEntry().withField(StandardField.DOI, doiB);
    private BibEntry doiCEntry1 = new BibEntry().withField(StandardField.DOI, doiC);

    @Test
    public void testOnePairDuplicateDOI() {
        List<BibEntry> entries = List.of(doiAEntry1, doiAEntry2, doiCEntry1);
        BibDatabase database = new BibDatabase(entries);
        List<IntegrityMessage> results = List.of(new IntegrityMessage(Localization.lang("Same DOI used in multiple entries"), doiAEntry1, StandardField.DOI),
        new IntegrityMessage(Localization.lang("Same DOI used in multiple entries"), doiAEntry2, StandardField.DOI));
        assertEquals(results, checker.check(database));
    }

    @Test
    public void testMultiPairsDuplicateDOI() {
        List<BibEntry> entries = List.of(doiAEntry1, doiAEntry2, doiBEntry1, doiBEntry2, doiCEntry1);
        BibDatabase database = new BibDatabase(entries);
        List<IntegrityMessage> results = List.of(new IntegrityMessage(Localization.lang("Same DOI used in multiple entries"), doiAEntry1, StandardField.DOI),
                new IntegrityMessage(Localization.lang("Same DOI used in multiple entries"), doiAEntry2, StandardField.DOI),
                new IntegrityMessage(Localization.lang("Same DOI used in multiple entries"), doiBEntry1, StandardField.DOI),
                new IntegrityMessage(Localization.lang("Same DOI used in multiple entries"), doiBEntry2, StandardField.DOI));
        assertEquals(results, checker.check(database));
    }

    @Test
    public void testNoDuplicateDOI() {
        List<BibEntry> entries = List.of(doiAEntry1, doiBEntry1, doiCEntry1);
        BibDatabase database = new BibDatabase(entries);
        assertEquals(Collections.emptyList(), checker.check(database));
    }
}
