package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jabref.logic.JabRefException;
import org.jabref.logic.oostyle.CitationMarkerEntry;
import org.jabref.logic.oostyle.CitationMarkerEntryImpl;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.logic.oostyle.OOProcess;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.OOFormattedText;
import org.jabref.model.oostyle.OOStyleDataModelVersion;

import com.sun.star.beans.IllegalTypeException;
import com.sun.star.beans.NotRemoveableException;
import com.sun.star.beans.PropertyExistException;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditInsert {

    private static final Logger LOGGER = LoggerFactory.getLogger(EditInsert.class);

    /**
     * In insertEntry we receive BibEntry values from the GUI.
     *
     * In the document we store citations by their citation key.
     *
     * If the citation key is missing, the best we can do is to notify
     * the user. Or the programmer, that we cannot accept such input.
     *
     */
    private static String insertEntryGetCitationKey(BibEntry entry) {
        Optional<String> key = entry.getCitationKey();
        if (key.isEmpty()) {
            throw new RuntimeException("insertEntryGetCitationKey:"
                                       + " cannot cite entries without citation key");
        }
        return key.get();
    }

    /*
     * @param cursor Where to insert.
     */
    public static void insertCitationGroup(XTextDocument doc,
                                           OOFrontend fr,
                                           XTextCursor cursor,
                                           List<BibEntry> entries,
                                           BibDatabase database,
                                           List<BibDatabase> allBases,
                                           OOBibStyle style,
                                           boolean inParenthesis,
                                           boolean withText,
                                           String pageInfo,
                                           boolean sync,
                                           boolean alwaysAddCitedOnPages)
        throws
        UnknownPropertyException,
        NoDocumentException,
        NotRemoveableException,
        WrappedTargetException,
        PropertyVetoException,
        PropertyExistException,
        NoSuchElementException,
        CreationException,
        IllegalTypeException,
        JabRefException {
            List<String> citationKeys =
                entries.stream()
                .map(EditInsert::insertEntryGetCitationKey)
                .collect(Collectors.toList());

            InTextCitationType citationType = OOProcess.citationTypeFromOptions(withText, inParenthesis);

            final int nEntries = entries.size();
            // JabRef53 style pageInfo list
            List<OOFormattedText> pageInfosForCitations =
                OOStyleDataModelVersion.fakePageInfosForCitations(pageInfo, nEntries);

            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>(nEntries);
            for (int i = 0; i < nEntries; i++) {
                // Using the same database for each entry.
                // Probably the GUI limits selection to a single database.
                CitationMarkerEntry cm =
                    new CitationMarkerEntryImpl(citationKeys.get(i),
                                                Optional.ofNullable(entries.get(i)),
                                                Optional.ofNullable(database),
                                                Optional.empty(), // uniqueLetter
                                                Optional.ofNullable(pageInfosForCitations.get(i)),
                                                false /* isFirstAppearanceOfSource */);
                citationMarkerEntries.add(cm);
            }

            // The text we insert
            OOFormattedText citeText =
                (style.isNumberEntries()
                 ? OOFormattedText.fromString("[-]") // A dash only. Only refresh later.
                 : style.getCitationMarker(citationMarkerEntries,
                                           inParenthesis,
                                           OOBibStyle.NonUniqueCitationMarker.FORGIVEN));

            if ("".equals(OOFormattedText.toString(citeText))) {
                citeText = OOFormattedText.fromString("[?]");
            }

            UpdateCitationMarkers.createAndFillCitationGroup(fr,
                                                             doc,
                                                             citationKeys,
                                                             pageInfosForCitations,
                                                             citationType,
                                                             citeText,
                                                             cursor,
                                                             withText,
                                                             style,
                                                             true /* insertSpaceAfter */);

            // Remember this position: we will come back here in the
            // end.
            XTextRange position = cursor.getEnd();

            if (sync) {
                // To account for numbering and for uniqueLetters, we
                // must refresh the cite markers:
                OOFrontend fr2 = new OOFrontend(doc);

                Update.updateDocument(doc,
                                      fr2,
                                      allBases,
                                      style,
                                      true, /* doUpdateBibliography */
                                      alwaysAddCitedOnPages);

                /*
                 * Problem: insertEntry in bibliography
                 * Reference is destroyed when we want to get there.
                 */
                // Go back to the relevant position:
                try {
                    cursor.gotoRange(position, false);
                } catch (com.sun.star.uno.RuntimeException ex) {
                    LOGGER.warn("insertCitationGroup:"
                                + " Could not go back to end of in-text citation", ex);
                }
            }

    }
}
