package org.jabref.logic.oostyle;

import org.jabref.model.oostyle.OOFormattedText;

public class OOFormat {

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

    public static OOFormattedText formatReferenceToPageNumberOfReferenceMark(String referencMarkName) {
        String s = String.format("<oo:referenceToPageNumberOfReferenceMark target=\"%s\">",
                                 referencMarkName);
        return OOFormattedText.fromString(s);
    }
 }
