package org.jabref.gui.openoffice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jabref.gui.DialogService;
import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.oostyle.Citation;
import org.jabref.logic.oostyle.CitationGroup;
import org.jabref.logic.oostyle.CitedKey;
import org.jabref.logic.oostyle.CitedKeys;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.logic.openoffice.ConnectionLostException;
import org.jabref.logic.openoffice.CreationException;
import org.jabref.logic.openoffice.EditInsert;
import org.jabref.logic.openoffice.EditMerge;
import org.jabref.logic.openoffice.ManageCitations;
import org.jabref.logic.openoffice.NoDocumentException;
import org.jabref.logic.openoffice.NoDocumentFoundException;
import org.jabref.logic.openoffice.OOFrontend;
import org.jabref.logic.openoffice.Result;
import org.jabref.logic.openoffice.UnoCrossRef;
import org.jabref.logic.openoffice.UnoCursor;
import org.jabref.logic.openoffice.UnoRedlines;
import org.jabref.logic.openoffice.UnoScreenRefresh;
import org.jabref.logic.openoffice.UnoStyle;
import org.jabref.logic.openoffice.UnoUndo;
import org.jabref.logic.openoffice.Update;
import org.jabref.logic.openoffice.UpdateCitationMarkers;
import org.jabref.logic.openoffice.VoidResult;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.OOFormattedText;
import org.jabref.model.openoffice.CitationEntry;

import com.sun.star.beans.IllegalTypeException;
import com.sun.star.beans.NotRemoveableException;
import com.sun.star.beans.PropertyExistException;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.comp.helper.BootstrapException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.DisposedException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.util.InvalidStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for manipulating the Bibliography of the currently started
 * document in OpenOffice.
 */
class OOBibBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(OOBibBase.class);

    /* variables  */
    private final DialogService dialogService;

    /*
     * Shall we add "Cited on pages: ..." to resolved bibliography entries?
     */
    private final boolean alwaysAddCitedOnPages;

    private final OOBibBaseConnect connection;

    /*
     * Constructor
     */
    public OOBibBase(Path loPath, DialogService dialogService)
        throws
        BootstrapException,
        CreationException {

        this.dialogService = dialogService;
        this.connection = new OOBibBaseConnect(loPath, dialogService);
        this.alwaysAddCitedOnPages = false;
    }

    public void guiActionSelectDocument(boolean autoSelectForSingle) {
        try {

            this.connection.selectDocument(autoSelectForSingle);

        } catch (NoDocumentFoundException ex) {
            OOError.from(ex).showErrorDialog(dialogService);
        } catch (WrappedTargetException
                 | IndexOutOfBoundsException
                 | NoSuchElementException ex) {
            LOGGER.warn("Problem connecting", ex);
            dialogService.showErrorDialogAndWait(ex);
        }

        if (this.isConnectedToDocument()) {
            dialogService.notify(Localization.lang("Connected to document") + ": "
                                 + this.getCurrentDocumentTitle().orElse(""));
        }
    }

    /**
     * A simple test for document availability.
     *
     * See also `isDocumentConnectionMissing` for a test
     * actually attempting to use the connection.
     *
     */
    public boolean isConnectedToDocument() {
        return this.connection.isConnectedToDocument();
    }

    /**
     * @return true if we are connected to a document
     */
    public boolean isDocumentConnectionMissing() {
        return this.connection.isDocumentConnectionMissing();
    }

    /**
     * Either return an XTextDocument or return JabRefException.
     */
    public Result<XTextDocument, OOError> getXTextDocument() {
        return this.connection.getXTextDocument();
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

    void showDialog(OOError ex) {
        ex.showErrorDialog(dialogService);
    }

    void showDialog(String title, OOError ex) {
        ex.setTitle(title).showErrorDialog(dialogService);
    }

    VoidResult<OOError> collectResults(String title, List<VoidResult<OOError>> results) {
        String msg = (results.stream()
                      .filter(e -> e.isError())
                      .map(e -> e.getError().getLocalizedMessage())
                      .collect(Collectors.joining("\n\n")));
        if (msg.isEmpty()) {
            return VoidResult.OK();
        } else {
            return VoidResult.Error(new OOError(title, msg));
        }
    }

    boolean testDialog(VoidResult<OOError> res) {
        return res.ifError(ex -> ex.showErrorDialog(dialogService)).isError();
    }

    boolean testDialog(String title, VoidResult<OOError> res) {
        return res.ifError(e -> showDialog(e.setTitle(title))).isError();
    }

    boolean testDialog(String title, List<VoidResult<OOError>> results) {
        return testDialog(title, collectResults(title, results));
    }

    boolean testDialog(String title, VoidResult<OOError>... results) {
        List<VoidResult<OOError>> rs = Arrays.asList(results);
        return testDialog(collectResults(title, rs));
    }

    /*
     *
     * ManageCitationsDialogView
     *
     */
    public Optional<List<CitationEntry>> guiActionGetCitationEntries() {

        final Optional<List<CitationEntry>> FAIL = Optional.empty();
        final String title = Localization.lang("Problem collecting citations");

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title, odoc.asVoidResult())) {
            return FAIL;
        }
        XTextDocument doc = odoc.get();

        if (testDialog(title, checkIfOpenOfficeIsRecordingChanges2(doc))) {
            LOGGER.warn(title);
            return FAIL;
        }

        try {

            return Optional.of(ManageCitations.getCitationEntries(doc));

        } catch (NoDocumentException ex) {
            OOError.from(ex).showErrorDialog(dialogService);
            return FAIL;
        } catch (UnknownPropertyException
                 | WrappedTargetException ex) {
            LOGGER.warn(title, ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
            return FAIL;
        }
    }

    /**
     * Apply editable parts of citationEntries to the document: store
     * pageInfo.
     *
     * Does not change presentation.
     *
     * Note: we use no undo context here, because only
     *       DocumentConnection.setUserDefinedStringPropertyValue() is called,
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
    public void guiActionApplyCitationEntries(List<CitationEntry> citationEntries) {

        final String title = Localization.lang("Problem modifying citation");

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title, odoc.asVoidResult())) {
            return;
        }
        XTextDocument doc = odoc.get();

        try {

            ManageCitations.applyCitationEntries(doc, citationEntries);

        } catch (NoDocumentException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (UnknownPropertyException
                 | NotRemoveableException
                 | PropertyExistException
                 | PropertyVetoException
                 | IllegalTypeException
                 | WrappedTargetException
                 | IllegalArgumentException ex) {
            LOGGER.warn(title, ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
        }
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
     * @param citationType Indicates whether it is an in-text
     *                     citation, a citation in parenthesis or
     *                     an invisible citation.
     *                     The in-text/in-parenthesis distionction is not relevant if
     *                     numbered citations are used.
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
    public void guiActionInsertEntry(List<BibEntry> entries,
                                     BibDatabase database,
                                     List<BibDatabase> allBases,
                                     OOBibStyle style,
                                     InTextCitationType citationType,
                                     String pageInfo,
                                     boolean sync) {

        final String title = "Could not insert entry";

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title,
                       odoc.asVoidResult(),
                       styleIsRequired(style),
                       databaseIsRequired(allBases, OOError::noDataBaseIsOpenForCiting))) {
            return;
        }

        if (entries == null || entries.size() == 0) {
            OOError.noEntriesSelectedForCitation().showErrorDialog(dialogService);
            return;
        }

        XTextDocument doc = odoc.get();

        if (testDialog(title,
                       checkStylesExistInTheDocument(style, doc),
                       checkIfOpenOfficeIsRecordingChanges2(doc))) {
            return;
        }

        boolean useUndoContext = true;

        try {
            if (useUndoContext) {
                UnoUndo.enterUndoContext(doc, "Insert citation");
            }

            OOFrontend fr = new OOFrontend(doc);

            XTextCursor cursor;
            // Get the cursor positioned by the user.
            try {
                cursor = UnoCursor.getViewCursor(doc).orElse(null);
            } catch (RuntimeException ex) {
                cursor = null;
            }

            if (cursor == null) {
                showDialog(title,
                           new OOError(Localization.lang("Could not get the cursor."),
                                       Localization.lang("Could not get the cursor.")));
                return;
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
                showDialog(title, new OOError(title, msg, ex));
                return;
            }

            EditInsert.insertCitationGroup(doc,
                                           fr,
                                           cursor,
                                           entries,
                                           database,
                                           allBases,
                                           style,
                                           citationType,
                                           pageInfo,
                                           sync,
                                           this.alwaysAddCitedOnPages);

        } catch (NoDocumentException ex) {
            OOError.from(ex).showErrorDialog(dialogService);
            return;
        } catch (DisposedException ex) {
            // We need to catch this one here because the OpenOfficePanel class is
            // loaded before connection, and therefore cannot directly reference
            // or catch a DisposedException (which is in a OO JAR file).
            // throw new ConnectionLostException(ex.getMessage());
            OOError.from(new ConnectionLostException("DisposedException")).showErrorDialog(dialogService);
            return;
        } catch (JabRefException ex) {
            OOError.from(ex).showErrorDialog(dialogService);
            return;
        } catch (// com.sun.star.lang.IllegalArgumentException
            CreationException
            | IllegalTypeException
            | NoSuchElementException
            | NotRemoveableException
            | PropertyExistException
            | PropertyVetoException
            | UnknownPropertyException
            | WrappedTargetException ex) {
            LOGGER.warn("Could not insert entry", ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
            return;
        } finally {
            if (useUndoContext) {
                UnoUndo.leaveUndoContext(doc);
            }
        }
    }

    /**
     * GUI action "Merge citations"
     *
     */
    public void guiActionMergeCitationGroups(List<BibDatabase> databases, OOBibStyle style)
        throws
        IOException,
        WrappedTargetException,
        NoSuchElementException,
        NotRemoveableException,
        IllegalArgumentException,
        UnknownPropertyException,
        PropertyVetoException,
        PropertyExistException,
        IllegalTypeException,
        CreationException,
        NoDocumentException,
        JabRefException,
        InvalidStateException {

        final String title = "Could not merge citations";

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title,
                       odoc.asVoidResult(),
                       styleIsRequired(style),
                       databaseIsRequired(databases, OOError::noDataBaseIsOpen))) {
            return;
        }

        XTextDocument doc = odoc.get();

        if (testDialog(title,
                       checkStylesExistInTheDocument(style, doc),
                       checkIfOpenOfficeIsRecordingChanges2(doc))) {
            return;
        }

        OOFrontend fr = new OOFrontend(doc);

        try {
            UnoUndo.enterUndoContext(doc, "Merge citations");

            EditMerge.mergeCitationGroups(doc,
                                          fr,
                                          databases,
                                          style);
        } finally {
            UnoUndo.leaveUndoContext(doc);
        }
    } // MergeCitationGroups

    /**
     * GUI action "Separate citations".
     *
     * Do the opposite of MergeCitationGroups.
     * Combined markers are split, with a space inserted between.
     */
    public void unCombineCiteMarkers(List<BibDatabase> databases,
                                     OOBibStyle style)
        throws
        IOException,
        WrappedTargetException,
        NoSuchElementException,
        IllegalArgumentException,
        UnknownPropertyException,
        NotRemoveableException,
        PropertyVetoException,
        PropertyExistException,
        IllegalTypeException,
        CreationException,
        NoDocumentException,
        JabRefException,
        InvalidStateException {

        final String title = "Separate citations failed";

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title,
                       odoc.asVoidResult(),
                       styleIsRequired(style),
                       databaseIsRequired(databases, OOError::noDataBaseIsOpen))) {
            return;
        }

        Objects.requireNonNull(databases);
        Objects.requireNonNull(style);

        final boolean useLockControllers = true;

        XTextDocument doc = odoc.get();

        if (testDialog(title,
                       checkStylesExistInTheDocument(style, doc),
                       checkIfOpenOfficeIsRecordingChanges2(doc))) {
            return;
        }

        OOFrontend fr = new OOFrontend(doc);

        try {
            UnoUndo.enterUndoContext(doc, "Separate citations");
            boolean madeModifications = false;

            // {@code names} does not need to be sorted.
            List<CitationGroupID> names =
                new ArrayList<>(fr.citationGroups.getCitationGroupIDsUnordered());

            try {
                if (useLockControllers) {
                    UnoScreenRefresh.lockControllers(doc);
                }

                int pivot = 0;

                while (pivot < (names.size())) {
                    CitationGroupID cgid = names.get(pivot);
                    CitationGroup cg = fr.citationGroups.getCitationGroupOrThrow(cgid);
                    XTextRange range1 = (fr
                                         .getMarkRange(doc, cgid)
                                         .orElseThrow(RuntimeException::new));
                    XTextCursor textCursor = range1.getText().createTextCursorByRange(range1);

                    // Note: JabRef52 returns cg.pageInfo for the last citation.
                    List<OOFormattedText> pageInfosForCitations =
                        cg.getPageInfosForCitationsInStorageOrder();

                    List<Citation> cits = cg.citationsInStorageOrder;
                    if (cits.size() <= 1) {
                        pivot++;
                        continue;
                    }

                    List<String> keys =
                        cits.stream().map(cit -> cit.citationKey).collect(Collectors.toList());

                    fr.removeCitationGroup(cg, doc);

                    // Now we own the content of cits

                    // Insert mark for each key
                    final int last = keys.size() - 1;
                    for (int i = 0; i < keys.size(); i++) {
                        boolean insertSpaceAfter = (i != last);
                        List<String> citationKeys1 = keys.subList(i, i + 1);
                        List<OOFormattedText> pageInfos1 = pageInfosForCitations.subList(i, i + 1);
                        OOFormattedText citationText1 = OOFormattedText.fromString("tmp");
                        UpdateCitationMarkers.createAndFillCitationGroup(fr,
                                                                         doc,
                                                                         citationKeys1,
                                                                         pageInfos1,
                                                                         cg.citationType,
                                                                         citationText1,
                                                                         textCursor,
                                                                         style,
                                                                         insertSpaceAfter);
                        textCursor.collapseToEnd();
                    }

                    madeModifications = true;
                    pivot++;
                }
            } finally {
                if (useLockControllers) {
                    UnoScreenRefresh.unlockControllers(doc);
                }
            }

            if (madeModifications) {
                UnoCrossRef.refresh(doc);
                OOFrontend fr2 = new OOFrontend(doc);
                Update.updateDocument(doc,
                                      fr2,
                                      databases,
                                      style,
                                      false, /* doUpdateBibliography */
                                      this.alwaysAddCitedOnPages);
            }

        } finally {
            UnoUndo.leaveUndoContext(doc);
        }
    }

    static class GenerateDatabaseResult {
        /**
         * null: not done; isEmpty: no unresolved
         */
        List<String> unresolvedKeys;
        BibDatabase newDatabase;
        GenerateDatabaseResult(List<String> unresolvedKeys,
                               BibDatabase newDatabase) {
            this.unresolvedKeys = unresolvedKeys;
            this.newDatabase = newDatabase;
        }
    }

    /**
     * GUI action for "Export cited"
     *
     * Does not refresh the bibliography.
     *
     * @param returnPartialResult If there are some unresolved keys,
     *       shall we return an otherwise nonempty result, or Optional.empty()?
     */
    public Optional<BibDatabase> exportCitedHelper(List<BibDatabase> databases,
                                                   boolean returnPartialResult)
        throws
        WrappedTargetException,
        NoSuchElementException,
        NoDocumentException,
        UnknownPropertyException,
        PropertyVetoException,
        IOException,
        CreationException,
        InvalidStateException,
        JabRefException {

        final Optional<BibDatabase> FAIL = Optional.empty();
        final String title = Localization.lang("Unable to generate new library");

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title,
                       odoc.asVoidResult(),
                       databaseIsRequired(databases, OOError::noDataBaseIsOpenForExport))) {
            return FAIL;
        }

        XTextDocument doc = odoc.get();

        try {

            GenerateDatabaseResult result;
            try {
                UnoUndo.enterUndoContext(doc, "Changes during \"Export cited\"");
                result = this.generateDatabase(databases, doc);
            } finally {
                // There should be no changes, thus no Undo entry
                // in LibreOffice
                UnoUndo.leaveUndoContext(doc);
            }

            if (!result.newDatabase.hasEntries()) {
                dialogService.showErrorDialogAndWait(
                    Localization.lang("Unable to generate new library"),
                    Localization.lang("Your OpenOffice/LibreOffice document references"
                                      + " no citation keys"
                                      + " which could also be found in your current library."));
                return FAIL;
            }

            List<String> unresolvedKeys = result.unresolvedKeys;
            if (!unresolvedKeys.isEmpty()) {
                dialogService.showErrorDialogAndWait(
                    Localization.lang("Unable to generate new library"),
                    Localization.lang("Your OpenOffice/LibreOffice document references"
                                       + " at least %0 citation keys"
                                       + " which could not be found in your current library."
                                       + " Some of these are %1.",
                                      String.valueOf(unresolvedKeys.size()),
                                      String.join(", ", unresolvedKeys)));
                if (returnPartialResult) {
                    return Optional.of(result.newDatabase);
                } else {
                    return FAIL;
                }
            }
            return Optional.of(result.newDatabase);
        } catch (NoDocumentException ex) {
                OOError.from(ex).showErrorDialog(dialogService);
        } catch (UnknownPropertyException
                 | NoSuchElementException
                 | WrappedTargetException ex) {
            LOGGER.warn("Problem generating new database.", ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
        }
        return FAIL;
    }

    /**
     * Used from GUI: "Export cited"
     *
     * @param databases The databases to look up the citation keys in the document from.
     * @return A new database, with cloned entries.
     *
     * If a key is not found, it is added to result.unresolvedKeys
     *
     * Cross references (in StandardField.CROSSREF) are followed (not recursively):
     * if the referenced entry is found, it is included in the result.
     * If it is not found, it is silently ignored.
     */
    private GenerateDatabaseResult generateDatabase(List<BibDatabase> databases, XTextDocument doc)
        throws
        NoSuchElementException,
        WrappedTargetException,
        NoDocumentException,
        UnknownPropertyException {

        OOFrontend fr = new OOFrontend(doc);
        CitedKeys cks = fr.citationGroups.getCitedKeysUnordered();
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
        return new GenerateDatabaseResult(unresolvedKeys, resultDatabase);
    }

    /*
     * Throw JabRefException if recording changes or the document contains
     * recorded changes.
     */
    private static void checkIfOpenOfficeIsRecordingChanges(XTextDocument doc)
        throws
        UnknownPropertyException,
        WrappedTargetException,
        JabRefException {
        boolean recordingChanges = UnoRedlines.getRecordChanges(doc);
        int nRedlines = UnoRedlines.countRedlines(doc);
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

    private static VoidResult<OOError> checkIfOpenOfficeIsRecordingChanges2(XTextDocument doc) {

        String title = Localization.lang("Recording and/or Recorded changes");
        try {
            boolean recordingChanges = UnoRedlines.getRecordChanges(doc);
            int nRedlines = UnoRedlines.countRedlines(doc);
            if (recordingChanges || nRedlines > 0) {
                String msg = "";
                if (recordingChanges) {
                    msg += Localization.lang("Cannot work with"
                                             + " [Edit]/[Track Changes]/[Record] turned on.");
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
                return VoidResult.Error(new OOError(title, msg));
            }
        } catch (UnknownPropertyException | WrappedTargetException ex) {
            String msg = Localization.lang("Error while checking if Writer"
                                           + " is recording changes or has recorded changes.");
            return VoidResult.Error(new OOError(title, msg, ex));
        }
        return VoidResult.OK();
    }

    /*
     * Called from GUI.
     * @return true on error, false if OK.
     */
    public boolean guiCheckIfOpenOfficeIsRecordingChanges(String title) {
        final boolean FAIL = true;
        final boolean PASS = false;
        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title, odoc.asVoidResult())) {
            return FAIL;
        }
        XTextDocument doc = odoc.get();

        if (testDialog(title, checkIfOpenOfficeIsRecordingChanges2(doc))) {
            return FAIL;
        }
        return PASS;
    }

    public boolean guiCheckIfConnectedToDocument(String title) {
        final boolean FAIL = true;
        final boolean PASS = false;
        if (!isConnectedToDocument()) {
            String msg = Localization.lang("Not connected to any Writer document."
                                           + " Please make sure a document is open,"
                                           + " and use the 'Select Writer document' button"
                                           + " to connect to it.");
            dialogService.showErrorDialogAndWait(title, msg);
            return FAIL;
        }
        return PASS;
    }

    VoidResult<OOError> styleIsRequired(OOBibStyle style) {
        if (style == null) {
            return VoidResult.Error(OOError.noValidStyleSelected());
        } else {
            return VoidResult.OK();
        }
    }

    VoidResult<OOError> databaseIsRequired(List<BibDatabase> databases,
                                           Supplier<OOError> fun) {
        if (databases == null || databases.isEmpty()) {
            return VoidResult.Error(fun.get());
        } else {
            return VoidResult.OK();
        }
    }

    /*
     * Checks existence and also checks if it is not an internal name.
     */
    public VoidResult<OOError> checkStyleExistsInTheDocument(String familyName,
                                                             String styleName,
                                                             XTextDocument doc,
                                                             String labelInJstyleFile,
                                                             String pathToStyleFile)
        throws
        NoSuchElementException,
        WrappedTargetException {

        Optional<String> internalName = UnoStyle.getInternalNameOfStyle(doc, familyName, styleName);

        if (internalName.isEmpty()) {
            String msg =
                switch (familyName) {
                case UnoStyle.PARAGRAPH_STYLES ->
                Localization.lang("The %0 paragraph style '%1' is missing from the document",
                                  labelInJstyleFile,
                                  styleName);
                case UnoStyle.CHARACTER_STYLES ->
                Localization.lang("The %0 character style '%1' is missing from the document",
                                  labelInJstyleFile,
                                  styleName);
                default ->
                throw new RuntimeException("Expected " + UnoStyle.CHARACTER_STYLES
                                           + " or " + UnoStyle.PARAGRAPH_STYLES
                                           + " for familyName");
                }
                + "\n"
                + Localization.lang("Please create it in the document or change in the file:")
                + "\n"
                + pathToStyleFile;
                return VoidResult.Error(new OOError("StyleIsNotKnown", msg));
        }

        if (!internalName.get().equals(styleName)) {
            String msg =
                switch (familyName) {
                case UnoStyle.PARAGRAPH_STYLES ->
                Localization.lang("The %0 paragraph style '%1' is a display name for '%2'.",
                                  labelInJstyleFile,
                                  styleName,
                                  internalName.get());
                case UnoStyle.CHARACTER_STYLES ->
                Localization.lang("The %0 character style '%1' is a display name for '%2'.",
                                  labelInJstyleFile,
                                  styleName,
                                  internalName.get());
                default ->
                throw new RuntimeException("Expected " + UnoStyle.CHARACTER_STYLES
                                           + " or " + UnoStyle.PARAGRAPH_STYLES
                                           + " for familyName");
                }
                + "\n"
                + Localization.lang("Please use the latter in the style file below"
                                    + " to avoid localization problems.")
                + "\n"
                + pathToStyleFile;
                return VoidResult.Error(new OOError("StyleNameIsNotInternal", msg));
        }
        return VoidResult.OK();
    }

    public VoidResult<OOError> checkStylesExistInTheDocument(OOBibStyle style, XTextDocument doc) {

        String pathToStyleFile = style.getPath();

        List<VoidResult<OOError>> results = new ArrayList<>();
        try {
            results.add(checkStyleExistsInTheDocument(UnoStyle.PARAGRAPH_STYLES,
                                                      style.getReferenceHeaderParagraphFormat(),
                                                      doc,
                                                      "ReferenceHeaderParagraphFormat",
                                                      pathToStyleFile));
            results.add(checkStyleExistsInTheDocument(UnoStyle.PARAGRAPH_STYLES,
                                                      style.getReferenceParagraphFormat(),
                                                      doc,
                                                      "ReferenceParagraphFormat",
                                                      pathToStyleFile));
            if (style.isFormatCitations()) {
                results.add(checkStyleExistsInTheDocument(UnoStyle.CHARACTER_STYLES,
                                                          style.getCitationCharacterFormat(),
                                                          doc,
                                                          "CitationCharacterFormat",
                                                          pathToStyleFile));
            }
        } catch (NoSuchElementException
                 | WrappedTargetException ex) {
            results.add(VoidResult.Error(new OOError("Other error in checkStyleExistsInTheDocument",
                                                     ex.getMessage(),
                                                     ex)));
        }

        return collectResults("checkStyleExistsInTheDocument failed", results);
    }

    /**
     * GUI action, refreshes citation markers and bibliography.
     *
     * @param databases Must have at least one.
     * @param style Style.
     *
     */
    public void guiActionUpdateDocument(List<BibDatabase> databases, OOBibStyle style) {

        final String title = Localization.lang("Unable to synchronize bibliography");

        try {

            Result<XTextDocument, OOError> odoc = getXTextDocument();
            if (testDialog(title, List.of(odoc.asVoidResult(), styleIsRequired(style)))) {
                return;
            }

            XTextDocument doc = odoc.get();

            if (testDialog(title, List.of(styleIsRequired(style),
                                          checkStylesExistInTheDocument(style, doc)))) {
                return;
            }

            checkIfOpenOfficeIsRecordingChanges(doc);

            OOFrontend fr = new OOFrontend(doc);
            // Check Range overlaps
            boolean requireSeparation = false;
            int maxReportedOverlaps = 10;
            VoidResult<OOError> ee = (fr.checkRangeOverlaps(doc,
                                                            requireSeparation,
                                                            maxReportedOverlaps)
                                      .mapError(OOError::fromJabRefException));
            if (testDialog(title, ee)) {
                return;
            }

            List<String> unresolvedKeys;
            try {
            UnoUndo.enterUndoContext(doc, "Refresh bibliography");
            boolean doUpdateBibliography = true;
            unresolvedKeys = Update.updateDocument(doc,
                                                   fr,
                                                   databases,
                                                   style,
                                                   doUpdateBibliography,
                                                   this.alwaysAddCitedOnPages);
            } finally {
                UnoUndo.leaveUndoContext(doc);
            }

        if (!unresolvedKeys.isEmpty()) {
                String msg = Localization.lang(
                    "Your OpenOffice/LibreOffice document references the citation key '%0',"
                    + " which could not be found in your current library.",
                    unresolvedKeys.get(0));
                dialogService.showErrorDialogAndWait(title, msg);
                return;
            }

        } catch (JabRefException ex) {
            OOError.from(ex).showErrorDialog(dialogService);
        } catch (ConnectionLostException ex) {
            OOError.from(ex).showErrorDialog(dialogService);
        } catch (NoDocumentException ex) {
            OOError.from(ex).showErrorDialog(dialogService);
        } catch (com.sun.star.lang.IllegalArgumentException
                 | PropertyVetoException
                 | UnknownPropertyException
                 | WrappedTargetException
                 | NoSuchElementException
                 | CreationException ex) {
            LOGGER.warn("Could not update bibliography", ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
        }
    }

} // end of OOBibBase
