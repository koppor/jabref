package org.jabref.gui.openoffice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.gui.DialogService;
import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.oostyle.Citation;
import org.jabref.logic.oostyle.CitationGroup;
import org.jabref.logic.oostyle.CitationGroups;
import org.jabref.logic.oostyle.CitationMarkerEntry;
import org.jabref.logic.oostyle.CitationMarkerEntryImpl;
import org.jabref.logic.oostyle.CitedKey;
import org.jabref.logic.oostyle.CitedKeys;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.logic.oostyle.OOFormat;
import org.jabref.logic.oostyle.OOFormatBibliography;
import org.jabref.logic.oostyle.OOProcess;
import org.jabref.logic.openoffice.CreationException;
import org.jabref.logic.openoffice.DocumentConnection;
import org.jabref.logic.openoffice.NoDocumentException;
import org.jabref.logic.openoffice.OOFormattedTextIntoOO;
import org.jabref.logic.openoffice.OOFrontend;
import org.jabref.logic.openoffice.UndefinedCharacterFormatException;
import org.jabref.logic.openoffice.UndefinedParagraphFormatException;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.OOFormattedText;
import org.jabref.model.oostyle.OOStyleDataModelVersion;
import org.jabref.model.openoffice.CitationEntry;

import com.sun.star.beans.IllegalTypeException;
import com.sun.star.beans.NotRemoveableException;
import com.sun.star.beans.PropertyExistException;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.comp.helper.BootstrapException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XNameAccess;
import com.sun.star.lang.DisposedException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextSection;
import com.sun.star.uno.Any;
import com.sun.star.util.InvalidStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for manipulating the Bibliography of the currently started
 * document in OpenOffice.
 */
@AllowedToUseAwt("Requires AWT for italics and bold")
class OOBibBase {

    private static final String BIB_SECTION_NAME = "JR_bib";
    private static final String BIB_SECTION_END_NAME = "JR_bib_end";


    private static final Logger LOGGER = LoggerFactory.getLogger(OOBibBase.class);

    /* variables  */
    private final DialogService dialogService;
    private final boolean alwaysAddCitedOnPages;

    /**
     * Created when connected to a document.
     *
     * Cleared (to null) when we discover we lost the connection.
     */
    // private DocumentConnection xDocumentConnection;

    private OOBibBaseConnect connection;

    /*
     * Constructor
     */
    public OOBibBase(Path loPath,
                     DialogService dialogService)
        throws
        BootstrapException,
        CreationException {

        this.dialogService = dialogService;
        this.connection = new OOBibBaseConnect(loPath, dialogService);
        this.alwaysAddCitedOnPages = true;
    }

    public void selectDocument()
        throws
        NoDocumentException,
        NoSuchElementException,
        WrappedTargetException {
        this.connection.selectDocument();
    }

    /**
     * A simple test for document availability.
     *
     * See also `documentConnectionMissing` for a test
     * actually attempting to use teh connection.
     *
     */
    public boolean isConnectedToDocument() {
        return this.connection.isConnectedToDocument();
    }

    /**
     * @return true if we are connected to a document
     */
    public boolean documentConnectionMissing() {
        return this.connection.documentConnectionMissing();
    }

    /**
     * Either return a valid DocumentConnection or throw
     * NoDocumentException.
     */
    public DocumentConnection getDocumentConnectionOrThrow()
        throws
        NoDocumentException {
        return this.connection.getDocumentConnectionOrThrow();
    }

    /**
     *  The title of the current document, or Optional.empty()
     */
    public Optional<String> getCurrentDocumentTitle() {
        return this.connection.getCurrentDocumentTitle();
    }

    /* ****************************
     *
     *           Misc
     *
     * ****************************/

    public List<CitationEntry> getCitationEntries()
        throws
        UnknownPropertyException,
        WrappedTargetException,
        NoDocumentException,
        CreationException,
        PropertyVetoException,
        JabRefException {

        DocumentConnection documentConnection = this.getDocumentConnectionOrThrow();

        checkIfOpenOfficeIsRecordingChanges(documentConnection);

        OOFrontend fr = new OOFrontend(documentConnection);
        return fr.getCitationEntries(documentConnection);
    }

    /**
     * Apply editable parts of citationEntries to the document: store
     * pageInfo.
     *
     * Does not change presentation.
     *
     * Note: we use no undo context here, because only
     *       documentConnection.setCustomProperty() is called,
     *       and Undo in LO will not undo that.
     *
     * GUI: "Manage citations" dialog "OK" button.
     * Called from: ManageCitationsDialogViewModel.storeSettings
     *
     * <p>
     * Currently the only editable part is pageInfo.
     * <p>
     * Since the only call to applyCitationEntries() only changes
     * pageInfo w.r.t those returned by getCitationEntries(), we can
     * do with the following restrictions:
     * <ul>
     * <li> Missing pageInfo means no action.</li>
     * <li> Missing CitationEntry means no action (no attempt to remove
     *      citation from the text).</li>
     * </ul>
     */
    public void applyCitationEntries(List<CitationEntry> citationEntries)
        throws
        UnknownPropertyException,
        NotRemoveableException,
        PropertyExistException,
        IllegalTypeException,
        IllegalArgumentException,
        NoDocumentException,
        WrappedTargetException {

        DocumentConnection documentConnection = getDocumentConnectionOrThrow();
        OOFrontend fr = new OOFrontend(documentConnection);
        fr.applyCitationEntries(documentConnection, citationEntries);
    }

    private static void fillCitationMarkInCursor(DocumentConnection documentConnection,
                                                 XTextCursor cursor,
                                                 OOFormattedText citationText,
                                                 boolean withText,
                                                 OOBibStyle style)
        throws
        UnknownPropertyException,
        WrappedTargetException,
        PropertyVetoException,
        IllegalArgumentException,
        UndefinedCharacterFormatException,
        NoSuchElementException,
        CreationException {

        Objects.requireNonNull(cursor);
        Objects.requireNonNull(citationText);
        Objects.requireNonNull(style);

        if (withText) {
            OOFormattedText citationText2 = OOFormat.setLocaleNone(citationText);
            if (style.isFormatCitations()) {
                String charStyle = style.getCitationCharacterFormat();
                citationText2 = OOFormat.setCharStyle(citationText2, charStyle);
            }
            // inject a ZERO_WIDTH_SPACE to hold the initial character format
            final String ZERO_WIDTH_SPACE = "\u200b";
            citationText2 = OOFormattedText.fromString(ZERO_WIDTH_SPACE + citationText2.asString());
            OOFormattedTextIntoOO.write(documentConnection, cursor, citationText2);
        } else {
            cursor.setString("");
        }
    }

