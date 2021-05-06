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
     * @return OOFormattedText suitable for insertOOFormattedTextAtCurrentLocation2()
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

    /**
     * Mark {@code s} as using a character locale known to OO.
     *
     * @param locale language[-country[-territory]]
     *
     * https://www.openoffice.org/api/docs/common/ref/com/sun/star/lang/Locale.html
     *
     * The country part is optional.
     *
     * The territory part is not only optional, the allowed "codes are
     * vendor and browser-specific", so probably best to avoid them if possible.
     *
     */
    public static OOFormattedText setLocale(OOFormattedText s, String locale) {
        return OOFormattedText.fromString(String.format("<span lang=\"%s\">", locale)
                                          + s.asString()
                                          + "</span>");
    }

    /**
     * Mark {@code s} as using the character locale "zxx", which means
     * "no language", "no linguistic content".
     *
     * Used around citation marks, probably to turn off spellchecking.
     *
     */
    public static OOFormattedText setLocaleNone(OOFormattedText s) {
        return OOFormat.setLocale(s, "zxx");
    }

    /**
     * Mark {@code s} using a character style {@code charStyle}
     *
     * @param charStyle Name of a character style known to OO. May be
     * empty for "Standard", which in turn means do not override any properties.
     *
     */
    public static OOFormattedText setCharStyle(OOFormattedText s, String charStyle) {
        return OOFormattedText.fromString(String.format("<span oo:CharStyleName=\"%s\">", charStyle)
                                          + s.asString()
                                          + "</span>");
    }

    /**
     * Mark {@code s} as part of a paragraph with style {@code paraStyle}
     */
    public static OOFormattedText paragraph(OOFormattedText s, String paraStyle) {
        return OOFormattedText.fromString(String.format("<p oo:ParaStyleName=\"%s\">", paraStyle)
                                          + s.asString()
                                          + "</p>");
    }

    /**
     * Mark {@code s} as part of a paragraph.
     */
    public static OOFormattedText paragraph(OOFormattedText s) {
        return OOFormattedText.fromString("<p>"
                                          + s.asString()
                                          + "</p>");
    }
 }
