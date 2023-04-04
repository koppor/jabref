package org.jabref.http.dto;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import org.jabref.http.dto.typeadapters.EntryTypeAdapter;
import org.jabref.http.dto.typeadapters.MapFieldStringAdapter;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.types.EntryType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;

public class BibEntryJsonWriter implements Feature, MessageBodyWriter<BibEntry> {
    @Override
    public boolean configure(FeatureContext context) {
        context.register(BibEntryJsonWriter.class);
        return true;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type.equals(BibEntry.class);
    }

    @Override
    public void writeTo(BibEntry bibEntry, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(EntryType.class, new EntryTypeAdapter())
                .registerTypeAdapter(MapFieldStringAdapter.TYPE, new MapFieldStringAdapter())
                .setPrettyPrinting()
                .create();
        String json = gson.toJson(new BibEntryDTO(bibEntry));
        entityStream.write(json.getBytes());
    }
}
