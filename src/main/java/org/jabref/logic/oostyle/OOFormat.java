package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jabref.logic.l10n.Localization;
import org.jabref.logic.layout.Layout;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.UnknownField;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.OOFormattedText;

public class OOFormat {
    private static final OOPreFormatter POSTFORMATTER = new OOPreFormatter();
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
    private static OOFormattedText formatFullReferenceOfBibEbtry(Layout layout,
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

    private static OOFormattedText formatCitedOnPages(CitationGroups cgs, CitedKey ck) {
        if (!cgs.citationGroupsProvideReferenceMarkNameForLinking()) {
            return OOFormattedText.fromString("");
        }

        // Format links to citations.
        //
        // Requires reference marks for citation groups.
        //
        // - With Reference
        //   - we do not control the text shown
        //   - using page numbers: useful in print
        //
        StringBuilder sb = new StringBuilder();

        String prefix = String.format(" (%s: ", Localization.lang("Cited on pages"));
        String suffix = ")";
        sb.append(prefix);

        List<CitationGroup> citationGroups = new ArrayList();
        for (CitationPath p : ck.where) {
            CitationGroupID cgid = p.group;
            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
            citationGroups.add(cg);
        }

        // sort the citationGroups according to their indexInGlobalOrder
        citationGroups.sort((a, b) -> {
                return (a
                        .getIndexInGlobalOrder()
                        .orElseThrow(RuntimeException::new)
                        .compareTo(b
                                   .getIndexInGlobalOrder()
                                   .orElseThrow(RuntimeException::new))); });

        int i = 0;
        for (CitationGroup cg : citationGroups) {
            if (i > 0) {
                sb.append(", ");
            }
            String referenceMarkName = (cg.getReferenceMarkNameForLinking()
                                        .orElseThrow(RuntimeException::new));
            OOFormattedText xref =
                OOFormat.formatReferenceToPageNumberOfReferenceMark(referenceMarkName);
            sb.append(xref.asString());
            i++;
        }
        sb.append(suffix);
        return OOFormattedText.fromString(sb.toString());
    }

    /**
     * Format body of bibliography.
     */
    public static OOFormattedText formatBibliographyBody(CitationGroups cgs,
                                                         CitedKeys bibliography,
                                                         OOBibStyle style,
                                                         boolean alwaysAddCitedOnPages) {

        final boolean debugThisFun = false;

        if (debugThisFun) {
            System.out.printf("Ref IsSortByPosition %s\n", style.isSortByPosition());
            System.out.printf("Ref IsNumberEntries  %s\n", style.isNumberEntries());
        }

        String parStyle = style.getReferenceParagraphFormat();

        StringBuilder stringBuilder = new StringBuilder();

        for (CitedKey ck : bibliography.values()) {
            StringBuilder sb = new StringBuilder();

            if (debugThisFun) {
                System.out.printf("Ref cit %-20s ck.number %7s%n",
                                  String.format("'%s'", ck.citationKey),
                                  (ck.number.isEmpty()
                                   ? "(empty)"
                                   : String.format("%02d", ck.number.get())));
            }

            // insert marker "[1]"
            if (style.isNumberEntries()) {

                if (ck.number.isEmpty()) {
                    throw new RuntimeException("formatFullReference:"
                                               + " numbered style, but found unnumbered entry");
                }

                int number = ck.number.get();
                OOFormattedText marker = style.getNumCitationMarkerForBibliography(number);
                sb.append(marker.asString());
            } else {
                // !style.isNumberEntries() : emit no prefix
                // TODO: We might want [citationKey] prefix for style.isCitationKeyCiteMarkers();
            }

            if (ck.db.isEmpty()) {
                // Unresolved entry
                OOFormattedText referenceDetails =
                    OOFormattedText.fromString(String.format("Unresolved(%s)", ck.citationKey));
                sb.append(referenceDetails.asString());
                if (true) {
                    sb.append(formatCitedOnPages(cgs, ck).asString());
                }

            } else {
                // Resolved entry
                BibEntry bibentry = ck.db.get().entry;

                // insert the actual details.
                Layout layout = style.getReferenceFormat(bibentry.getType());
                layout.setPostFormatter(POSTFORMATTER);

                OOFormattedText formattedText =
                    OOFormat.formatFullReferenceOfBibEbtry(layout,
                                                           bibentry,
                                                           ck.db.get().database,
                                                           ck.uniqueLetter.orElse(null));

                // Insert the formatted text:
                sb.append(formattedText.asString());
                if (alwaysAddCitedOnPages) {
                    sb.append(formatCitedOnPages(cgs, ck).asString());
                }
            }

            // Emit a bibliography entry
            OOFormattedText entryText = OOFormattedText.fromString(sb.toString());
            entryText = OOFormat.paragraph(entryText, parStyle);
            stringBuilder.append(entryText.asString());
        } // for CitedKey

        OOFormattedText full = OOFormattedText.fromString(stringBuilder.toString());
        return full;
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

    public static OOFormattedText formatReferenceToPageNumberOfReferenceMark(String referencMarkName) {
        String s = String.format("<oo:referenceToPageNumberOfReferenceMark target=\"%s\">",
                                 referencMarkName);
        return OOFormattedText.fromString(s);
    }
 }
