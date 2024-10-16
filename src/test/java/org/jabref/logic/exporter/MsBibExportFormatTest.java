package org.jabref.logic.exporter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class MsBibExportFormatTest {

    public BibDatabaseContext databaseContext;
    public MSBibExporter msBibExportFormat;

    @BeforeEach
    void setUp() throws Exception {
        databaseContext = new BibDatabaseContext();
        msBibExportFormat = new MSBibExporter();
    }

    @Test
    final void performExportWithNoEntry(@TempDir Path tempFile) throws IOException, SaveException {
        Path path = tempFile.resolve("ThisIsARandomlyNamedFile");
        Files.createFile(path);
        List<BibEntry> entries = Collections.emptyList();
        msBibExportFormat.export(databaseContext, path, entries);
        assertEquals(Collections.emptyList(), Files.readAllLines(path));
    }
}
