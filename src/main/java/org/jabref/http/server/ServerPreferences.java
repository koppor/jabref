package org.jabref.http.server;

import java.util.List;

import org.jabref.logic.bibtex.FieldContentFormatterPreferences;
import org.jabref.logic.bibtex.FieldWriterPreferences;

public class ServerPreferences {

    public static FieldWriterPreferences fieldWriterPreferences() {
        FieldContentFormatterPreferences fieldContentFormatterPreferences = new FieldContentFormatterPreferences(List.of());
        FieldWriterPreferences fieldWriterPreferences = new FieldWriterPreferences(false, List.of(), fieldContentFormatterPreferences);
        return fieldWriterPreferences;
    }
}
