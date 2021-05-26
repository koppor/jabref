package org.jabref.model.openoffice.style;

import java.util.Objects;

public class OOText {

    private final String data;

    private OOText(String data) {
        Objects.requireNonNull(data);
        this.data = data;
    }

    public static OOText fromString(String s) {
        if (s == null) {
            return null;
        }
        return new OOText(s);
    }

    public static String toString(OOText s) {
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

        if (!(o instanceof OOText)) {
            return false;
        }

        OOText c = (OOText) o;

        return data.equals(c.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
