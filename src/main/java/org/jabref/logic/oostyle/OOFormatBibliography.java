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

public class OOFormatBibliography {
    private static final OOPreFormatter POSTFORMATTER = new OOPreFormatter();
    private static final Field UNIQUEFIER_FIELD = new UnknownField("uniq");

    private OOFormatBibliography() {
    }

    /*
     * @return just the body. No label, "Cited on pages" or paragraph.
     */
    public static OOFormattedText formatBibliographyEntryBody(CitedKey ck,
                                                              OOBibStyle style) {
        if (ck.db.isEmpty()) {
            // Unresolved entry
            return OOFormattedText.fromString(String.format("Unresolved(%s)", ck.citationKey));
        } else {
            // Resolved entry, use the layout engine
            BibEntry bibentry = ck.db.get().entry;
            Layout layout = style.getReferenceFormat(bibentry.getType());
            layout.setPostFormatter(POSTFORMATTER);

            return formatFullReferenceOfBibEbtry(layout,
                                                 bibentry,
                                                 ck.db.get().database,
                                                 ck.uniqueLetter.orElse(null));
        }
    }

    /*
     * @return a paragraph. Includes label and "Cited on pages".
     */
    public static OOFormattedText formatBibliographyEntry(CitationGroups cgs,
                                                          CitedKey ck,
                                                          OOBibStyle style,
                                                          boolean alwaysAddCitedOnPages) {
        StringBuilder sb = new StringBuilder();

        // insert marker "[1]"
        if (style.isNumberEntries()) {

            if (ck.number.isEmpty()) {
                throw new RuntimeException("formatBibliographyEntry:"
                                           + " numbered style, but found unnumbered entry");
            }

            int number = ck.number.get();
            OOFormattedText marker = style.getNumCitationMarkerForBibliography(number);
            sb.append(marker.asString());
        } else {
            // !style.isNumberEntries() : emit no prefix
            // Note: We might want [citationKey] prefix for style.isCitationKeyCiteMarkers();
        }

        // Add entry body
        OOFormattedText formattedText = formatBibliographyEntryBody(ck, style);
        sb.append(formattedText.asString());

        // Add "Cited on pages"
        if (ck.db.isEmpty()) {
            // Unresolved entry: add links to citations
            sb.append(formatCitedOnPages(cgs, ck).asString());
        } else {
            // Resolved entry
            if (alwaysAddCitedOnPages) {
                sb.append(formatCitedOnPages(cgs, ck).asString());
            }
        }

        // Add paragraph
        OOFormattedText entryText = OOFormattedText.fromString(sb.toString());
        String parStyle = style.getReferenceParagraphFormat();
        return OOFormat.paragraph(entryText, parStyle);
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

            OOFormattedText entryText = formatBibliographyEntry(cgs, ck, style, alwaysAddCitedOnPages);

            // Add full entry to bibliography.
            stringBuilder.append(entryText.asString());
        } // for CitedKey

        OOFormattedText full = OOFormattedText.fromString(stringBuilder.toString());
        return full;
    }

    public static OOFormattedText formatBibliography(CitationGroups cgs,
                                                     CitedKeys bibliography,
                                                     OOBibStyle style,
                                                     boolean alwaysAddCitedOnPages) {

        OOFormattedText title = style.getFormattedBibliographyTitle();
        OOFormattedText body = OOFormatBibliography.formatBibliography(cgs,
                                                                       bibliography,
                                                                       style,
                                                                       alwaysAddCitedOnPages);
        return OOFormattedText.fromString(title.asString() + body.asString());
    }

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

}
