package org.jabref.model.openoffice.ootext;

import java.util.Objects;

/**
 * Text with HTML-like markup as understood by OOTextIntoOO.write
 *
 * Some of the tags can be added using OOFormat methods. Others come from the layout engine, either
 * by interpreting LaTeX markup or from settings in the jstyle file.
 */
public class OOText {

    private final String data;

    private OOText(String data) {
        Objects.requireNonNull(data);
        this.data = data;
    }

    /* null input is passed through */
    public static OOText fromString(String string) {
        if (string == null) {
            return null;
        }
        return new OOText(string);
    }

    /* null input is passed through */
    public static String toString(OOText ootext) {
        if (ootext == null) {
            return null;
        }
        return ootext.data;
    }

    @Override
    public String toString() {
        return data;
    }

    /* Object.equals */
    @Override
    public boolean equals(Object object) {

        if (object == this) {
            return true;
        }

        if (!(object instanceof OOText)) {
            return false;
        }

        OOText c = (OOText) object;

        return data.equals(c.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}