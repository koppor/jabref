package org.jabref.logic.importer.fileformat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.stream.Stream;

class RISImporterFilesTest {

    private static final String FILE_ENDING = ".ris";

    private static Stream<String> fileNames() throws IOException {
        Predicate<String> fileName =
                name -> name.startsWith("RisImporterTest") && name.endsWith(FILE_ENDING);
        return ImporterTestEngine.getTestFiles(fileName).stream();
    }

    @ParameterizedTest
    @MethodSource("fileNames")
    void isRecognizedFormat(String fileName) throws IOException {
        ImporterTestEngine.testIsRecognizedFormat(new RisImporter(), fileName);
    }

    @ParameterizedTest
    @MethodSource("fileNames")
    void importEntries(String fileName) throws Exception {
        ImporterTestEngine.testImportEntries(new RisImporter(), fileName, FILE_ENDING);
    }
}
