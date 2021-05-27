package org.jabref.logic.openoffice.ootext;

import org.jabref.model.openoffice.ootext.OOText;

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
    public static OOText setLocale(OOText s, String locale) {
        return OOText.fromString(String.format("<span lang=\"%s\">", locale)
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
    public static OOText setLocaleNone(OOText s) {
        return OOFormat.setLocale(s, "zxx");
    }

    /**
     * Mark {@code s} using a character style {@code charStyle}
     *
     * @param charStyle Name of a character style known to OO. May be
     * empty for "Standard", which in turn means do not override any properties.
     *
     */
    public static OOText setCharStyle(OOText s, String charStyle) {
        return OOText.fromString(String.format("<span oo:CharStyleName=\"%s\">", charStyle)
                                          + s.asString()
                                          + "</span>");
    }

    /**
     * Mark {@code s} as part of a paragraph with style {@code paraStyle}
     */
    public static OOText paragraph(OOText s, String paraStyle) {
        if (paraStyle == null || "".equals(paraStyle)) {
            return paragraph(s);
        }
        return OOText.fromString(String.format("<p oo:ParaStyleName=\"%s\">", paraStyle)
                                          + s.asString()
                                          + "</p>");
    }

    /**
     * Mark {@code s} as part of a paragraph.
     */
    public static OOText paragraph(OOText s) {
        return OOText.fromString("<p>"
                                          + s.asString()
                                          + "</p>");
    }

    public static OOText formatReferenceToPageNumberOfReferenceMark(String referencMarkName) {
        String s = String.format("<oo:referenceToPageNumberOfReferenceMark target=\"%s\">",
                                 referencMarkName);
        return OOText.fromString(s);
    }
 }