    /**
     *  Inserts a citation group in the document: creates and fills it.
     *
     * @param fr
     * @param documentConnection Connection to a document.
     * @param citationKeys BibTeX keys of
     * @param pageInfosForCitations
     * @param itcType
     *
     * @param citationText Text for the citation. A citation mark or
     *             placeholder if not yet available.
     *
     * @param position Location to insert at.
     * @param withText If false, citationText is not shown.
     * @param style
     * @param insertSpaceAfter A space inserted after the reference
     *             mark makes it easier to separate from the text
     *             coming after. But is not wanted when we recreate a
     *             reference mark.
     */
    private void createAndFillCitationGroup(OOFrontend fr,
                                            DocumentConnection documentConnection,
                                            List<String> citationKeys,
                                            List<OOFormattedText> pageInfosForCitations,
                                            InTextCitationType itcType,
                                            OOFormattedText citationText,
                                            XTextCursor position,
                                            boolean withText,
                                            OOBibStyle style,
                                            boolean insertSpaceAfter)
        throws
        UnknownPropertyException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        WrappedTargetException,
        PropertyVetoException,
        IllegalArgumentException,
        UndefinedCharacterFormatException,
        CreationException,
        NoDocumentException,
        IllegalTypeException,
        NoSuchElementException {

        CitationGroupID cgid = fr.createCitationGroup(documentConnection,
                                                      citationKeys,
                                                      pageInfosForCitations,
                                                      itcType,
                                                      position,
                                                      insertSpaceAfter,
                                                      !withText /* withoutBrackets */);

        if (withText) {
            XTextCursor c2 = fr.getFillCursorForCitationGroup(documentConnection,
                                                              cgid);

            fillCitationMarkInCursor(documentConnection,
                                     c2,
                                     citationText,
                                     withText,
                                     style);

            fr.cleanFillCursorForCitationGroup(documentConnection, cgid);
        }
        position.collapseToEnd();
    }

    /**
     * Test if we have a problem applying character style prescribe by
     * the style.
     *
     * If the style prescribes an character style, we insert a
     * character, format it and delete it.
     *
     * An UndefinedCharacterFormatException may be raised, indicating
     * that the style requested is not available in the document.
     *
     * @param cursor Provides location where we insert, format and
     * remove a character.
     */
    void assertCitationCharacterFormatIsOK(XTextCursor cursor,
                                           OOBibStyle style)
        throws UndefinedCharacterFormatException {
        if (!style.isFormatCitations()) {
            return;
        }

        /* We do not want to change the cursor passed in, so using a copy. */
        XTextCursor c2 = cursor.getText().createTextCursorByRange(cursor.getEnd());

        /*
         * Inserting, formatting and removing a single character
         * still leaves a style change in place.
         * Let us try with two characters, formatting only the first.
         */
        c2
         .getText()
         .insertString(c2, "@*", false);

        String charStyle = style.getCitationCharacterFormat();
        try {
            c2.goLeft((short) 1, false); // step over '*'
            c2.goLeft((short) 1, true);  // select '@'
            // The next line may throw
            // UndefinedCharacterFormatException(charStyle).
            // We let that propagate.
            DocumentConnection.setCharStyle(c2, charStyle);
        } finally {
            // Before leaving this scope, always delete the character we
            // inserted:
            c2.collapseToStart();
            c2.goRight((short) 2, true);  // select '@*'
            c2.setString("");
        }
    }

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

