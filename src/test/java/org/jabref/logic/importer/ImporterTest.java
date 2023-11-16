package org.jabref.logic.importer;

import org.jabref.logic.importer.fileformat.*;
import org.jabref.logic.xmp.XmpPreferences;
import org.jabref.model.util.DummyFileUpdateMonitor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ImporterTest {

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void isRecognizedFormatWithNullForBufferedReaderThrowsException(Importer format) {
        assertThrows(NullPointerException.class, () -> format.isRecognizedFormat((BufferedReader) null));
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void isRecognizedFormatWithNullForStringThrowsException(Importer format) {
        assertThrows(NullPointerException.class, () -> format.isRecognizedFormat((String) null));
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void importDatabaseWithNullForBufferedReaderThrowsException(Importer format) {
        assertThrows(NullPointerException.class, () -> format.importDatabase((BufferedReader) null));
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void importDatabaseWithNullForStringThrowsException(Importer format) {
        assertThrows(NullPointerException.class, () -> format.importDatabase((String) null));
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void getFormatterNameDoesNotReturnNull(Importer format) {
        assertNotNull(format.getName());
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void getFileTypeDoesNotReturnNull(Importer format) {
        assertNotNull(format.getFileType());
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void getIdDoesNotReturnNull(Importer format) {
        assertNotNull(format.getId());
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void getIdDoesNotContainWhitespace(Importer format) {
        Pattern whitespacePattern = Pattern.compile("\\s");
        assertFalse(whitespacePattern.matcher(format.getId()).find());
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void getIdStripsSpecialCharactersAndConvertsToLowercase(Importer format) {
        Importer importer = mock(Importer.class, Mockito.CALLS_REAL_METHODS);
        when(importer.getName()).thenReturn("*Test-Importer");
        assertEquals("testimporter", importer.getId());
    }

    @ParameterizedTest
    @MethodSource("instancesToTest")
    public void getDescriptionDoesNotReturnNull(Importer format) {
        assertNotNull(format.getDescription());
    }

    public static Stream<Importer> instancesToTest() {
        // all classes implementing {@link Importer}
        // sorted alphabetically

        ImportFormatPreferences importFormatPreferences =
                mock(ImportFormatPreferences.class, Answers.RETURNS_DEEP_STUBS);
        when(importFormatPreferences.bibEntryPreferences().getKeywordSeparator())
                .thenReturn(',');
        XmpPreferences xmpPreferences = mock(XmpPreferences.class);
        // @formatter:off
        return Stream.of(
                new BiblioscapeImporter(),
                new BibtexImporter(importFormatPreferences, new DummyFileUpdateMonitor()),
                new CopacImporter(),
                new EndnoteImporter(),
                new InspecImporter(),
                new IsiImporter(),
                new MedlineImporter(),
                new MedlinePlainImporter(),
                new ModsImporter(importFormatPreferences),
                new MsBibImporter(),
                new OvidImporter(),
                new PdfContentImporter(),
                new PdfXmpImporter(xmpPreferences),
                new RepecNepImporter(importFormatPreferences),
                new RisImporter(),
                new SilverPlatterImporter(),
                new CitaviXmlImporter());
        // @formatter:on
    }
}
