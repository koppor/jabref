package org.jabref.logic.shared.restserver.core.serialization;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jabref.logic.TypedBibEntry;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.InternalField;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BibEntryJacksonSerializer extends StdSerializer<BibEntry> {

    private static final String JSON_TYPE = "entrytype";
    private static final String JSON_KEY = "citekey";
    private final Logger LOGGER = LoggerFactory.getLogger(BibEntryJacksonSerializer.class);

    public BibEntryJacksonSerializer(Class<BibEntry> t) {
        super(t);
    }

    @Override
    public void serialize(BibEntry entry, JsonGenerator writer, SerializerProvider provider) throws IOException {
        if (entry == null) {
            writer.writeNull();
            return;
        }
        writer.writeStartObject();
        writer.writeStringField(JSON_TYPE, new TypedBibEntry(entry, BibDatabaseMode.BIBTEX).getTypeForDisplay());
        writer.writeStringField(JSON_KEY, entry.getCitationKey().orElse(""));

        // Grab field entries and place in map
        Map<String, String> mapFieldToValue = new HashMap<>();
        // determine sorted fields -- all fields lower case
        SortedSet<String> sortedFields = new TreeSet<>();
        for (Map.Entry<Field, String> field : entry.getFieldMap().entrySet()) {
            Field fieldName = field.getKey();
            String fieldValue = field.getValue();
            // JabRef stores the key in the field KEY_FIELD, which must not be serialized
            if (!fieldName.equals(InternalField.KEY_FIELD)) {
                String lowerCaseFieldName = fieldName.getName().toLowerCase(Locale.US);
                sortedFields.add(lowerCaseFieldName);
                mapFieldToValue.put(lowerCaseFieldName, fieldValue);
            }
        }

        // Add to writer
        for (String fieldName : sortedFields) {
            writer.writeStringField(fieldName, String.valueOf(mapFieldToValue.get(fieldName)).replaceAll("\\r\\n", "\n"));
        }
        writer.writeEndObject();
    }
}
