package org.jabref.http.dto.typeadapters;

import java.io.IOException;

import org.jabref.model.entry.types.EntryType;
import org.jabref.model.entry.types.EntryTypeFactory;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class EntryTypeAdapter extends TypeAdapter<EntryType> {
    @Override
    public void write(JsonWriter out, EntryType value) throws IOException {
        out.jsonValue(value.getName());
    }

    @Override
    public EntryType read(JsonReader in) throws IOException {
        return EntryTypeFactory.parse(in.nextString());
    }
}
