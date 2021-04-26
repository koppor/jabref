package org.jabref.logic.oostyle;

import java.util.Optional;

import org.jabref.logic.layout.Layout;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.UnknownField;

public class OOFormat {
    private static final Field UNIQUEFIER_FIELD = new UnknownField("uniq");
    /**
     * Format the reference part of a bibliography entry using a Layout.
     *
     * @param layout     The Layout to format the reference with.
     * @param entry      The entry to insert.
     * @param database   The database the entry belongs to.
     * @param uniquefier Uniqiefier letter, if any, to append to the entry's year.
     *
     * @return OOFormattedText suitable for insertOOFormattedTextAtCurrentLocation()
     *
     * TODO: this is not OO-specific, should be in oostyle
     */
    public static OOFormattedText formatFullReference(Layout layout,
                                                      BibEntry entry,
                                                      BibDatabase database,
                                                      String uniquefier) {

        // Backup the value of the uniq field, just in case the entry already has it:
        Optional<String> oldUniqVal = entry.getField(UNIQUEFIER_FIELD);

        // Set the uniq field with the supplied uniquefier:
        if (uniquefier == null) {
            entry.clearField(UNIQUEFIER_FIELD);
        } else {
            entry.setField(UNIQUEFIER_FIELD, uniquefier);
        }

        // Do the layout for this entry:
        OOFormattedText formattedText = OOFormattedText.fromString(layout.doLayout(entry, database));

        // Afterwards, reset the old value:
        if (oldUniqVal.isPresent()) {
            entry.setField(UNIQUEFIER_FIELD, oldUniqVal.get());
        } else {
            entry.clearField(UNIQUEFIER_FIELD);
        }

        return formattedText;
    }

    public static OOFormattedText setLocale(OOFormattedText s, String locale) {
        return OOFormattedText.fromString(String.format("<locale value=\"%s\">", locale)
                                          + s.asString()
                                          + "</locale>");
    }

    public static OOFormattedText setLocaleNone(OOFormattedText s) {
        return OOFormat.setLocale(s, "zxx");
    }

    public static OOFormattedText setCharStyle(OOFormattedText s, String charStyle) {
        return OOFormattedText.fromString(String.format("<font class=\"%s\">", charStyle)
                                          + s.asString()
                                          + "</font>");
    }

    public static OOFormattedText paragraph(OOFormattedText s, String paraStyle) {
        return OOFormattedText.fromString(String.format("<p class=\"%s\">", paraStyle)
                                          + s.asString()
                                          + "</p>");
    }

    public static OOFormattedText paragraph(OOFormattedText s) {
        return OOFormattedText.fromString("<p>"
                                          + s.asString()
                                          + "</p>");
    }
 }
