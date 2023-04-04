package org.jabref.http.dto.typeadapters;

import java.io.IOException;

import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldFactory;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class FieldAdapter extends TypeAdapter<Field> {
    @Override
    public void write(JsonWriter out, Field value) throws IOException {
        out.value(value.getName());
    }

    @Override
    public Field read(JsonReader in) throws IOException {
        return FieldFactory.parseField(in.nextString());
    }
}
