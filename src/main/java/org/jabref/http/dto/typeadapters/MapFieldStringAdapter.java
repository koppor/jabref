package org.jabref.http.dto.typeadapters;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.FieldFactory;

import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class MapFieldStringAdapter extends TypeAdapter<Map<Field, String>> {

    public static final Type TYPE = new TypeToken<Map<Field, String>>() {
    }.getType();

    @Override
    public void write(JsonWriter out, Map<Field, String> map) throws IOException {
        out.beginObject();
        for (Map.Entry<Field, String> entry : map.entrySet()) {
            out.name(entry.getKey().getName());
            out.value(entry.getValue());
        }
        out.endObject();
    }

    @Override
    public Map<Field, String> read(JsonReader in) throws IOException {
        Map<Field, String> resultMap = new HashMap<>();
        in.beginObject();
        while (in.hasNext()) {
            Field field = FieldFactory.parseField(in.nextName());
            String value = in.nextString();
            resultMap.put(field, value);
        }
        in.endObject();
        return resultMap;
    }
}
