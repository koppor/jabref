package org.jabref.logic.oostyle;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import org.jabref.logic.layout.Layout;
import org.jabref.logic.layout.LayoutFormatterPreferences;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;
import org.jabref.model.entry.types.UnknownEntryType;
import org.jabref.model.oostyle.Citation;
import org.jabref.model.oostyle.CitationLookupResult;
import org.jabref.model.oostyle.CitationMarkerEntry;
import org.jabref.model.oostyle.NonUniqueCitationMarker;
import org.jabref.model.oostyle.OOText;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OOBibStyleTest {
    private LayoutFormatterPreferences layoutFormatterPreferences;

    @BeforeEach
    void setUp() {
        layoutFormatterPreferences = mock(LayoutFormatterPreferences.class, Answers.RETURNS_DEEP_STUBS);
    }

    @Test
    void testAuthorYear() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_AUTHORYEAR_STYLE_PATH, layoutFormatterPreferences);
        assertTrue(style.isValid());
        assertTrue(style.isInternalStyle());
        assertFalse(style.isCitationKeyCiteMarkers());
        assertFalse(style.isBoldCitations());
        assertFalse(style.isFormatCitations());
        assertFalse(style.isItalicCitations());
        assertFalse(style.isNumberEntries());
        assertFalse(style.isSortByPosition());
    }

    @Test
    void testAuthorYearAsFile() throws URISyntaxException, IOException {
        File defFile = Path.of(OOBibStyleTest.class.getResource(StyleLoader.DEFAULT_AUTHORYEAR_STYLE_PATH).toURI())
                           .toFile();
        OOBibStyle style = new OOBibStyle(defFile, layoutFormatterPreferences, StandardCharsets.UTF_8);
        assertTrue(style.isValid());
        assertFalse(style.isInternalStyle());
        assertFalse(style.isCitationKeyCiteMarkers());
        assertFalse(style.isBoldCitations());
        assertFalse(style.isFormatCitations());
        assertFalse(style.isItalicCitations());
        assertFalse(style.isNumberEntries());
        assertFalse(style.isSortByPosition());
    }

    @Test
    void testNumerical() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        assertTrue(style.isValid());
        assertFalse(style.isCitationKeyCiteMarkers());
        assertFalse(style.isBoldCitations());
        assertFalse(style.isFormatCitations());
        assertFalse(style.isItalicCitations());
        assertTrue(style.isNumberEntries());
        assertTrue(style.isSortByPosition());
    }

    List<CitationMarkerEntry> mkNum(OOBibStyle style,
                                    int minGroupingCount,
                                    Tuple3<String,Integer,String>... s) {
        List<CitationMarkerEntry> input = (Stream.of(s)
                                           .map(CitationMarkerEntryImpl::fromRaw)
                                           .collect(Collectors.toList()));
        return style.getNumCitationMarker2(input, minGroupingCount).asString());
    }

