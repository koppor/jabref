package org.jabref.http.dto;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.types.EntryType;
import org.jabref.http.dto.typeadapters.EntryTypeAdapter;
import org.jabref.http.dto.typeadapters.FieldAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;

public class BibEntryJsonReader implements Feature, MessageBodyReader<BibEntry> {

    private Gson gson = new GsonBuilder()
            .registerTypeAdapter(EntryType.class, new EntryTypeAdapter())
            .registerTypeAdapter(Field.class, new FieldAdapter())
            .create();

    @Override
    public boolean configure(FeatureContext context) {
        context.register(BibEntryJsonReader.class);
        return true;
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type.equals(BibEntry.class);
    }

    @Override
    public BibEntry readFrom(Class<BibEntry> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        BibEntryDTO bibEntryDTO = gson.fromJson(new InputStreamReader(entityStream), BibEntryDTO.class);
        BibEntry bibEntry = new BibEntry(bibEntryDTO.type())
                .withSharedBibEntryData(bibEntryDTO.sharingMetadata())
                .withUserComments(bibEntryDTO.userComments())
                .withFields(bibEntryDTO.content());
        if (bibEntryDTO.citationKey() != null) {
            bibEntry.withCitationKey(bibEntryDTO.citationKey());
        }
        return bibEntry;
    }
}