    /**
     *
     * Creates a citation group from {@code entries} at the cursor,
     * and (if sync is true) refreshes the citation markers and the
     * bibliography.
     *
     *
     * Called from: OpenOfficePanel.pushEntries, a GUI action for
     * "Cite", "Cite in-text", "Cite special" and "Insert empty
     * citation".
     *
     * Uses LO undo context "Insert citation".
     *
     * Note: Undo does not remove custom properties. Presumably
     * neither does it reestablish them.
     *
     * @param entries       The entries to cite.
     *
     * @param database      The database the entries belong to (all of them).
     *                      Used when creating the citation mark.
     *
     * @param allBases      Used if sync is true. The list of all databases
     *                      we may need to refresh the document.
     *
     * @param style         The bibliography style we are using.
     *
     * @param inParenthesis Indicates whether it is an in-text
     *                      citation or a citation in parenthesis.
     *                      This is not relevant if
     *                      numbered citations are used.
     * @param withText      Indicates whether this should be a visible
     *                      citation (true) or an empty (invisible) citation (false).
     *
     * @param pageInfo      A single page-info for these entries. Stored in custom property
     *                      with the same name as the reference mark.
     *
     *                      This is a GUI call, and we are not ready
     *                      to get multiple pageInfo values there.
     *
     *                      In case of multiple entries, pageInfo goes
     *                      to the last citation (as apparently did in JabRef52).
     *
     *                      Related https://latex.org/forum/viewtopic.php?t=14331
     *                      """
     *                      Q: What I would like is something like this:
     *                      (Jones, 2010, p. 12; Smith, 2003, pp. 21 - 23)
     *                      A: Not in a single \citep, no.
     *                         Use \citetext{\citealp[p.~12]{jones2010};
     *                                       \citealp[pp.~21--23]{smith2003}}
     *                      """
     *
     * @param sync          Indicates whether the reference list and in-text citations
     *                      should be refreshed in the document.
     *
     *
     */
    public void insertEntry(List<BibEntry> entries,
                            BibDatabase database,
                            List<BibDatabase> allBases,
                            OOBibStyle style,
                            boolean inParenthesis,
                            boolean withText,
                            String pageInfo,
                            boolean sync)
        throws
        JabRefException,
        IllegalArgumentException,
        UnknownPropertyException,
        NotRemoveableException,
        PropertyExistException,
        IllegalTypeException,
        UndefinedCharacterFormatException,
        WrappedTargetException,
        NoSuchElementException,
        PropertyVetoException,
        IOException,
        CreationException,
        UndefinedParagraphFormatException,
        NoDocumentException,
        InvalidStateException {

        styleIsRequired(style);

        if (entries == null || entries.size() == 0) {
            String title = "No bibliography entries selected";
            String msg = (Localization.lang("No bibliography entries are selected for citation.")
                          + "\n"
                          + Localization.lang("Select some before citing."));
            throw new JabRefException(title, msg);
        }
        final int nEntries = entries.size();

        DocumentConnection documentConnection = getDocumentConnectionOrThrow();

        checkStylesExistInTheDocument(style, documentConnection);
        checkIfOpenOfficeIsRecordingChanges(documentConnection);

        OOFrontend fr = new OOFrontend(documentConnection);
        // CitationGroups cgs = new CitationGroups(documentConnection);
        // TODO: imposeLocalOrder

        boolean useUndoContext = true;

        try {
            if (useUndoContext) {
                documentConnection.enterUndoContext("Insert citation");
            }
            XTextCursor cursor;
            // Get the cursor positioned by the user.
            try {
                cursor = documentConnection.getViewCursor();
            } catch (RuntimeException ex) {
                // com.sun.star.uno.RuntimeException
                throw new JabRefException("Could not get the cursor",
                                          Localization.lang("Could not get the cursor."));
            }

            // Check for crippled XTextViewCursor
            Objects.requireNonNull(cursor);
            try {
                cursor.getStart();
            } catch (com.sun.star.uno.RuntimeException ex) {
                String msg =
                    Localization.lang("Please move the cursor"
                                      + " to the location for the new citation.")
                    + "\n"
                    + Localization.lang("I cannot insert to the cursors current location.");
                throw new JabRefException(msg, ex);
            }

            List<String> citationKeys =
                entries.stream()
                .map(OOBibBase::insertEntryGetCitationKey)
                .collect(Collectors.toList());

            InTextCitationType itcType = OOProcess.citationTypeFromOptions(withText, inParenthesis);

            assertCitationCharacterFormatIsOK(cursor, style);

            // JabRef53 style pageInfo list
            List<OOFormattedText> pageInfosForCitations =
                OOStyleDataModelVersion.fakePageInfosForCitations(pageInfo, nEntries);

            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>(entries.size());
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

            createAndFillCitationGroup(fr,
                                       documentConnection,
                                       citationKeys,
                                       pageInfosForCitations,
                                       itcType,
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
                OOFrontend fr2 = new OOFrontend(documentConnection);
                fr2.imposeGlobalOrder(documentConnection);
                OOProcess.ProduceCitationMarkersResult x =
                    OOProcess.produceCitationMarkers(fr2.cgs, allBases, style);
                try {
                    documentConnection.lockControllers();
                    applyNewCitationMarkers(documentConnection,
                                            fr2,
                                            x.citMarkers,
                                            style);
                    // Insert it at the current position:
                    rebuildBibTextSection(documentConnection,
                                          style,
                                          fr2,
                                          x.getBibliography(),
                                          this.alwaysAddCitedOnPages);
                } finally {
                    documentConnection.unlockControllers();
                }

                /*
                 * Problem: insertEntry in bibliography
                 * Reference is destroyed when we want to get there.
                 */
                // Go back to the relevant position:
                try {
                    cursor.gotoRange(position, false);
                } catch (com.sun.star.uno.RuntimeException ex) {
                    LOGGER.warn("OOBibBase.insertEntry:"
                                + " Could not go back to end of in-text citation", ex);
                }
            }
        } catch (DisposedException ex) {
            // We need to catch this one here because the OpenOfficePanel class is
            // loaded before connection, and therefore cannot directly reference
            // or catch a DisposedException (which is in a OO JAR file).
            throw new ConnectionLostException(ex.getMessage());
        } finally {
            if (useUndoContext) {
                documentConnection.leaveUndoContext();
            }
        }
    }

    /* **************************************************
     *
     *  modifies both storage and presentation, but should only affect presentation
     *
     * **************************************************/

