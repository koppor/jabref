package org.jabref.logic.shared.restserver.core.serialization;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.IEEEField;
import org.jabref.model.entry.field.SpecialField;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.field.UnknownField;
import org.jabref.model.entry.types.StandardEntryType;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BibEntryJacksonDeserializer extends JsonDeserializer<BibEntry> {
    private final Logger LOGGER = LoggerFactory.getLogger(BibEntryJacksonDeserializer.class);

    @Override
    public BibEntry deserialize(JsonParser in, DeserializationContext ctxt) throws IOException {
        BibEntry deserializedEntry = new BibEntry();
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(in);
            assert node.isObject();
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                switch (field) {
                    case "citekey":
                        deserializedEntry.withCitationKey(node.get(field).asText());
                        break;
                    case "entrytype":
                        deserializedEntry.setType(StandardEntryType.valueOf(node.get(field).asText()));
                        break;
                    default:
                        // We cannot apply an optional of Field here as e.g. Optional<StandardField> cannot be assigned to Optional<Field>
                        Optional<StandardField> parsedField = Arrays.stream(StandardField.values())
                                                                    .filter(e -> e.name().equalsIgnoreCase(field))
                                                                    .findAny();
                        if (parsedField.isPresent()) {
                            deserializedEntry.withField(parsedField.get(), node.get(field).asText());
                            break;
                        }

                        Optional<IEEEField> parsedIEEEField = Arrays.stream(IEEEField.values())
                                                                    .filter(e -> e.name().equalsIgnoreCase(field))
                                                                    .findAny();
                        if (parsedIEEEField.isPresent()) {
                            deserializedEntry.withField(parsedIEEEField.get(), node.get(field).asText());
                            break;
                        }
                        Optional<SpecialField> parsedSpecialField = Arrays.stream(SpecialField.values())
                                                                          .filter(e -> e.name().equalsIgnoreCase(field))
                                                                          .findAny();
                        if (parsedSpecialField.isPresent()) {
                            deserializedEntry.withField(parsedSpecialField.get(), node.get(field).asText());
                            break;
                        }
                        UnknownField unknownField = new UnknownField(field);
                        deserializedEntry.withField(unknownField, node.get(field).asText());
                }
            }
            deserializedEntry.setChanged(true);
            return deserializedEntry;
        } catch (IOException ex) {
            LOGGER.error("Deserialization failed", ex);
            throw ex;
        }
    }
}