//    List<Optional<OOText>> asPageInfos(String... s) {
//        return (Stream.of(s)
//                .map(OOText::fromString)
//                .map(Optional::ofNullable)
//                .collect(Collectors.toList()));
//    }

    @Test
    void testGetNumCitationMarker() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        // Unfortunately these two are both "; " in
        // jabref/src/main/resources/resource/openoffice/default_numerical.jstyle
        // We might want to change one of them
        // style.getPageInfoSeparator();
        // style.getCitationSeparator();

        /* The default numerical style uses "[1]", no space after "]" for in-text,
         * but "[1] " with space after "]" for the bibliography.
         */
        assertEquals("[1]",  mkNum(style, -1, Tuple3("xx", 1, null)).asString());
        // style.getNumCitationMarker2(Arrays.asList(1), -1, empty).asString());

        // Identical numeric entries are joined.
        assertEquals("[1; 2]", mkNum(style, 3,
                                     Tuple3("x1",1,null),
                                     Tuple3("x2",2,null),
                                     Tuple3("x1",2,null),
                                     Tuple3("x2",1,null)));
        // style.getNumCitationMarker2(Arrays.asList(1,2,1,2), 3, empty).asString());


        // ... unless minGroupingCount <= 0
        assertEquals("[1; 1; 2; 2]", mkNum(style, 0,
                                     Tuple3("x1",1,null),
                                     Tuple3("x2",2,null),
                                     Tuple3("x1",2,null),
                                     Tuple3("x2",1,null)));
        // style.getNumCitationMarker2(Arrays.asList(1,2,1,2),0,empty).asString());

        // ... or have different pageInfos
        assertEquals("[1; p1a; 1; p1b; 2; p2; 3]", mkNum(style, 1,
                                                         Tuple3("x1", 1, "p1a"),
                                                         Tuple3("x1", 1, "p1b"),
                                                         Tuple3("x2", 2, "p2"),
                                                         Tuple3("x2", 2, "p2"),
                                                         Tuple3("x3", 3, null),
                                                         Tuple3("x3", 4, null)));
        // style.getNumCitationMarker2(Arrays.asList(1,1,2,2,3,3),
        // 1,
        // asPageInfos("p1a","p1b","p2","p2",
        // null, null)).asString());

        // Consecutive numbers can become a range ...
        assertEquals("[1-3]", mkNum(style, 1
                                    Tuple3("x1", 1, null),
                                    Tuple3("x2", 2, null),
                                    Tuple3("x3", 3, null)));
        // style.getNumCitationMarker2(Arrays.asList(1, 2, 3),
        //                            1, /* minGroupingCount */
        //                            empty).asString());

        // ... unless minGroupingCount is too high
        assertEquals("[1; 2; 3]", mkNum(style, 4,
                                        Tuple3("x1", 1, null),
                                        Tuple3("x2", 2, null),
                                        Tuple3("x3", 3, null)));
        // style.getNumCitationMarker2(Arrays.asList(1, 2, 3),
        // 4, /* minGroupingCount */
        // empty).asString());

        // ... or if minGroupingCount <= 0
        assertEquals("[1; 2; 3]", mkNum(style, 0,
                                        Tuple3("x1", 1, null),
                                        Tuple3("x2", 2, null),
                                        Tuple3("x3", 3, null)));
        // style.getNumCitationMarker2(Arrays.asList(1, 2, 3),
        // 0, /* minGroupingCount */
        // empty).asString());

        // ... a pageInfo needs to be emitted
        assertEquals("[1; p1; 2-3]", mkNum(style, 1,
                                           Tuple3("x1", 1, "p1"),
                                           Tuple3("x2", 2, null),
                                           Tuple3("x3", 3, null)));

        // style.getNumCitationMarker2(Arrays.asList(1, 2, 3),
        // 1, /* minGroupingCount */
        // asPageInfos("p1",null,null))
        // .asString());

        // null and "" pageInfos are taken as equal.
        // Due to trimming, "   " is the same as well.
        assertEquals("[1]", mkNum(style, 1,
                                  Tuple3("x1", 1, ""),
                                  Tuple3("x1", 1, null),
                                  Tuple3("x1", 1, "  ")));
        // style.getNumCitationMarker2(Arrays.asList(1, 1, 1),
        // 1, /* minGroupingCount */
        // asPageInfos("",null,"  "))
        // .asString());

        // pageInfos are trimmed
        assertEquals("[1; p1]", mkNum(style, 1,
                                      Tuple3("x1", 1, "p1"),
                                      Tuple3("x1", 1, " p1"),
                                      Tuple3("x1", 1, "p1 ")));
        // style.getNumCitationMarker2(Arrays.asList(1, 1, 1),
        // 1, /* minGroupingCount */
        // asPageInfos("p1"," p1","p1 "))
        // .asString());

        // The citation numbers come out sorted
        assertEquals("[3-5; 7; 10-12]", mkNum(style, 1,
                                              Tuple3("x12", 12, null),
                                              Tuple3("x7", 7, null),
                                              Tuple3("x3", 3, null),
                                              Tuple3("x4", 4, null),
                                              Tuple3("x11", 11, null),
                                              Tuple3("x10", 10, null),
                                              Tuple3("x5", 5, null)));
        // style.getNumCitationMarker2(Arrays.asList(12, 7, 3, 4, 11, 10, 5),
        // 1,
        // empty).asString());

        // pageInfos are sorted together with the numbers
        // (but they inhibit ranges where they are, even if they are identical,
        //  but not empty-or-null)
        assertEquals("[3; p3; 4; p4; 5; p5; 7; p7; 10; px; 11; px; 12; px]",
                     mkNum(style, 1,
                           Tuple3("x12", 12, "px"),
                           Tuple3("x7", 7, "p7"),
                           Tuple3("x3", 3, "p3"),
                           Tuple3("x4", 4, "p4"),
                           Tuple3("x11", 11, "px"),
                           Tuple3("x10", 10, "px"),
                           Tuple3("x5", 5, "p5")));

        // style.getNumCitationMarker2(Arrays.asList(12, 7, 3, 4, 11, 10, 5),
        // 1,
        // asPageInfos("px", "p7", "p3", "p4",
        // "px", "px", "p5"))
        // .asString());


        // pageInfo sorting (for the same number)
        assertEquals("[1; 1; a; 1; b]",
                     mkNum(style, 1,
                           Tuple3("x1", 1, ""),
                           Tuple3("x1", 1, "b"),
                           Tuple3("x1", 1, "a")));
        // style.getNumCitationMarker2(Arrays.asList(1, 1, 1),
        // 1, /* minGroupingCount */
        // asPageInfos("","b","a "))
        // .asString());

        // pageInfo sorting (for the same number) is not numeric.
        assertEquals("[1; p100; 1; p20; 1; p9]",
                     mkNum(style, 1,
                           Tuple3("x1", 1, "p20"),
                           Tuple3("x1", 1, "p9"),
                           Tuple3("x1", 1, "p100")));
        // style.getNumCitationMarker2(Arrays.asList(1, 1, 1),
        // 1, /* minGroupingCount */
        // asPageInfos("p20","p9","p100"))
        // .asString());

        assertEquals("[1-3]",
                     mkNum(style, 1,
                           Tuple3("x1", 1, null),
                           Tuple3("x2", 2, null),
                           Tuple3("x3", 3, null)));
        // style.getNumCitationMarker2(Arrays.asList(1, 2, 3),
        // 1, /* minGroupingCount */
        // empty).asString());

        assertEquals("[1; 2; 3]",
                     mkNum(style, 5,
                           Tuple3("x1", 1, null),
                           Tuple3("x2", 2, null),
                           Tuple3("x3", 3, null)));
        // style.getNumCitationMarker2(Arrays.asList(1, 2, 3),
        // 5,
        // empty).asString());

        assertEquals("[1; 2; 3]",
                     mkNum(style, -1,
                           Tuple3("x1", 1, null),
                           Tuple3("x2", 2, null),
                           Tuple3("x3", 3, null)));
        // style.getNumCitationMarker2(Arrays.asList(1, 2, 3),
        // -1,
        // empty).asString());

        assertEquals("[1; 3; 12]",
                     mkNum(style, 1,
                           Tuple3("x1", 1, null),
                           Tuple3("x12", 12, null),
                           Tuple3("x3", 3, null)));
        // style.getNumCitationMarker2(Arrays.asList(1, 12, 3),
        // 1,
        // empty).asString());

        assertEquals("[3-5; 7; 10-12]",
                     mkNum(style, 1,
                           Tuple3("x12", 12, ""),
                           Tuple3("x7", 7, ""),
                           Tuple3("x3", 3, ""),
                           Tuple3("x4", 4, ""),
                           Tuple3("x11", 11, ""),
                           Tuple3("x10", 10, ""),
                           Tuple3("x5", 5, "")));
        // style.getNumCitationMarker2(Arrays.asList(12, 7, 3, 4, 11, 10, 5),
        // 1,
        // empty).asString());

        /*
         * BIBLIOGRAPHY : I think
         * style.getNumCitationMarkerForBibliography(int num);
         * should be enough: we only need it for a single number, never more.
         * Consequently minGroupingCount is not needed.
         * Nor do we need pageInfo in the bibliography.
         */
        {
            Citation x = new Citation("key");
            x.setNumber(Optional.of(1));
            // x.setPageInfo(Optional.empty());
            assertEquals("[1] ",
                         style.getNumCitationMarkerForBibliography(x).asString());
        }

    }

    @Test
    void testGetNumCitationMarkerUndefined() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        List<Optional<OOText>> empty = null;

        // unresolved citations look like [??key]
        assertEquals("[" + OOBibStyle.UNDEFINED_CITATION_MARKER + "key" + "]",
                     mkNum(style, 1,
                           Tuple3("key",0,null)));
        // style.getNumCitationMarker2(Arrays.asList(0),
        // 1,
        // empty).asString());

        // pageInfo is shown for unresolved citations
        assertEquals("[" + OOBibStyle.UNDEFINED_CITATION_MARKER + "; p1]",
                     mkNum(style, 1,
                           Tuple3("key",0,"p1")));
        // style.getNumCitationMarker2(Arrays.asList(0),
        // 1,
        // asPageInfos("p1")).asString());

        // unresolved citations sorted to the front
        assertEquals("[" + OOBibStyle.UNDEFINED_CITATION_MARKER + "key" + "; 2-4]",
                     mkNum(style, 1,
                           Tuple3("x4",4,""),
                           Tuple3("x2",2,""),
                           Tuple3("x3",3,""),
                           Tuple3("key",0,"")));
        // style.getNumCitationMarker2(Arrays.asList(4, 2, 3, 0),
        // 1,
        // empty).asString());

        assertEquals("[" + OOBibStyle.UNDEFINED_CITATION_MARKER + "key" + "; 1-3]",
                     mkNum(style, 1,
                           Tuple3("x1",1,""),
                           Tuple3("x2",2,""),
                           Tuple3("y3",3,""),
                           Tuple3("key",0,"")));
        // style.getNumCitationMarker2(Arrays.asList(1, 2, 3, 0),
        // 1,
        // empty).asString());

        // multiple unresolved citations are not collapsed
        assertEquals("["
                     + OOBibStyle.UNDEFINED_CITATION_MARKER + "x1" + "; "
                     + OOBibStyle.UNDEFINED_CITATION_MARKER + "x1" + "; "
                     + OOBibStyle.UNDEFINED_CITATION_MARKER + "x3" + "]",
                     mkNum(style, 1,
                           Tuple3("x1",0,""),
                           Tuple3("x2",0,""),
                           Tuple3("x3",0,"")));
        // style.getNumCitationMarker2(Arrays.asList(0, 0, 0),
        // 1,
        // empty).asString());

        /*
         * BIBLIOGRAPHY
         */
        {
            Citation x = new Citation("key");
            // x.setPageInfo(Optional.empty());
            // x.setNumber(Optional.empty());
            assertEquals("[" + OOBibStyle.UNDEFINED_CITATION_MARKER + "] ",
                         style.getNumCitationMarkerForBibliography(x).asString());
        }

    }

    @Test
    void testGetCitProperty() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        assertEquals(", ", style.getStringCitProperty("AuthorSeparator"));
        assertEquals(3, style.getMaxAuthors());
        assertTrue(style.getMultiCiteChronological());
        assertEquals("Default", style.getCitationCharacterFormat());
        assertEquals("Default [number] style file.", style.getName());
        Set<String> journals = style.getJournals();
        assertTrue(journals.contains("Journal name 1"));
    }

    private static CitationMarkerEntry makeCitationMarkerEntry(String citationKey,
                                                               BibEntry entry,
                                                               BibDatabase database,
                                                               String uniqueLetterQ,
                                                               String pageInfoQ,
                                                               boolean isFirstAppearanceOfSource) {
        Objects.requireNonNull(citationKey);
        Citation result = new Citation(citationKey);
        result.setLookupResult(Optional.of(new CitationLookupResult(entry, database)));
        result.setUniqueLetter(Optional.ofNullable(uniqueLetterQ));
        Optional<OOText> pageInfo = Optional.ofNullable(OOText.fromString(pageInfoQ));
        result.setPageInfo(Citation.normalizePageInfo(pageInfo));
        result.setIsFirstAppearanceOfSource(isFirstAppearanceOfSource);
        return result;
    }

    @Test
    void testGetCitationMarker() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH, layoutFormatterPreferences);
        BibEntry entry = new BibEntry()
                .withField(StandardField.AUTHOR, "Gustav Bostr\\\"{o}m and Jaana W\\\"{a}yrynen and Marine Bod\\'{e}n and Konstantin Beznosov and Philippe Kruchten")
                .withField(StandardField.YEAR, "2006")
                .withField(StandardField.BOOKTITLE, "SESS '06: Proceedings of the 2006 international workshop on Software engineering for secure systems")
                .withField(StandardField.PUBLISHER, "ACM")
                .withField(StandardField.TITLE, "Extending XP practices to support security requirements engineering")
                .withField(StandardField.PAGES, "11--18");
        BibDatabase database = new BibDatabase();
        database.insertEntry(entry);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
        CitationMarkerEntry cm =
            makeCitationMarkerEntry("Bostrom2006", entry, database, null, null, false);
        citationMarkerEntries.add(cm);

        assertEquals(3, style.getMaxAuthors());
        assertEquals(-1, style.getMaxAuthorsFirst());

        /*
         * For in-text citations most (maybe all) styles prescribe a single
         * author's name before "et al."
         */
        assertEquals("[Boström et al., 2006]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());

        assertEquals("Boström et al. [2006]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              false,
                                              NonUniqueCitationMarker.THROWS).asString());

        /*
         * Currently there is no way override for getMaxAuthors(), except
         * cm.isFirstAppearanceOfSource, which asks for getMaxAuthorsFirst()
         */
        citationMarkerEntries.clear();
        cm = makeCitationMarkerEntry("Bostrom2006", entry, database, null, null, true);
        citationMarkerEntries.add(cm);

        assertEquals("[Boström, Wäyrynen, Bodén, Beznosov & Kruchten, 2006]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());
    }

    @Test
    void testGetCitationMarkerJoinFirst() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                                          layoutFormatterPreferences);

        // Question: What should happen if some of the sources is
        // marked as isFirstAppearanceOfSource?
        // This test documents what is happening now,
        // but it is possible this is not what should.

        // Two entries with identical normalizedMarkers and many authors.
        BibEntry entry1 = new BibEntry()
                .withField(StandardField.AUTHOR,
                           "Gustav Bostr\\\"{o}m"
                           + " and Jaana W\\\"{a}yrynen"
                           + " and Marine Bod\\'{e}n"
                           + " and Konstantin Beznosov"
                           + " and Philippe Kruchten")
                .withField(StandardField.YEAR, "2006")
                .withField(StandardField.BOOKTITLE, "A book 1")
                .withField(StandardField.PUBLISHER, "ACM")
                .withField(StandardField.TITLE, "Title 1")
                .withField(StandardField.PAGES, "11--18");

        BibEntry entry2 = new BibEntry()
                .withField(StandardField.AUTHOR,
                           "Gustav Bostr\\\"{o}m"
                           + " and Jaana W\\\"{a}yrynen"
                           + " and Marine Bod\\'{e}n"
                           + " and Konstantin Beznosov"
                           + " and Philippe Kruchten")
                .withField(StandardField.YEAR, "2006")
                .withField(StandardField.BOOKTITLE, "A book 2")
                .withField(StandardField.PUBLISHER, "ACM")
                .withField(StandardField.TITLE, "title2")
                .withField(StandardField.PAGES, "11--18");

        // Last Author differs.
        BibEntry entry3 = new BibEntry()
                .withField(StandardField.AUTHOR,
                           "Gustav Bostr\\\"{o}m"
                           + " and Jaana W\\\"{a}yrynen"
                           + " and Marine Bod\\'{e}n"
                           + " and Konstantin Beznosov"
                           + " and Philippe NotKruchten")
                .withField(StandardField.YEAR, "2006")
                .withField(StandardField.BOOKTITLE, "A book 3")
                .withField(StandardField.PUBLISHER, "ACM")
                .withField(StandardField.TITLE, "title3")
                .withField(StandardField.PAGES, "11--18");

        BibDatabase database = new BibDatabase();
        database.insertEntry(entry1);
        database.insertEntry(entry2);
        database.insertEntry(entry3);

        // Without pageInfo, two isFirstAppearanceOfSource may be joined.
        // The third is NotKruchten, should not be joined.
        if (true) {
            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
            CitationMarkerEntry cm1 =
                makeCitationMarkerEntry("b1", entry1, database, "a", null, true);
            citationMarkerEntries.add(cm1);
            CitationMarkerEntry cm2 =
                makeCitationMarkerEntry("b2", entry2, database, "b", null, true);
            citationMarkerEntries.add(cm2);
            CitationMarkerEntry cm3 =
                makeCitationMarkerEntry("b3", entry3, database, "c", null, true);
            citationMarkerEntries.add(cm3);

            assertEquals("[Boström, Wäyrynen, Bodén, Beznosov & Kruchten, 2006a,b"
                         +"; Boström, Wäyrynen, Bodén, Beznosov & NotKruchten, 2006c]",
                         style.getCitationMarker2(citationMarkerEntries,
                                                  true,
                                                  NonUniqueCitationMarker.THROWS).asString());

            assertEquals("Boström, Wäyrynen, Bodén, Beznosov & Kruchten [2006a,b]"
                         + "; Boström, Wäyrynen, Bodén, Beznosov & NotKruchten [2006c]",
                         style.getCitationMarker2(citationMarkerEntries,
                                                  false,
                                                  NonUniqueCitationMarker.THROWS).asString());
        }

        // Without pageInfo, only the first is isFirstAppearanceOfSource.
        // The second may be joined, based on expanded normalizedMarkers.
        // The third is NotKruchten, should not be joined.
        if (true) {
            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
            CitationMarkerEntry cm1 =
                makeCitationMarkerEntry("b1", entry1, database, "a", null, true);
            citationMarkerEntries.add(cm1);
            CitationMarkerEntry cm2 =
                makeCitationMarkerEntry("b2", entry2, database, "b", null, false);
            citationMarkerEntries.add(cm2);
            CitationMarkerEntry cm3 =
                makeCitationMarkerEntry("b3", entry3, database, "c", null, false);
            citationMarkerEntries.add(cm3);

            assertEquals("[Boström, Wäyrynen, Bodén, Beznosov & Kruchten, 2006a,b"
                         +"; Boström et al., 2006c]",
                         style.getCitationMarker2(citationMarkerEntries,
                                                  true,
                                                  NonUniqueCitationMarker.THROWS).asString());

        }
        // Without pageInfo, only the second is isFirstAppearanceOfSource.
        // The second is not joined, because it is a first appearance, thus
        // requires more names to be shown.
        // The third is NotKruchten, should not be joined.
        if (true) {
            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
            CitationMarkerEntry cm1 =
                makeCitationMarkerEntry("b1", entry1, database, "a", null, false);
            citationMarkerEntries.add(cm1);
            CitationMarkerEntry cm2 =
                makeCitationMarkerEntry("b2", entry2, database, "b", null, true);
            citationMarkerEntries.add(cm2);
            CitationMarkerEntry cm3 =
                makeCitationMarkerEntry("b3", entry3, database, "c", null, false);
            citationMarkerEntries.add(cm3);

            assertEquals("[Boström et al., 2006a"
                         + "; Boström, Wäyrynen, Bodén, Beznosov & Kruchten, 2006b"
                         + "; Boström et al., 2006c]",
                         style.getCitationMarker2(citationMarkerEntries,
                                                  true,
                                                  NonUniqueCitationMarker.THROWS).asString());
        }

        // Without pageInfo, only the neither is isFirstAppearanceOfSource.
        // The second is joined.
        // The third is NotKruchten, but is joined because NotKruchten is not among the names shown.
        // Is this the correct behaviour?
        if (true) {
            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
            CitationMarkerEntry cm1 =
                makeCitationMarkerEntry("b1", entry1, database, "a", null, false);
            citationMarkerEntries.add(cm1);
            CitationMarkerEntry cm2 =
                makeCitationMarkerEntry("b2", entry2, database, "b", null, false);
            citationMarkerEntries.add(cm2);
            CitationMarkerEntry cm3 =
                makeCitationMarkerEntry("b3", entry3, database, "c", null, false);
            citationMarkerEntries.add(cm3);

            assertEquals("[Boström et al., 2006a,b,c]",
                         style.getCitationMarker2(citationMarkerEntries,
                                                  true,
                                                  NonUniqueCitationMarker.THROWS).asString());
        }

        // With pageInfo: different entries with identical non-null pageInfo: not joined.
        // XY [2000a,b,c; p1] whould be confusing.
        if (true) {
            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
            CitationMarkerEntry cm1 =
                makeCitationMarkerEntry("b1", entry1, database, "a", "p1", false);
            citationMarkerEntries.add(cm1);
            CitationMarkerEntry cm2 =
                makeCitationMarkerEntry("b2", entry2, database, "b", "p1", false);
            citationMarkerEntries.add(cm2);
            CitationMarkerEntry cm3 =
                makeCitationMarkerEntry("b3", entry3, database, "c", "p1", false);
            citationMarkerEntries.add(cm3);

            assertEquals("[Boström et al., 2006a; p1"
                         + "; Boström et al., 2006b; p1"
                         + "; Boström et al., 2006c; p1]",
                         style.getCitationMarker2(citationMarkerEntries,
                                                  true,
                                                  NonUniqueCitationMarker.THROWS).asString());
        }

        // With pageInfo: same entries with identical non-null pageInfo: collapsed.
        // Note: "same" here looks at the visible parts and citation key only,
        //       but ignores the rest. Normally the citation key should distinguish.
        if (true) {
            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
            CitationMarkerEntry cm1 =
                makeCitationMarkerEntry("b1", entry1, database, "a", "p1", false);
            citationMarkerEntries.add(cm1);
            CitationMarkerEntry cm2 =
                makeCitationMarkerEntry("b1", entry1, database, "a", "p1", false);
            citationMarkerEntries.add(cm2);
            CitationMarkerEntry cm3 =
                makeCitationMarkerEntry("b1", entry1, database, "a", "p1", false);
            citationMarkerEntries.add(cm3);

            assertEquals("[Boström et al., 2006a; p1]",
                         style.getCitationMarker2(citationMarkerEntries,
                                                  true,
                                                  NonUniqueCitationMarker.THROWS).asString());
        }
        // With pageInfo: same entries with different pageInfo: kept separate.
        // Empty ("") and null pageInfos considered equal her, collapsed.
        if (true) {
            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
            CitationMarkerEntry cm1 =
                makeCitationMarkerEntry("b1", entry1, database, "a", "p1", false);
            citationMarkerEntries.add(cm1);
            CitationMarkerEntry cm2 =
                makeCitationMarkerEntry("b1", entry1, database, "a", "p2", false);
            citationMarkerEntries.add(cm2);
            CitationMarkerEntry cm3 =
                makeCitationMarkerEntry("b1", entry1, database, "a", "", false);
            citationMarkerEntries.add(cm3);
            CitationMarkerEntry cm4 =
                makeCitationMarkerEntry("b1", entry1, database, "a", null, false);
            citationMarkerEntries.add(cm4);

            assertEquals("[Boström et al., 2006a; p1"
                         + "; Boström et al., 2006a; p2"
                         + "; Boström et al., 2006a]",
                         style.getCitationMarker2(citationMarkerEntries,
                                                  true,
                                                  NonUniqueCitationMarker.THROWS).asString());
        }
    }


    @Test
    void testLayout() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH, layoutFormatterPreferences);

        BibEntry entry = new BibEntry()
                .withField(StandardField.AUTHOR, "Gustav Bostr\\\"{o}m and Jaana W\\\"{a}yrynen and Marine Bod\\'{e}n and Konstantin Beznosov and Philippe Kruchten")
                .withField(StandardField.YEAR, "2006")
                .withField(StandardField.BOOKTITLE, "SESS '06: Proceedings of the 2006 international workshop on Software engineering for secure systems")
                .withField(StandardField.PUBLISHER, "ACM")
                .withField(StandardField.TITLE, "Extending XP practices to support security requirements engineering")
                .withField(StandardField.PAGES, "11--18");
        BibDatabase database = new BibDatabase();
        database.insertEntry(entry);

        Layout l = style.getReferenceFormat(new UnknownEntryType("default"));
        l.setPostFormatter(new OOPreFormatter());
        assertEquals(
                "Boström, G.; Wäyrynen, J.; Bodén, M.; Beznosov, K. and Kruchten, P. (<b>2006</b>). <i>Extending XP practices to support security requirements engineering</i>,   : 11-18.",
                l.doLayout(entry, database));

        l = style.getReferenceFormat(StandardEntryType.InCollection);
        l.setPostFormatter(new OOPreFormatter());
        assertEquals(
                "Boström, G.; Wäyrynen, J.; Bodén, M.; Beznosov, K. and Kruchten, P. (<b>2006</b>). <i>Extending XP practices to support security requirements engineering</i>. In:  (Ed.), <i>SESS '06: Proceedings of the 2006 international workshop on Software engineering for secure systems</i>, ACM.",
                l.doLayout(entry, database));
    }

    @Test
    void testInstitutionAuthor() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH, layoutFormatterPreferences);
        BibDatabase database = new BibDatabase();

        Layout l = style.getReferenceFormat(StandardEntryType.Article);
        l.setPostFormatter(new OOPreFormatter());

        BibEntry entry = new BibEntry();
        entry.setType(StandardEntryType.Article);
        entry.setField(StandardField.AUTHOR, "{JabRef Development Team}");
        entry.setField(StandardField.TITLE, "JabRef Manual");
        entry.setField(StandardField.YEAR, "2016");
        database.insertEntry(entry);
        assertEquals("<b>JabRef Development Team</b> (<b>2016</b>). <i>JabRef Manual</i>,  .",
                l.doLayout(entry, database));
    }

    @Test
    void testVonAuthor() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        BibDatabase database = new BibDatabase();

        Layout l = style.getReferenceFormat(StandardEntryType.Article);
        l.setPostFormatter(new OOPreFormatter());

        BibEntry entry = new BibEntry();
        entry.setType(StandardEntryType.Article);
        entry.setField(StandardField.AUTHOR, "Alpha von Beta");
        entry.setField(StandardField.TITLE, "JabRef Manual");
        entry.setField(StandardField.YEAR, "2016");
        database.insertEntry(entry);
        assertEquals("<b>von Beta, A.</b> (<b>2016</b>). <i>JabRef Manual</i>,  .",
                l.doLayout(entry, database));
    }

    @Test
    void testInstitutionAuthorMarker() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        BibDatabase database = new BibDatabase();

        BibEntry entry = new BibEntry();
        entry.setType(StandardEntryType.Article);
        entry.setField(StandardField.AUTHOR, "{JabRef Development Team}");
        entry.setField(StandardField.TITLE, "JabRef Manual");
        entry.setField(StandardField.YEAR, "2016");
        database.insertEntry(entry);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
        CitationMarkerEntry cm =
            makeCitationMarkerEntry("JabRef2016", entry, database, null, null, false);
        citationMarkerEntries.add(cm);
        assertEquals("[JabRef Development Team, 2016]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());
    }

    @Test
    void testVonAuthorMarker() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        BibDatabase database = new BibDatabase();

        BibEntry entry = new BibEntry();
        entry.setType(StandardEntryType.Article);
        entry.setField(StandardField.AUTHOR, "Alpha von Beta");
        entry.setField(StandardField.TITLE, "JabRef Manual");
        entry.setField(StandardField.YEAR, "2016");
        database.insertEntry(entry);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
        CitationMarkerEntry cm =
            makeCitationMarkerEntry("vonBeta2016", entry, database, null, null, false);
        citationMarkerEntries.add(cm);

        assertEquals("[von Beta, 2016]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());
    }

    @Test
    void testNullAuthorMarker() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        BibDatabase database = new BibDatabase();

        BibEntry entry = new BibEntry();
        entry.setType(StandardEntryType.Article);
        entry.setField(StandardField.YEAR, "2016");
        database.insertEntry(entry);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
        CitationMarkerEntry cm =
            makeCitationMarkerEntry("anon2016", entry, database, null, null, false);
        citationMarkerEntries.add(cm);

        assertEquals("[, 2016]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());
    }

    @Test
    void testNullYearMarker() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        BibDatabase database = new BibDatabase();

        BibEntry entry = new BibEntry();
        entry.setType(StandardEntryType.Article);
        entry.setField(StandardField.AUTHOR, "Alpha von Beta");
        database.insertEntry(entry);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
        CitationMarkerEntry cm =
            makeCitationMarkerEntry("vonBetaNNNN", entry, database, null, null, false);
        citationMarkerEntries.add(cm);

        assertEquals("[von Beta, ]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());
    }

    @Test
    void testEmptyEntryMarker() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        BibDatabase database = new BibDatabase();

        BibEntry entry = new BibEntry();
        entry.setType(StandardEntryType.Article);
        database.insertEntry(entry);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
        CitationMarkerEntry cm =
            makeCitationMarkerEntry("Empty", entry, database, null, null, false);
        citationMarkerEntries.add(cm);

        assertEquals("[, ]", style.getCitationMarker2(citationMarkerEntries,
                                                      true,
                                                      NonUniqueCitationMarker.THROWS)
                     .asString());
    }

    @Test
    void testGetCitationMarkerUniquefiers() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        BibDatabase database = new BibDatabase();

        // Three different entries, the first two having the same
        // normalized citation marks.
        BibEntry entry1 = new BibEntry();
        entry1.setField(StandardField.AUTHOR, "Alpha Beta");
        entry1.setField(StandardField.TITLE, "Paper 1");
        entry1.setField(StandardField.YEAR, "2000");
        database.insertEntry(entry1);

        BibEntry entry3 = new BibEntry();
        entry3.setField(StandardField.AUTHOR, "Alpha Beta");
        entry3.setField(StandardField.TITLE, "Paper 2");
        entry3.setField(StandardField.YEAR, "2000");
        database.insertEntry(entry3);

        BibEntry entry2 = new BibEntry();
        entry2.setField(StandardField.AUTHOR, "Gamma Epsilon");
        entry2.setField(StandardField.YEAR, "2001");
        database.insertEntry(entry2);


        // Without uniquefiers this is a problem getCitationMarker cannot
        // solve, since it does not see the whole picture (citations outside its scope).
        // It can throw a RuntimeException or forgive and provide a flawed presentation.
        //
        // This latter is used for providing a temporary citation mark
        // for newly inserted citations.
        CitationMarkerEntry cm1a =
            makeCitationMarkerEntry("Beta2000a", entry1, database, null, null, false);
        CitationMarkerEntry cm3a =
            makeCitationMarkerEntry("Beta2000b", entry3, database, null, null, false);
        CitationMarkerEntry cm2 =
            makeCitationMarkerEntry("Epsilon2001", entry2, database, null, null, false);

        List<CitationMarkerEntry> citationMarkerEntriesA = new ArrayList<>();
        citationMarkerEntriesA.add(cm1a);
        citationMarkerEntriesA.add(cm3a);
        citationMarkerEntriesA.add(cm2);

        // Consecutive, different source without distinguishing uniquefiers
        // can throw a RuntimeException.
        boolean doesItThrow = false;
        try {
            style.getCitationMarker2(citationMarkerEntriesA,
                                     false,
                                     NonUniqueCitationMarker.THROWS);
        } catch (RuntimeException ex) {
            doesItThrow = true;
        }
        assertEquals(true, doesItThrow);

        // Or can just emit a presentation with repeated marks.
        assertEquals("[Beta, 2000; Beta, 2000; Epsilon, 2001]",
                     style.getCitationMarker2(citationMarkerEntriesA,
                                              true,
                                              NonUniqueCitationMarker.FORGIVEN).asString());

        assertEquals("Beta [2000]; Beta [2000]; Epsilon [2001]",
                     style.getCitationMarker2(citationMarkerEntriesA,
                                              false,
                                              NonUniqueCitationMarker.FORGIVEN).asString());


        // With uniquefiers
        CitationMarkerEntry cm1b =
            makeCitationMarkerEntry("Beta2000a", entry1, database, "a", null, false);
        CitationMarkerEntry cm3b =
            makeCitationMarkerEntry("Beta2000b", entry3, database, "b", null, false);

        List<CitationMarkerEntry> citationMarkerEntriesB = new ArrayList<>();
        citationMarkerEntriesB.add(cm1b);
        citationMarkerEntriesB.add(cm3b);
        citationMarkerEntriesB.add(cm2);

        assertEquals("[Beta, 2000a,b; Epsilon, 2001]",
                     style.getCitationMarker2(citationMarkerEntriesB,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());

        assertEquals("Beta [2000a,b]; Epsilon [2001]",
                     style.getCitationMarker2(citationMarkerEntriesB,
                                              false,
                                              NonUniqueCitationMarker.THROWS).asString());
    }


    @Test
    void testGetCitationMarkerUniquefiersThreeSameAuthor() throws IOException {
        OOBibStyle style = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);

        BibDatabase database = new BibDatabase();

        BibEntry entry1 = new BibEntry();
        entry1.setField(StandardField.AUTHOR, "Alpha Beta");
        entry1.setField(StandardField.TITLE, "Paper 1");
        entry1.setField(StandardField.YEAR, "2000");
        database.insertEntry(entry1);

        BibEntry entry2 = new BibEntry();
        entry2.setField(StandardField.AUTHOR, "Alpha Beta");
        entry2.setField(StandardField.TITLE, "Paper 2");
        entry2.setField(StandardField.YEAR, "2000");
        database.insertEntry(entry2);

        BibEntry entry3 = new BibEntry();
        entry3.setField(StandardField.AUTHOR, "Alpha Beta");
        entry3.setField(StandardField.TITLE, "Paper 3");
        entry3.setField(StandardField.YEAR, "2000");
        database.insertEntry(entry3);

        CitationMarkerEntry cm1 =
            makeCitationMarkerEntry("v1", entry1, database, "a", null, false);
        CitationMarkerEntry cm2 =
            makeCitationMarkerEntry("v2", entry2, database, "b", null, false);
        CitationMarkerEntry cm3 =
            makeCitationMarkerEntry("v3", entry3, database, "c", null, false);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
        citationMarkerEntries.add(cm1);
        citationMarkerEntries.add(cm2);
        citationMarkerEntries.add(cm3);

        assertEquals("[Beta, 2000a,b,c]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());

        assertEquals("Beta [2000a,b,c]",
                     style.getCitationMarker2(citationMarkerEntries,
                                              false,
                                              NonUniqueCitationMarker.THROWS).asString());
    }

    @Test
    // TODO: equals only work when initialized from file, not from reader
    void testEquals() throws IOException {
        OOBibStyle style1 = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        OOBibStyle style2 = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        assertEquals(style1, style2);
    }

    @Test
    // TODO: equals only work when initialized from file, not from reader
    void testNotEquals() throws IOException {
        OOBibStyle style1 = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        OOBibStyle style2 = new OOBibStyle(StyleLoader.DEFAULT_AUTHORYEAR_STYLE_PATH,
                layoutFormatterPreferences);
        assertNotEquals(style1, style2);
    }

    @Test
    void testCompareToEqual() throws IOException {
        OOBibStyle style1 = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        OOBibStyle style2 = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        assertEquals(0, style1.compareTo(style2));
    }

    @Test
    void testCompareToNotEqual() throws IOException {
        OOBibStyle style1 = new OOBibStyle(StyleLoader.DEFAULT_NUMERICAL_STYLE_PATH,
                layoutFormatterPreferences);
        OOBibStyle style2 = new OOBibStyle(StyleLoader.DEFAULT_AUTHORYEAR_STYLE_PATH,
                layoutFormatterPreferences);
        assertTrue(style1.compareTo(style2) > 0);
        assertFalse(style2.compareTo(style1) > 0);
    }

    @Test
    void testEmptyStringPropertyAndOxfordComma() throws Exception {

        OOBibStyle style = new OOBibStyle("test.jstyle", layoutFormatterPreferences);

        BibDatabase database = new BibDatabase();

        BibEntry entry = new BibEntry();
        entry.setType(StandardEntryType.Article);
        entry.setField(StandardField.AUTHOR, "Alpha von Beta and Gamma Epsilon and Ypsilon Tau");
        entry.setField(StandardField.TITLE, "JabRef Manual");
        entry.setField(StandardField.YEAR, "2016");
        database.insertEntry(entry);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>();
        citationMarkerEntries.add(
            makeCitationMarkerEntry("Beta2016", entry, database, null, null, false));

        assertEquals("von Beta, Epsilon, & Tau, 2016",
                     style.getCitationMarker2(citationMarkerEntries,
                                              true,
                                              NonUniqueCitationMarker.THROWS).asString());
    }

    @Test
    void testIsValidWithDefaultSectionAtTheStart() throws Exception {
        OOBibStyle style = new OOBibStyle("testWithDefaultAtFirstLIne.jstyle", layoutFormatterPreferences);
        assertTrue(style.isValid());
    }

}