    /**
     * Visit each reference mark in referenceMarkNames, overwrite its
     * text content.
     *
     * After each fillCitationMarkInCursor call check if we lost the
     * OOBibBase.BIB_SECTION_NAME bookmark and recreate it if we did.
     *
     * @param documentConnection
     * @param fr
     *
     * @param citMarkers Corresponding text for each reference mark,
     *                   that replaces the old text.
     *
     * @param style Bibliography style to use.
     *
     */
    private void applyNewCitationMarkers(DocumentConnection documentConnection,
                                         OOFrontend fr,
                                         Map<CitationGroupID, OOFormattedText> citMarkers,
                                         OOBibStyle style)
        throws
        NoDocumentException,
        UndefinedCharacterFormatException,
        UnknownPropertyException,
        CreationException,
        WrappedTargetException,
        PropertyVetoException,
        NoSuchElementException {

        CitationGroups cgs = fr.cgs;
        final boolean hadBibSection = (documentConnection
                                       .getBookmarkRange(OOBibBase.BIB_SECTION_NAME)
                                       .isPresent());

        // If we are supposed to set character format for citations,
        // must run a test before we delete old citation
        // markers. Otherwise, if the specified character format
        // doesn't exist, we end up deleting the markers before the
        // process crashes due to a the missing format, with
        // catastrophic consequences for the user.
        boolean mustTestCharFormat = style.isFormatCitations();

        for (Map.Entry<CitationGroupID, OOFormattedText> kv : citMarkers.entrySet()) {

            CitationGroupID cgid = kv.getKey();
            Objects.requireNonNull(cgid);

            OOFormattedText citationText = kv.getValue();
            Objects.requireNonNull(citationText);

            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);

            boolean withText = (cg.itcType != InTextCitationType.INVISIBLE_CIT);

            if (withText) {

                XTextCursor cursor = fr.getFillCursorForCitationGroup(documentConnection,
                                                                      cgid);

                if (mustTestCharFormat) {
                    assertCitationCharacterFormatIsOK(cursor, style);
                    mustTestCharFormat = false;
                }

                fillCitationMarkInCursor(documentConnection,
                                         cursor,
                                         citationText,
                                         withText,
                                         style);

                fr.cleanFillCursorForCitationGroup(documentConnection, cgid);
            }

            if (hadBibSection
                && (documentConnection
                    .getBookmarkRange(OOBibBase.BIB_SECTION_NAME)
                    .isEmpty())) {
                // Overwriting text already there is too harsh.
                // I am making it an error, to see if we ever get here.
                throw new RuntimeException("OOBibBase.applyNewCitationMarkers:"
                                           + " just overwrote the bibliography section marker. Sorry.");
            }
        }
    }

    /* **************************************************
     *
     *     Bibliography: needs uniqueLetters or numbers
     *
     * **************************************************/

    /**
     * Rebuilds the bibliography.
     *
     *  Note: assumes fresh `jabRefReferenceMarkNamesSortedByPosition`
     *  if `style.isSortByPosition()`
     */
    private void rebuildBibTextSection(DocumentConnection documentConnection,
                                       OOBibStyle style,
                                       OOFrontend fr,
                                       CitedKeys bibliography,
                                       boolean alwaysAddCitedOnPages)
        throws
        NoSuchElementException,
        WrappedTargetException,
        IllegalArgumentException,
        CreationException,
        PropertyVetoException,
        UnknownPropertyException,
        UndefinedParagraphFormatException {

        clearBibTextSectionContent2(documentConnection);

        populateBibTextSection(documentConnection,
                               fr,
                               bibliography,
                               style,
                               alwaysAddCitedOnPages);
    }

    /**
     * Insert a paragraph break and create a text section for the bibliography.
     *
     * Only called from `clearBibTextSectionContent2`
     */
    private void createBibTextSection2(DocumentConnection documentConnection)
        throws
        IllegalArgumentException,
        CreationException {

        // Always creating at the end of documentConnection.getText()
        // Alternatively, we could receive a cursor.
        XTextCursor textCursor = documentConnection.getText().createTextCursor();
        textCursor.gotoEnd(false);

        // OOUtil.insertParagraphBreak(documentConnection.xText, textCursor);
        textCursor.collapseToEnd();

        documentConnection.insertTextSection(OOBibBase.BIB_SECTION_NAME,
                                             textCursor,
                                             false);
    }

    /**
     *  Find and clear the text section OOBibBase.BIB_SECTION_NAME to "",
     *  or create it.
     *
     * Only called from: `rebuildBibTextSection`
     *
     */
    private void clearBibTextSectionContent2(DocumentConnection documentConnection)
        throws
        WrappedTargetException,
        IllegalArgumentException,
        CreationException {

        XNameAccess nameAccess = documentConnection.getTextSections();
        if (!nameAccess.hasByName(OOBibBase.BIB_SECTION_NAME)) {
            createBibTextSection2(documentConnection);
            return;
        }

        try {
            Any a = ((Any) nameAccess.getByName(OOBibBase.BIB_SECTION_NAME));
            XTextSection section = (XTextSection) a.getObject();
            // Clear it:

            XTextCursor cursor = (documentConnection.getText()
                                  .createTextCursorByRange(section.getAnchor()));

            cursor.gotoRange(section.getAnchor(), false);
            cursor.setString("");
        } catch (NoSuchElementException ex) {
            // NoSuchElementException: is thrown by child access
            // methods of collections, if the addressed child does
            // not exist.

            // We got this exception from nameAccess.getByName() despite
            // the nameAccess.hasByName() check just above.

            // Try to create.
            LOGGER.warn("Could not get section '" + OOBibBase.BIB_SECTION_NAME + "'", ex);
            createBibTextSection2(documentConnection);
        }
    }

    /**
     * Only called from: `rebuildBibTextSection`
     *
     * Assumes the section named `OOBibBase.BIB_SECTION_NAME` exists.
     */
    private void populateBibTextSection(DocumentConnection documentConnection,
                                        OOFrontend fr,
                                        CitedKeys bibliography,
                                        OOBibStyle style,
                                        boolean alwaysAddCitedOnPages)
        throws
        NoSuchElementException,
        WrappedTargetException,
        PropertyVetoException,
        UnknownPropertyException,
        UndefinedParagraphFormatException,
        IllegalArgumentException,
        CreationException {

        XTextSection section = (documentConnection
                                .getTextSectionByName(OOBibBase.BIB_SECTION_NAME)
                                .orElseThrow(RuntimeException::new));

        XTextCursor cursor = (documentConnection.getText()
                              .createTextCursorByRange(section.getAnchor()));

        // emit the title of the bibliography
        OOFormattedTextIntoOO.removeDirectFormatting(cursor);
        OOFormattedText bibliographyText = OOFormatBibliography.formatBibliography(fr.cgs,
                                                                                   bibliography,
                                                                                   style,
                                                                                   alwaysAddCitedOnPages);
        OOFormattedTextIntoOO.write(documentConnection, cursor, bibliographyText);
        cursor.collapseToEnd();

        // remove the inital empty paragraph from the section.
        XTextCursor initialParagraph = (documentConnection.getText()
                                        .createTextCursorByRange(section.getAnchor()));
        initialParagraph.collapseToStart();
        initialParagraph.goRight((short) 1, true);
        initialParagraph.setString("");

        documentConnection.insertBookmark(OOBibBase.BIB_SECTION_END_NAME,
                                          cursor,
                                          true);
        cursor.collapseToEnd();
    }

    /* *************************
     *
     *   GUI level
     *
     * *************************/

    /*
     * GUI: Manage citations
     */

    /**
     * GUI action "Merge citations"
     *
     */
    public void combineCiteMarkers(List<BibDatabase> databases,
                                   OOBibStyle style)
        throws
        IOException,
        WrappedTargetException,
        NoSuchElementException,
        NotRemoveableException,
        IllegalArgumentException,
        UndefinedCharacterFormatException,
        UnknownPropertyException,
        PropertyVetoException,
        PropertyExistException,
        IllegalTypeException,
        CreationException,
        NoDocumentException,
        JabRefException,
        InvalidStateException {

        styleIsRequired(style);

        Objects.requireNonNull(databases);
        Objects.requireNonNull(style);

        final boolean useLockControllers = true;
        DocumentConnection documentConnection = this.getDocumentConnectionOrThrow();
        checkStylesExistInTheDocument(style, documentConnection);
        checkIfOpenOfficeIsRecordingChanges(documentConnection);

        OOFrontend fr = new OOFrontend(documentConnection);

        try {
            documentConnection.enterUndoContext("Merge citations");

            boolean madeModifications = false;

            List<CitationGroupID> referenceMarkNames =
                fr.getCitationGroupIDsSortedWithinPartitions(documentConnection,
                                                             false /* mapFootnotesToFootnoteMarks */);

            final int nRefMarks = referenceMarkNames.size();

            try {

                if (useLockControllers) {
                    documentConnection.lockControllers();
                }

                /*
                 * joinableGroups collects lists of CitationGroup values
                 * that we think are joinable.
                 *
                 * joinableGroupsCursors provides the range for each group
                 */
                List<List<CitationGroup>> joinableGroups = new ArrayList<>();
                List<XTextCursor> joinableGroupsCursors = new ArrayList<>();

                // Since we only join groups with identical itcTypes, we
                // can get itcType from the first element of each
                // joinableGroup.
                //
                // List<InTextCitationType> itcTypes = new ArrayList<>();

                if (referenceMarkNames.size() > 0) {
                    // current group of CitationGroup values
                    List<CitationGroup> currentGroup = new ArrayList<>();
                    XTextCursor currentGroupCursor = null;
                    XTextCursor cursorBetween = null;
                    CitationGroup prev = null;
                    XTextRange prevRange = null;

                    for (CitationGroupID cgid : referenceMarkNames) {
                        CitationGroup cg = fr.cgs.getCitationGroupOrThrow(cgid);

                        XTextRange currentRange = (fr
                                                   .getMarkRange(documentConnection, cgid)
                                                   .orElseThrow(RuntimeException::new));

                        boolean addToGroup = true;
                        /*
                         * Decide if we add cg to the group
                         */

                        // Only combine (Author 2000) type citations
                        if (cg.itcType != InTextCitationType.AUTHORYEAR_PAR
                            // allow "Author (2000)"
                            // && itcTypes[i] != InTextCitationType.AUTHORYEAR_INTEXT
                            ) {
                            addToGroup = false;
                        }

                        // Even if we combine AUTHORYEAR_INTEXT citations, we
                        // would not mix them with AUTHORYEAR_PAR
                        if (addToGroup && (prev != null)) {
                            if (cg.itcType != prev.itcType) {
                            addToGroup = false;
                            }
                        }

                        if (addToGroup && prev != null) {
                            Objects.requireNonNull(prevRange);
                            Objects.requireNonNull(currentRange);
                            if (!DocumentConnection.comparableRanges(prevRange, currentRange)) {
                                addToGroup = false;
                            } else {

                                int textOrder = DocumentConnection.javaCompareRegionStarts(prevRange,
                                                                                           currentRange);

                                if (textOrder != (-1)) {
                                    String msg =
                                        String.format("combineCiteMarkers:"
                                                      + " \"%s\" supposed to be followed by \"%s\","
                                                      + " but %s",
                                                      prevRange.getString(),
                                                      currentRange.getString(),
                                                      ((textOrder == 0)
                                                       ? "they start at the same position"
                                                       : ("the start of the latter precedes"
                                                          + " the start of the first")));
                                    LOGGER.warn(msg);
                                    addToGroup = false;
                                }
                            }
                        }

                        if (addToGroup && (cursorBetween != null)) {
                            Objects.requireNonNull(currentGroupCursor);
                            // assume: currentGroupCursor.getEnd() == cursorBetween.getEnd()
                            if (DocumentConnection
                                .javaCompareRegionEnds(cursorBetween, currentGroupCursor) != 0) {
                                String msg = ("combineCiteMarkers:"
                                              + " cursorBetween.end != currentGroupCursor.end");
                                throw new RuntimeException(msg);
                            }

                            XTextRange rangeStart = currentRange.getStart();

                            boolean couldExpand = true;

                            XTextCursor thisCharCursor =
                                (currentRange.getText()
                                 .createTextCursorByRange(cursorBetween.getEnd()));

                            while (couldExpand &&
                                   (DocumentConnection
                                    .javaCompareRegionEnds(cursorBetween, rangeStart) < 0)) {
                                couldExpand = cursorBetween.goRight((short) 1, true);
                                currentGroupCursor.goRight((short) 1, true);
                                //
                                thisCharCursor.goRight((short) 1, true);
                                String thisChar = thisCharCursor.getString();
                                thisCharCursor.collapseToEnd();
                                if (thisChar.isEmpty()
                                    || thisChar.equals("\n")
                                    || !thisChar.trim().isEmpty()) {
                                    couldExpand = false;
                                }
                                if (DocumentConnection
                                    .javaCompareRegionEnds(cursorBetween, currentGroupCursor) != 0) {
                                    String msg = ("combineCiteMarkers:"
                                                  + " cursorBetween.end != currentGroupCursor.end"
                                                  + " (during expand)");
                                    throw new RuntimeException(msg);
                                }
                            } // while

                            if (!couldExpand) {
                                addToGroup = false;
                            }
                        }

                        /*
                         * Even if we do not add it to an existing group,
                         * we might use it to start a new group.
                         *
                         * Can it start a new group?
                         */
                        boolean canStartGroup = (cg.itcType == InTextCitationType.AUTHORYEAR_PAR);

                        if (!addToGroup) {
                            // close currentGroup
                            if (currentGroup.size() > 1) {
                                joinableGroups.add(currentGroup);
                                joinableGroupsCursors.add(currentGroupCursor);
                            }
                            // Start a new, empty group
                            currentGroup = new ArrayList<>();
                            currentGroupCursor = null;
                            cursorBetween = null;
                            prev = null;
                            prevRange = null;
                        }

                        if (addToGroup || canStartGroup) {
                            // Add the current entry to a group.
                            currentGroup.add(cg);
                            // ... and start new cursorBetween
                            // Set up cursorBetween
                            //
                            XTextRange rangeEnd = currentRange.getEnd();
                            cursorBetween =
                                currentRange.getText().createTextCursorByRange(rangeEnd);
                            // If new group, create currentGroupCursor
                            if (currentGroupCursor == null) {
                                currentGroupCursor = (currentRange.getText()
                                                      .createTextCursorByRange(currentRange.getStart()));
                            }
                            // include self in currentGroupCursor
                            currentGroupCursor.goRight((short) (currentRange.getString().length()), true);

                            if (DocumentConnection
                                .javaCompareRegionEnds(cursorBetween, currentGroupCursor) != 0) {
                                /*
                                 * A problem discovered using this check:
                                 * when viewing the document in
                                 * two-pages-side-by-side mode, our visual
                                 * firstAppearanceOrder follows the visual
                                 * ordering on the screen. The problem
                                 * this caused: it sees a citation on the
                                 * 2nd line of the 1st page as appearing
                                 * after one at the 1st line of 2nd
                                 * page. Since we create cursorBetween at
                                 * the end of range1Full (on 1st page), it
                                 * is now BEFORE currentGroupCursor (on
                                 * 2nd page).
                                 */
                                String msg =
                                    "combineCiteMarkers: "
                                    + "cursorBetween.end != currentGroupCursor.end"
                                    + String.format(" (after %s)",
                                                    addToGroup ? "addToGroup" : "startGroup")
                                    + (addToGroup
                                       ? String.format(" comparisonResult: %d\n"
                                                       + "cursorBetween: '%s'\n"
                                                       + "currentRange: '%s'\n"
                                                       + "currentGroupCursor: '%s'\n",
                                                       DocumentConnection
                                                       .javaCompareRegionEnds(cursorBetween,
                                                                              currentGroupCursor),
                                                       cursorBetween.getString(),
                                                       currentRange.getString(),
                                                       currentGroupCursor.getString())
                                       : "");
                                throw new RuntimeException(msg);
                            }
                            prev = cg;
                            prevRange = currentRange;
                        }
                    } // for cgid

                    // close currentGroup
                    if (currentGroup.size() > 1) {
                        joinableGroups.add(currentGroup);
                        joinableGroupsCursors.add(currentGroupCursor);
                    }
                } // if (referenceMarkNames.size() > 0)

                if (joinableGroups.size() > 0) {
                    XTextCursor textCursor = joinableGroupsCursors.get(0);
                    assertCitationCharacterFormatIsOK(textCursor, style);
                }

                /*
                 * Now we can process the joinable groups
                 */
                for (int gi = 0; gi < joinableGroups.size(); gi++) {

                    List<CitationGroup> joinableGroup = joinableGroups.get(gi);
                    /*
                     * Join those in joinableGroups.get(gi)
                     */

                    //
                    // Note: we are taking ownership of the citations (by
                    //       adding to newGroupCitations, then removing
                    //       the original CitationGroup values)

                    List<Citation> newGroupCitations = new ArrayList<>();
                    for (CitationGroup rk : joinableGroup) {
                        newGroupCitations.addAll(rk.citations);
                    }

                    InTextCitationType itcType = joinableGroup.get(0).itcType;

                    // cgPageInfos belong to the CitationGroup (DataModel JabRef52),
                    // but it is not clear how should we handle them here.
                    // We delegate the problem to the backend.
                    List<OOFormattedText> pageInfosForCitations =
                        fr.backend.combinePageInfos(joinableGroup);

                    // Remove the old citation groups from the document.
                    for (int gj = 0; gj < joinableGroup.size(); gj++) {
                        fr.removeCitationGroups(joinableGroup, documentConnection);
                    }

                    XTextCursor textCursor = joinableGroupsCursors.get(gi);
                    // Also remove the spaces between.
                    textCursor.setString("");

                    List<String> citationKeys = (newGroupCitations.stream()
                                                 .map(cit -> cit.citationKey)
                                                 .collect(Collectors.toList()));

                    // Insert reference mark:

                    /* insertSpaceAfter: no, it is already there (or could
                     * be)
                     */
                    boolean insertSpaceAfter = false;
                    createAndFillCitationGroup(fr,
                                               documentConnection,
                                               citationKeys,
                                               pageInfosForCitations,
                                               itcType, // InTextCitationType.AUTHORYEAR_PAR
                                               OOFormattedText.fromString("tmp"),
                                               textCursor,
                                               true, // withText
                                               style,
                                               insertSpaceAfter);
                } // for gi

                madeModifications = (joinableGroups.size() > 0);

            } finally {
                if (useLockControllers) {
                    documentConnection.unlockControllers();
                }
            }

            if (madeModifications) {
                documentConnection.refresh();
                OOFrontend fr2 = new OOFrontend(documentConnection);
                fr2.imposeGlobalOrder(documentConnection);
                OOProcess.ProduceCitationMarkersResult x =
                    OOProcess.produceCitationMarkers(fr2.cgs,
                                                     databases,
                                                     style);
                try {
                    if (useLockControllers) {
                        documentConnection.lockControllers();
                    }
                    applyNewCitationMarkers(documentConnection,
                                            fr2,
                                            x.citMarkers,
                                            style);
                    // bibliography is not refreshed
                } finally {
                    if (useLockControllers) {
                        documentConnection.unlockControllers();
                    }
                }
            }
        } finally {
            documentConnection.leaveUndoContext();
        }
        // documentConnection.refresh();
    } // combineCiteMarkers

    /**
     * GUI action "Separate citations".
     *
     * Do the opposite of combineCiteMarkers.
     * Combined markers are split, with a space inserted between.
     */
    public void unCombineCiteMarkers(List<BibDatabase> databases,
                                     OOBibStyle style)
        throws
        IOException,
        WrappedTargetException,
        NoSuchElementException,
        IllegalArgumentException,
        UndefinedCharacterFormatException,
        UnknownPropertyException,
        NotRemoveableException,
        PropertyVetoException,
        PropertyExistException,
        IllegalTypeException,
        CreationException,
        NoDocumentException,
        JabRefException,
        InvalidStateException {

        styleIsRequired(style);
        Objects.requireNonNull(databases);
        Objects.requireNonNull(style);

        final boolean useLockControllers = true;
        DocumentConnection documentConnection = getDocumentConnectionOrThrow();

        checkStylesExistInTheDocument(style, documentConnection);
        checkIfOpenOfficeIsRecordingChanges(documentConnection);

        OOFrontend fr = new OOFrontend(documentConnection);

        try {
            documentConnection.enterUndoContext("Separate citations");
            boolean madeModifications = false;

            // {@code names} does not need to be sorted.
            List<CitationGroupID> names = new ArrayList<>(fr.cgs.getCitationGroupIDs());

            try {
                if (useLockControllers) {
                    documentConnection.lockControllers();
                }

                int pivot = 0;
                boolean setCharStyleTested = false;

                while (pivot < (names.size())) {
                    CitationGroupID cgid = names.get(pivot);
                    CitationGroup cg = fr.cgs.getCitationGroupOrThrow(cgid);
                    XTextRange range1 = (fr
                                         .getMarkRange(documentConnection, cgid)
                                         .orElseThrow(RuntimeException::new));
                    XTextCursor textCursor = range1.getText().createTextCursorByRange(range1);

                    // If we are supposed to set character format for
                    // citations, test this before making any changes. This
                    // way we can throw an exception before any reference
                    // marks are removed, preventing damage to the user's
                    // document:
                    if (!setCharStyleTested) {
                        assertCitationCharacterFormatIsOK(textCursor, style);
                        setCharStyleTested = true;
                    }

                    // Note: JabRef52 returns cg.pageInfo for the last citation.
                    List<OOFormattedText> pageInfosForCitations = fr.cgs.getPageInfosForCitations(cg);

                    List<Citation> cits = cg.citations;
                    if (cits.size() <= 1) {
                        pivot++;
                        continue;
                    }

                    List<String> keys =
                        cits.stream().map(cit -> cit.citationKey).collect(Collectors.toList());

                    fr.removeCitationGroup(cg, documentConnection);

                    // Now we own the content of cits

                    // Insert mark for each key
                    final int last = keys.size() - 1;
                    for (int i = 0; i < keys.size(); i++) {
                        // Note: by using createAndFillCitationGroup (and not something
                        //       that accepts List<Citation>, we lose the extra
                        //       info stored in the citations.
                        //       We just reread below.

                        boolean insertSpaceAfter = (i != last);
                        boolean withText = cg.itcType != InTextCitationType.INVISIBLE_CIT; // true
                        createAndFillCitationGroup(fr,
                                                   documentConnection,
                                                   keys.subList(i, i + 1), // citationKeys,
                                                   pageInfosForCitations.subList(i, i + 1), // pageInfos,
                                                   InTextCitationType.AUTHORYEAR_PAR, // itcType,
                                                   OOFormattedText.fromString("tmp"),
                                                   textCursor,
                                                   withText,
                                                   style,
                                                   insertSpaceAfter);
                        textCursor.collapseToEnd();
                    }

                    madeModifications = true;
                    pivot++;
                }
            } finally {
                if (useLockControllers) {
                    documentConnection.unlockControllers();
                }
            }

            if (madeModifications) {
                documentConnection.refresh();
                OOFrontend fr2 = new OOFrontend(documentConnection);
                fr2.imposeGlobalOrder(documentConnection);
                OOProcess.ProduceCitationMarkersResult x =
                    OOProcess.produceCitationMarkers(fr2.cgs, databases, style);
                try {
                    if (useLockControllers) {
                        documentConnection.lockControllers();
                    }
                    applyNewCitationMarkers(documentConnection, fr2, x.citMarkers, style);
                    // bibliography is not refreshed
                } finally {
                    if (useLockControllers) {
                        documentConnection.unlockControllers();
                    }
                }
            }
        } finally {
            documentConnection.leaveUndoContext();
        }
        // documentConnection.refresh();
    }

    static class ExportCitedHelperResult {
        /**
         * null: not done; isempty: no unresolved
         */
        List<String> unresolvedKeys;
        BibDatabase newDatabase;
        ExportCitedHelperResult(List<String> unresolvedKeys,
                                BibDatabase newDatabase) {
            this.unresolvedKeys = unresolvedKeys;
            this.newDatabase = newDatabase;
        }
    }

    /**
     * Helper for GUI action "Export cited"
     *
     * Refreshes citation markers, (although the user did not ask for that).
     * Actually, we only call produceCitationMarkers to get x.unresolvedKeys
     *
     * Does not refresh the bibliography.
     */
    public ExportCitedHelperResult exportCitedHelper(List<BibDatabase> databases,
                                                     OOBibStyle style)
        throws
        WrappedTargetException,
        NoSuchElementException,
        NoDocumentException,
        UndefinedCharacterFormatException,
        UnknownPropertyException,
        PropertyVetoException,
        IOException,
        CreationException,
        InvalidStateException {

        DocumentConnection documentConnection = getDocumentConnectionOrThrow();
        try {
            documentConnection.enterUndoContext("Changes during \"Export cited\"");
            return this.generateDatabase(databases, documentConnection);
        } finally {
            documentConnection.leaveUndoContext();
        }
    }

    /**
     * Used from GUI: "Export cited"
     *
     * @param databases The databases to look up the citation keys in the document from.
     * @param documentConnection
     * @return A new database, with cloned entries.
     *
     * If a key is not found, it is added to result.unresolvedKeys
     *
     * Cross references (in StandardField.CROSSREF) are followed (not recursively):
     * if the referenced entry is found, it is included in the result.
     * If it is not found, it is silently ignored.
     */
    private ExportCitedHelperResult generateDatabase(List<BibDatabase> databases,
                                                     DocumentConnection documentConnection)
        throws
        NoSuchElementException,
        WrappedTargetException,
        NoDocumentException,
        UnknownPropertyException {

        OOFrontend fr = new OOFrontend(documentConnection);
        CitedKeys cks = fr.cgs.getCitedKeys();
        cks.lookupInDatabases(databases);

        List<String> unresolvedKeys = new ArrayList<>();
        BibDatabase resultDatabase = new BibDatabase();

        List<BibEntry> entriesToInsert = new ArrayList<>();
        Set<String> seen = new HashSet<>(); // Only add crossReference once.

        for (CitedKey ck : cks.values()) {
            if (ck.db.isEmpty()) {
                unresolvedKeys.add(ck.citationKey);
                continue;
            } else {
                BibEntry entry = ck.db.get().entry;
                BibDatabase loopDatabase = ck.db.get().database;

                // If entry found
                BibEntry clonedEntry = (BibEntry) entry.clone();

                // Insert a copy of the entry
                entriesToInsert.add(clonedEntry);

                // Check if the cloned entry has a cross-reference field
                clonedEntry
                    .getField(StandardField.CROSSREF)
                    .ifPresent(crossReference -> {
                            boolean isNew = !seen.contains(crossReference);
                            if (isNew) {
                                // Add it if it is in the current library
                                loopDatabase
                                    .getEntryByCitationKey(crossReference)
                                    .ifPresent(entriesToInsert::add);
                                seen.add(crossReference);
                            }
                        });
            }
        }

        resultDatabase.insertEntries(entriesToInsert);
        return new ExportCitedHelperResult(unresolvedKeys, resultDatabase);
    }

    /*
     * Throw JabRefException if recording changes or the document contains
     * recorded changes.
     */
    public void checkIfOpenOfficeIsRecordingChanges(DocumentConnection documentConnection)
        throws
        UnknownPropertyException,
        WrappedTargetException,
        PropertyVetoException,
        JabRefException {
        /* We could do this: documentConnection.setRecordChanges(false);
         * but it is more transparent to ask.
         */
        boolean recordingChanges = documentConnection.getRecordChanges();
        int nRedlines = documentConnection.countRedlines();
        if (recordingChanges || nRedlines > 0) {
            String msg = "";
            if (recordingChanges) {
                msg += Localization.lang("Cannot work with [Edit]/[Track Changes]/[Record] turned on.");
            }
            if (nRedlines > 0) {
                if (recordingChanges) {
                    msg += "\n";
                }
                msg += Localization.lang("Changes by JabRef"
                                         + " could result in unexpected interactions with"
                                         + " recorded changes.");
                msg += "\n";
                msg += Localization.lang("Use [Edit]/[Track Changes]/[Manage] to resolve them first.");
            }
            String title = Localization.lang("Recording and/or Recorded changes");
            throw new JabRefException(title, msg);
        }
    }

    void styleIsRequired(OOBibStyle style)
        throws
        JabRefException {
        if (style == null) {
            throw new JabRefException("This operation requires a style",
                                      Localization.lang("This operation requires a style.")
                                      + "\n"
                                      + Localization.lang("Please select one."));
        }
    }

    public void checkParagraphStyleExistsInTheDocument(String styleName,
                                                       DocumentConnection documentConnection,
                                                       String labelInJstyleFile,
                                                       String pathToStyleFile)
        throws
        JabRefException,
        NoSuchElementException,
        WrappedTargetException {

        Optional<String> internalName = documentConnection.getInternalNameOfParagraphStyle(styleName);

        if (internalName.isEmpty()) {
            String msg =
                Localization.lang("The %0 paragraph style '%1'"
                                  + " is missing from the document",
                                  labelInJstyleFile,
                                  styleName)
                + "\n"
                + Localization.lang("Please create it in the document or change in the file:")
                + "\n"
                + pathToStyleFile;
            throw new JabRefException(msg);
        }

        if (!internalName.get().equals(styleName)) {
            String msg =
                Localization.lang("The %0 paragraph style '%1'"
                                  + " is a display name for '%2'.",
                                  labelInJstyleFile,
                                  styleName,
                                  internalName.get())
                + "\n"
                + Localization.lang("Please use the latter in the style file below"
                                    + " to avoid localization problems.")
                + "\n"
                + pathToStyleFile;
            throw new JabRefException(msg);
        }
    }

    public void checkCharacterStyleExistsInTheDocument(String styleName,
                                                       DocumentConnection documentConnection,
                                                       String labelInJstyleFile,
                                                       String pathToStyleFile)
        throws
        JabRefException,
        NoSuchElementException,
        WrappedTargetException {

        Optional<String> internalName = (documentConnection
                                         .getInternalNameOfCharacterStyle(styleName));

        if (internalName.isEmpty()) {
            String msg =
                Localization.lang("The %0 character style '%1'"
                                  + " is missing from the document",
                                  labelInJstyleFile,
                                  styleName)
                + "\n"
                + Localization.lang("Please create it in the document or change in the file:")
                + "\n" + pathToStyleFile;
                throw new JabRefException(msg);
        }

        if (!internalName.get().equals(styleName)) {
            String msg =
                Localization.lang("The %0 character style '%1'"
                                  + " is a display name for '%2'.",
                                  labelInJstyleFile,
                                  styleName,
                                  internalName.get())
                + "\n"
                + Localization.lang("Please use the latter in the style file below"
                                    + " to avoid localization problems.")
                + "\n"
                + pathToStyleFile;
            throw new JabRefException(msg);
        }
    }

    public void checkStylesExistInTheDocument(OOBibStyle style, DocumentConnection documentConnection)
        throws
        JabRefException,
        NoSuchElementException,
        WrappedTargetException {

        String pathToStyleFile = style.getPath();

        checkParagraphStyleExistsInTheDocument(style.getReferenceHeaderParagraphFormat(),
                                               documentConnection,
                                               "ReferenceHeaderParagraphFormat",
                                               pathToStyleFile);

        checkParagraphStyleExistsInTheDocument(style.getReferenceParagraphFormat(),
                                               documentConnection,
                                               "ReferenceParagraphFormat",
                                               pathToStyleFile);
        if (style.isFormatCitations()) {
            checkCharacterStyleExistsInTheDocument(style.getCitationCharacterFormat(),
                                                   documentConnection,
                                                   "CitationCharacterFormat",
                                                   pathToStyleFile);
        }
    }

    /**
     * GUI action, refreshes citation markers and bibliography.
     *
     * @param databases Must have at least one.
     * @param style Style.
     * @return List of unresolved citation keys.
     *
     */
    public List<String> updateDocumentActionHelper(List<BibDatabase> databases,
                                                   OOBibStyle style)
        throws
        NoSuchElementException,
        WrappedTargetException,
        IllegalArgumentException,
        CreationException,
        PropertyVetoException,
        UnknownPropertyException,
        UndefinedParagraphFormatException,
        NoDocumentException,
        UndefinedCharacterFormatException,
        IOException,
        JabRefException,
        InvalidStateException {

        styleIsRequired(style);

        DocumentConnection documentConnection = getDocumentConnectionOrThrow();
        checkStylesExistInTheDocument(style, documentConnection);
        checkIfOpenOfficeIsRecordingChanges(documentConnection);

        try {
            documentConnection.enterUndoContext("Refresh bibliography");

            boolean requireSeparation = false;
            // CitationGroups cgs = new CitationGroups(documentConnection);
            OOFrontend fr = new OOFrontend(documentConnection);

            // Check Range overlaps
            int maxReportedOverlaps = 10;
            fr.checkRangeOverlaps(documentConnection,
                                  requireSeparation,
                                  maxReportedOverlaps);

            final boolean useLockControllers = true;
            fr.imposeGlobalOrder(documentConnection);
            OOProcess.ProduceCitationMarkersResult x =
                OOProcess.produceCitationMarkers(fr.cgs,
                                                 databases,
                                                 style);
            try {
                if (useLockControllers) {
                    documentConnection.lockControllers();
                }
                applyNewCitationMarkers(documentConnection,
                                        fr,
                                        x.citMarkers,
                                        style);
                rebuildBibTextSection(documentConnection,
                                      style,
                                      fr,
                                      x.getBibliography(),
                                      this.alwaysAddCitedOnPages);
                return x.getUnresolvedKeys();
            } finally {
                if (useLockControllers && documentConnection.hasControllersLocked()) {
                    documentConnection.unlockControllers();
                }
            }
        } finally {
            documentConnection.leaveUndoContext();
        }
    }

} // end of OOBibBase
