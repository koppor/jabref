package org.jabref.http.server;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javafx.collections.FXCollections;

import org.jabref.logic.bibtex.FieldContentFormatterPreferences;
import org.jabref.logic.bibtex.FieldWriterPreferences;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.util.io.BackupFileUtil;
import org.jabref.preferences.BibEntryPreferences;
import org.jabref.preferences.GuiPreferences;
import org.jabref.preferences.PreferencesService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerTest {

    static PreferencesService preferencesService() {
        PreferencesService preferencesService = mock(PreferencesService.class);

        ImportFormatPreferences importFormatPreferences = mock(ImportFormatPreferences.class);
        when(preferencesService.getImportFormatPreferences()).thenReturn(importFormatPreferences);

        BibEntryPreferences bibEntryPreferences = mock(BibEntryPreferences.class);
        when(importFormatPreferences.bibEntryPreferences()).thenReturn(bibEntryPreferences);
        when(bibEntryPreferences.getKeywordSeparator()).thenReturn(',');

        FieldWriterPreferences fieldWriterPreferences = mock(FieldWriterPreferences.class);
        when(preferencesService.getFieldWriterPreferences()).thenReturn(fieldWriterPreferences);
        when(fieldWriterPreferences.isResolveStrings()).thenReturn(false);

        // defaults are in {@link org.jabref.preferences.JabRefPreferences.NON_WRAPPABLE_FIELDS}
        FieldContentFormatterPreferences fieldContentFormatterPreferences = new FieldContentFormatterPreferences(List.of());
        when(preferencesService.getFieldWriterPreferences().getFieldContentFormatterPreferences()).thenReturn(fieldContentFormatterPreferences);

        GuiPreferences guiPreferences = mock(GuiPreferences.class);
        when(preferencesService.getGuiPreferences()).thenReturn(guiPreferences);

        when(guiPreferences.getLastFilesOpened()).thenReturn(FXCollections.observableArrayList(pathOfGeneralServerTestBib().toString()));

        return preferencesService;
    }

    static Path pathOfGeneralServerTestBib() {
        return Paths.get("src/test/resources/org/jabref/http/server/general-server-test.bib").toAbsolutePath();
    }

    static String idOfGeneralServerTestBib() {
        Path path = pathOfGeneralServerTestBib();
        return path.getFileName() + "-" + BackupFileUtil.getUniqueFilePrefix(path);
    }
}
