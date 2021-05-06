package org.jabref.model.oostyle;

public class OOFormattedText {

    private final String data;

    private OOFormattedText(String data) {
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
}
