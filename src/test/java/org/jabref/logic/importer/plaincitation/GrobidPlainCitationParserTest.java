package org.jabref.logic.importer.plaincitation;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.stream.Stream;

import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.ParseException;
import org.jabref.logic.importer.util.GrobidPreferences;
import org.jabref.logic.importer.util.GrobidService;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;
import org.jabref.testutils.category.FetcherTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@FetcherTest
public class GrobidPlainCitationParserTest {

    static ImportFormatPreferences importFormatPreferences = mock(ImportFormatPreferences.class, Answers.RETURNS_DEEP_STUBS);
    static GrobidPreferences grobidPreferences = new GrobidPreferences(
            true,
            false,
            "http://grobid.jabref.org:8070");
    static GrobidPlainCitationParser grobidPlainCitationParser = new GrobidPlainCitationParser(grobidPreferences, importFormatPreferences);

    static String example1 = "Derwing, T. M., Rossiter, M. J., & Munro, M. J. (2002). Teaching native speakers to listen to foreign-accented speech. Journal of Multilingual and Multicultural Development, 23(4), 245-259.";
    static BibEntry example1AsBibEntry = new BibEntry(StandardEntryType.Article).withCitationKey("-1")
                                                                                .withField(StandardField.AUTHOR, "Derwing, Tracey and Rossiter, Marian and Munro, Murray")
                                                                                .withField(StandardField.TITLE, "Teaching Native Speakers to Listen to Foreign-accented Speech")
                                                                                .withField(StandardField.JOURNAL, "Journal of Multilingual and Multicultural Development")
                                                                                .withField(StandardField.DOI, "10.1080/01434630208666468")
                                                                                .withField(StandardField.DATE, "2002-09")
                                                                                .withField(StandardField.MONTH, "9")
                                                                                .withField(StandardField.YEAR, "2002")
                                                                                .withField(StandardField.PAGES, "245-259")
                                                                                .withField(StandardField.VOLUME, "23")
                                                                                .withField(StandardField.PUBLISHER, "Informa UK Limited")
                                                                                .withField(StandardField.NUMBER, "4");

    static String example2 = "Thomas, H. K. (2004). Training strategies for improving listeners' comprehension of foreign-accented speech (Doctoral dissertation). University of Colorado, Boulder.";
    static BibEntry example2AsBibEntry = new BibEntry(BibEntry.DEFAULT_TYPE).withCitationKey("-1")
                                                                            .withField(StandardField.AUTHOR, "Thomas, H")
                                                                            .withField(StandardField.TITLE, "Training strategies for improving listeners' comprehension of foreign-accented speech (Doctoral dissertation)")
                                                                            .withField(StandardField.DATE, "2004")
                                                                            .withField(StandardField.PUBLISHER, "University of Colorado")
                                                                            .withField(StandardField.YEAR, "2004")
                                                                            .withField(StandardField.ADDRESS, "Boulder");

    static String example3 = "Turk, J., Graham, P., & Verhulst, F. (2007). Child and adolescent psychiatry : A developmental approach. Oxford, England: Oxford University Press.";
    static BibEntry example3AsBibEntry = new BibEntry(StandardEntryType.InBook).withCitationKey("-1")
                                                                            .withField(StandardField.AUTHOR, "Turk, Jeremy and Graham, Philip and Verhulst, Frank")
                                                                            .withField(StandardField.TITLE, "Developmental psychopathology")
                                                                            .withField(StandardField.BOOKTITLE, "Child and Adolescent Psychiatry")
                                                                            .withField(StandardField.PUBLISHER, "Oxford University Press")
                                                                            .withField(StandardField.DATE, "2007-02")
                                                                            .withField(StandardField.YEAR, "2007")
                                                                            .withField(StandardField.MONTH, "2")
                                                                            .withField(StandardField.PAGES, "191-264")
                                                                            .withField(StandardField.DOI, "10.1093/med/9780199216697.003.0004")
                                                                            .withField(StandardField.ADDRESS, "Oxford, England");

    static String example4 = "Carr, I., & Kidner, R. (2003). Statutes and conventions on international trade law (4th ed.). London, England: Cavendish.";
    static BibEntry example4AsBibEntry = new BibEntry(StandardEntryType.InBook).withCitationKey("-1")
                                                                               .withField(StandardField.AUTHOR, "Carr, I and Kidner, R")
                                                                               .withField(StandardField.BOOKTITLE, "Statutes and conventions on international trade law")
                                                                               .withField(StandardField.PUBLISHER, "Cavendish")
                                                                               .withField(StandardField.DATE, "2003")
                                                                               .withField(StandardField.YEAR, "2003")
                                                                               .withField(StandardField.NUMBER, "4")
                                                                               .withField(StandardField.ADDRESS, "London, England");

    public static Stream<Arguments> provideExamplesForCorrectResultTest() {
        return Stream.of(
                Arguments.of("example1", example1AsBibEntry, example1),
                Arguments.of("example2", example2AsBibEntry, example2),
                Arguments.of("example3", example3AsBibEntry, example3),
                Arguments.of("example4", example4AsBibEntry, example4)
        );
    }

    public static Stream<Arguments> provideInvalidInput() {
        return Stream.of(
                Arguments.of("😋😋😋"),
                Arguments.of("¦@#¦@#¦@#¦@#¦@#¦@#¦@°#¦@¦°¦@°")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideExamplesForCorrectResultTest")
    void grobidPerformSearchCorrectResultTest(String testName, BibEntry expectedBibEntry, String searchQuery) throws FetcherException {
        Optional<BibEntry> entry = grobidPlainCitationParser.parsePlainCitation(searchQuery);
        assertEquals(Optional.of(expectedBibEntry), entry);
    }

    @Test
    void grobidPerformSearchWithEmptyStringTest() throws FetcherException {
        Optional<BibEntry> entry = grobidPlainCitationParser.parsePlainCitation("");
        assertEquals(Optional.empty(), entry);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidInput")
    void grobidPerformSearchWithInvalidDataTest(String invalidInput) throws FetcherException {
        assertThrows(FetcherException.class, () ->
                grobidPlainCitationParser.parsePlainCitation("invalidInput"), "performSearch should throw an FetcherException.");
    }

    @Test
    void performSearchThrowsExceptionInCaseOfConnectionIssues() throws IOException, ParseException {
        GrobidService grobidServiceMock = mock(GrobidService.class);
        when(grobidServiceMock.processCitation(anyString(), any(), any())).thenThrow(new SocketTimeoutException("Timeout"));
        grobidPlainCitationParser = new GrobidPlainCitationParser(importFormatPreferences, grobidServiceMock);

        assertThrows(FetcherException.class, () ->
                grobidPlainCitationParser.parsePlainCitation("any text"), "performSearch should throw an FetcherException, when there are underlying IOException.");
    }
}
