package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jabref.logic.JabRefException;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.CitationDatabaseLookup;
import org.jabref.model.oostyle.CitationMarkerEntry;
import org.jabref.model.oostyle.CitationMarkerEntryImpl;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.NonUniqueCitationMarker;
import org.jabref.model.oostyle.OOFormattedText;
import org.jabref.model.oostyle.OOStyleDataModelVersion;
import org.jabref.model.openoffice.CreationException;
import org.jabref.model.openoffice.NoDocumentException;

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

    public static class SyncOptions {

        public final List<BibDatabase> databases;
        boolean updateBibliography;
        boolean alwaysAddCitedOnPages;

        public SyncOptions(List<BibDatabase> databases) {
            this.databases = databases;
            this.updateBibliography = false;
            this.alwaysAddCitedOnPages = false;
        }

        public SyncOptions setUpdateBibliography(boolean value) {
            this.updateBibliography = value;
            return this;
        }

        public SyncOptions setAlwaysAddCitedOnPages(boolean value) {
            this.alwaysAddCitedOnPages = value;
            return this;
        }
    }

    /*
     * @param cursor Where to insert.
     *
     * @param sync If not empty, update citation markers and,
     *             depending on the embedded options, the
     *             bibliography.
     *
     * @param fcursor If sync.isPresent(), it must provide a
     *                FunctionalTextViewCursor. Otherwise not used.
     */
    public static void insertCitationGroup(XTextDocument doc,
                                           OOFrontend fr,
                                           XTextCursor cursor,
                                           List<BibEntry> entries,
                                           BibDatabase database,
                                           OOBibStyle style,
                                           InTextCitationType citationType,
                                           String pageInfo,
                                           Optional<SyncOptions> sync,
                                           Optional<FunctionalTextViewCursor> fcursor)
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

        final int nEntries = entries.size();
        // JabRef53 style pageInfo list
        List<Optional<OOFormattedText>> pageInfosForCitations =
            OOStyleDataModelVersion.fakePageInfosForCitations(pageInfo, nEntries);

        List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>(nEntries);
        for (int i = 0; i < nEntries; i++) {
            // Using the same database for each entry.
            // Probably the GUI limits selection to a single database.
            Optional<CitationDatabaseLookup.Result> db =
                Optional.of(new CitationDatabaseLookup.Result(entries.get(i), database));
            CitationMarkerEntry cm =
                new CitationMarkerEntryImpl(citationKeys.get(i),
                                            db,
                                            // Optional.ofNullable(entries.get(i)),
                                            // Optional.ofNullable(database),
                                            Optional.empty(), // uniqueLetter
                                            pageInfosForCitations.get(i),
                                            false /* isFirstAppearanceOfSource */);
            citationMarkerEntries.add(cm);
        }

        // The text we insert
        OOFormattedText citeText =
            (style.isNumberEntries()
             ? OOFormattedText.fromString("[-]") // A dash only. Only refresh later.
             : style.getCitationMarker(citationMarkerEntries,
                                       citationType.inParenthesis(),
                                       NonUniqueCitationMarker.FORGIVEN));

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
                                                         style,
                                                         true /* insertSpaceAfter */);

        if (sync.isPresent()) {
            // Remember this position: we will come back here in the
            // end.
            XTextRange position = cursor.getEnd();

            // To account for numbering and for uniqueLetters, we
            // must refresh the cite markers:
            OOFrontend fr2 = new OOFrontend(doc);

            Update.updateDocument(doc,
                                  fr2,
                                  sync.get().databases,
                                  style,
                                  fcursor.get(),
                                  sync.get().updateBibliography,
                                  sync.get().alwaysAddCitedOnPages);

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
