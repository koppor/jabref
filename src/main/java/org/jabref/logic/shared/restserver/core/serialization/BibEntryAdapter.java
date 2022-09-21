package org.jabref.logic.shared.restserver.core.serialization;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jabref.logic.TypedBibEntry;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.IEEEField;
import org.jabref.model.entry.field.InternalField;
import org.jabref.model.entry.field.SpecialField;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.field.UnknownField;
import org.jabref.model.entry.types.StandardEntryType;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Used to convert a bibentry object between POJO and JSON
 */
public class BibEntryAdapter extends TypeAdapter<BibEntry> {

    private static final String JSON_TYPE = "entrytype";
    private static final String JSON_KEY = "citekey";

    @Override
    public void write(JsonWriter writer, BibEntry entry) throws IOException {
        if (entry == null) {
            writer.nullValue();
            return;
        }
        writer.beginObject();
        writer.name(JSON_TYPE).value(new TypedBibEntry(entry, BibDatabaseMode.BIBTEX).getTypeForDisplay());
        writer.name(JSON_KEY).value(entry.getCitationKey().orElse(""));

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
            writer.name(fieldName).value(String.valueOf(mapFieldToValue.get(fieldName)).replaceAll("\\r\\n", "\n"));
        }
        writer.endObject();
    }

    @Override
    public BibEntry read(JsonReader in) throws IOException {
        // Create new entry
        BibEntry deserializedEntry = new BibEntry();
        in.beginObject();
        while (in.hasNext()) {
            String field = in.nextName();
            switch (field) {
                case "citekey":
                    deserializedEntry.withCitationKey(in.nextString());
                    break;
                case "entrytype":
                    deserializedEntry.setType(StandardEntryType.valueOf(in.nextString()));
                    break;
                default:
                    // We cannot apply an optional of Field here as e.g. Optional<StandardField> cannot be assigned to Optional<Field>
                    Optional<StandardField> parsedField = Arrays.stream(StandardField.values())
                                                                .filter(e -> e.name().equalsIgnoreCase(field))
                                                                .findAny();
                    if (parsedField.isPresent()) {
                        deserializedEntry.withField(parsedField.get(), in.nextString());
                        break;
                    }

                    Optional<IEEEField> parsedIEEEField = Arrays.stream(IEEEField.values())
                                                                .filter(e -> e.name().equalsIgnoreCase(field))
                                                                .findAny();
                    if (parsedIEEEField.isPresent()) {
                        deserializedEntry.withField(parsedIEEEField.get(), in.nextString());
                        break;
                    }
                    Optional<SpecialField> parsedSpecialField = Arrays.stream(SpecialField.values())
                                                                      .filter(e -> e.name().equalsIgnoreCase(field))
                                                                      .findAny();
                    if (parsedSpecialField.isPresent()) {
                        deserializedEntry.withField(parsedSpecialField.get(), in.nextString());
                        break;
                    }
                    UnknownField unknownField = new UnknownField(field);
                    deserializedEntry.withField(unknownField, in.nextString());
            }
        }
        deserializedEntry.setChanged(true);
        in.endObject();
        return deserializedEntry;
    }
}
