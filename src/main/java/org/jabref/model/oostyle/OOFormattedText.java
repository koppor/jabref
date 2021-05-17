package org.jabref.model.oostyle;

import java.util.Objects;

public class OOFormattedText {

    private final String data;

    private OOFormattedText(String data) {
        Objects.requireNonNull(data);
        this.data = data;
    }

    public static OOFormattedText fromString(String s) {
        if (s == null) {
            return null;
        }
        return new OOFormattedText(s);
    }

    public static String toString(OOFormattedText s) {
        if (s == null) {
            return null;
        }
        return s.data;
    }

    public String asString() {
        return data;
    }

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }

        if (!(o instanceof OOFormattedText)) {
            return false;
        }

        OOFormattedText c = (OOFormattedText) o;

        return data.equals(c.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
