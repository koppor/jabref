package org.jabref.gui.openoffice;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jabref.gui.DialogService;
import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.logic.openoffice.EditInsert;
import org.jabref.logic.openoffice.EditMerge;
import org.jabref.logic.openoffice.EditSeparate;
import org.jabref.logic.openoffice.ExportCited;
import org.jabref.logic.openoffice.FunctionalTextViewCursor;
import org.jabref.logic.openoffice.ManageCitations;
import org.jabref.logic.openoffice.NoDocumentFoundException;
import org.jabref.logic.openoffice.OOFrontend;
import org.jabref.logic.openoffice.UnoCrossRef;
import org.jabref.logic.openoffice.UnoCursor;
import org.jabref.logic.openoffice.UnoRedlines;
import org.jabref.logic.openoffice.UnoStyle;
import org.jabref.logic.openoffice.UnoUndo;
import org.jabref.logic.openoffice.Update;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.openoffice.CitationEntry;
import org.jabref.model.openoffice.CreationException;
import org.jabref.model.openoffice.NoDocumentException;
import org.jabref.model.openoffice.Result;
import org.jabref.model.openoffice.VoidResult;

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
     * After inserting a citation, if ooPrefs.getSyncWhenCiting() returns true,
     * shall we also update the bibliography?
     */
    private final boolean refreshBibliographyDuringSyncWhenCiting;

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

        this.refreshBibliographyDuringSyncWhenCiting = false;
        this.alwaysAddCitedOnPages = false;
    }

    public void guiActionSelectDocument(boolean autoSelectForSingle) {
        final String title = Localization.lang("Problem connecting");

        try {

            this.connection.selectDocument(autoSelectForSingle);

        } catch (NoDocumentFoundException ex) {
            OOError.from(ex).showErrorDialog(dialogService);
        } catch (DisposedException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (WrappedTargetException
                 | IndexOutOfBoundsException
                 | NoSuchElementException ex) {
            LOGGER.warn("Problem connecting", ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
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

    /* ******************************************************
     *
     *  Tools to collect and show precondition test results
     *
     * ******************************************************/

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
            return VoidResult.ok();
        } else {
            return VoidResult.error(new OOError(title, msg));
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
     * Get the cursor positioned by the user for inserting text.
     *
     */
    Result<XTextCursor, OOError> getUserCursorForTextInsertion(XTextDocument doc, String title) {

        XTextCursor cursor;
        // Get the cursor positioned by the user.
        try {
            cursor = UnoCursor.getViewCursor(doc).orElse(null);
        } catch (RuntimeException ex) {
            return Result.error(new OOError(title,
                                            Localization.lang("Could not get the cursor."),
                                            ex));
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
            return Result.error(new OOError(title, msg, ex));
        }
        return Result.ok(cursor);
    }

    /**
     * This may move the view cursor.
     */
    Result<FunctionalTextViewCursor, OOError> getFunctionalTextViewCursor(XTextDocument doc,
                                                                          String title) {
        String messageOnFailureToObtain =
            Localization.lang("Please move the cursor into the document text.")
            + "\n"
            + Localization.lang("To get the visual positions of your citations"
                                + " I need to move the cursor around,"
                                + " but could not get it.");
        Result<FunctionalTextViewCursor, JabRefException> result =
            FunctionalTextViewCursor.get(doc, messageOnFailureToObtain);
        return result.mapError(e -> OOError.from(e).setTitle(title));
    }

    /*
     *
     * Tests for preconditions.
     *
     */

    private static VoidResult<OOError> checkIfOpenOfficeIsRecordingChanges(XTextDocument doc) {

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
                return VoidResult.error(new OOError(title, msg));
            }
        } catch (UnknownPropertyException | WrappedTargetException ex) {
            String msg = Localization.lang("Error while checking if Writer"
                                           + " is recording changes or has recorded changes.");
            return VoidResult.error(new OOError(title, msg, ex));
        }
        return VoidResult.ok();
    }

    VoidResult<OOError> styleIsRequired(OOBibStyle style) {
        if (style == null) {
            return VoidResult.error(OOError.noValidStyleSelected());
        } else {
            return VoidResult.ok();
        }
    }

    VoidResult<OOError> databaseIsRequired(List<BibDatabase> databases,
                                           Supplier<OOError> fun) {
        if (databases == null || databases.isEmpty()) {
            return VoidResult.error(fun.get());
        } else {
            return VoidResult.ok();
        }
    }

    VoidResult<OOError> selectedBibEntryIsRequired(List<BibEntry> entries,
                                                   Supplier<OOError> fun) {
        if (entries == null || entries.isEmpty()) {
            return VoidResult.error(fun.get());
        } else {
            return VoidResult.ok();
        }
    }

    /*
     * Checks existence and also checks if it is not an internal name.
     */
    private VoidResult<OOError> checkStyleExistsInTheDocument(String familyName,
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
                return VoidResult.error(new OOError("StyleIsNotKnown", msg));
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
                return VoidResult.error(new OOError("StyleNameIsNotInternal", msg));
        }
        return VoidResult.ok();
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
            results.add(VoidResult.error(new OOError("Other error in checkStyleExistsInTheDocument",
                                                     ex.getMessage(),
                                                     ex)));
        }

        return collectResults("checkStyleExistsInTheDocument failed", results);
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

        if (testDialog(title, checkIfOpenOfficeIsRecordingChanges(doc))) {
            LOGGER.warn(title);
            return FAIL;
        }

        try {

            return Optional.of(ManageCitations.getCitationEntries(doc));

        } catch (NoDocumentException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
            return FAIL;
        } catch (DisposedException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
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
        } catch (DisposedException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (UnknownPropertyException
                 | NotRemoveableException
                 | PropertyExistException
                 | PropertyVetoException
                 | IllegalTypeException
                 | WrappedTargetException
                 | com.sun.star.lang.IllegalArgumentException ex) {
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
     *                      Consistency: for each entry in {@entries} looking it up in
     *                      {@code syncOptions.get().databases} should yield {@code database}.
     *                      Otherwise we may get a different entry
     *                      with and without synchronization.
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
     * @param syncOptions   Indicates whether in-text citations
     *                      should be refreshed in the document.
     *                      Optional.empty() indicates no refresh.
     *                      Otherwise provides options for refreshing the reference list.
     *
     *
     */
    public void guiActionInsertEntry(List<BibEntry> entries,
                                     BibDatabase database,
                                     OOBibStyle style,
                                     InTextCitationType citationType,
                                     String pageInfo,
                                     Optional<Update.SyncOptions> syncOptions) {

        final String title = "Could not insert citation";

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title,
                       odoc.asVoidResult(),
                       styleIsRequired(style),
                       selectedBibEntryIsRequired(entries, OOError::noEntriesSelectedForCitation))) {
            return;
        }

        XTextDocument doc = odoc.get();
        Result<XTextCursor, OOError> cursor = getUserCursorForTextInsertion(doc, title);

        if (testDialog(title,
                       cursor.asVoidResult(),
                       checkStylesExistInTheDocument(style, doc),
                       checkIfOpenOfficeIsRecordingChanges(doc))) {
            return;
        }

        /*
         * For sync we need a FunctionalTextViewCursor.
         */
        Result<FunctionalTextViewCursor, OOError> fcursor = null;
        if (syncOptions.isPresent()) {
            fcursor = getFunctionalTextViewCursor(doc, title);
            if (testDialog(title, fcursor.asVoidResult())) {
                return;
            }
        }

        syncOptions
            .map(e -> e.setUpdateBibliography(this.refreshBibliographyDuringSyncWhenCiting))
            .map(e -> e.setAlwaysAddCitedOnPages(this.alwaysAddCitedOnPages));

        if (syncOptions.isPresent()) {
            if (testDialog(databaseIsRequired(syncOptions.get().databases,
                                              OOError::noDataBaseIsOpenForSyncingAfterCitation))) {
                return;
            }
        }

        try {
            UnoUndo.enterUndoContext(doc, "Insert citation");

            OOFrontend fr = new OOFrontend(doc);

            EditInsert.insertCitationGroup(doc,
                                           fr,
                                           cursor.get(),
                                           entries,
                                           database,
                                           style,
                                           citationType,
                                           pageInfo);

            if (syncOptions.isPresent()) {
                Update.resync(doc, style, fcursor.get(), syncOptions.get());
            }

        } catch (NoDocumentException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
            return;
        } catch (DisposedException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
            return;
        } catch (JabRefException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
            return;
        } catch (CreationException
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
            UnoUndo.leaveUndoContext(doc);
        }
    }

    /**
     * GUI action "Merge citations"
     *
     */
    public void guiActionMergeCitationGroups(List<BibDatabase> databases, OOBibStyle style) {

        final String title = Localization.lang("Problem combining cite markers");

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title,
                       odoc.asVoidResult(),
                       styleIsRequired(style),
                       databaseIsRequired(databases, OOError::noDataBaseIsOpen))) {
            return;
        }

        XTextDocument doc = odoc.get();

        Result<FunctionalTextViewCursor, OOError> fcursor = getFunctionalTextViewCursor(doc, title);

        if (testDialog(title,
                       fcursor.asVoidResult(),
                       checkStylesExistInTheDocument(style, doc),
                       checkIfOpenOfficeIsRecordingChanges(doc))) {
            return;
        }

        try {
            UnoUndo.enterUndoContext(doc, "Merge citations");

            OOFrontend fr = new OOFrontend(doc);
            boolean madeModifications = EditMerge.mergeCitationGroups(doc, fr, style);
            if (madeModifications) {
                UnoCrossRef.refresh(doc);
                Update.SyncOptions syncOptions = new Update.SyncOptions(databases);
                Update.resync(doc, style, fcursor.get(), syncOptions);
            }

        } catch (NoDocumentException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (DisposedException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (JabRefException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (CreationException
                 | IllegalTypeException
                 | InvalidStateException
                 | NoSuchElementException
                 | NotRemoveableException
                 | PropertyExistException
                 | PropertyVetoException
                 | UnknownPropertyException
                 | WrappedTargetException
                 | com.sun.star.lang.IllegalArgumentException ex) {
            LOGGER.warn("Problem combining cite markers", ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
        } finally {
            UnoUndo.leaveUndoContext(doc);
            fcursor.get().restore(doc);
        }
    } // MergeCitationGroups

    /**
     * GUI action "Separate citations".
     *
     * Do the opposite of MergeCitationGroups.
     * Combined markers are split, with a space inserted between.
     */
    public void guiActionSeparateCitations(List<BibDatabase> databases, OOBibStyle style) {

        final String title = Localization.lang("Problem during separating cite markers");

        Result<XTextDocument, OOError> odoc = getXTextDocument();
        if (testDialog(title,
                       odoc.asVoidResult(),
                       styleIsRequired(style),
                       databaseIsRequired(databases, OOError::noDataBaseIsOpen))) {
            return;
        }

        XTextDocument doc = odoc.get();
        Result<FunctionalTextViewCursor, OOError> fcursor = getFunctionalTextViewCursor(doc, title);

        if (testDialog(title,
                       fcursor.asVoidResult(),
                       checkStylesExistInTheDocument(style, doc),
                       checkIfOpenOfficeIsRecordingChanges(doc))) {
            return;
        }

        try {
            UnoUndo.enterUndoContext(doc, "Separate citations");

            OOFrontend fr = new OOFrontend(doc);
            boolean madeModifications = EditSeparate.separateCitations(doc, fr, databases, style);
            if (madeModifications) {
                UnoCrossRef.refresh(doc);
                Update.SyncOptions syncOptions = new Update.SyncOptions(databases);
                Update.resync(doc, style, fcursor.get(), syncOptions);
            }

        } catch (NoDocumentException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (DisposedException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (JabRefException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (CreationException
                 | IllegalTypeException
                 | InvalidStateException
                 | NoSuchElementException
                 | NotRemoveableException
                 | PropertyExistException
                 | PropertyVetoException
                 | UnknownPropertyException
                 | WrappedTargetException
                 | com.sun.star.lang.IllegalArgumentException ex) {
            LOGGER.warn("Problem during separating cite markers", ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
        } finally {
            UnoUndo.leaveUndoContext(doc);
            fcursor.get().restore(doc);
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
                                                   boolean returnPartialResult) {

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

            ExportCited.GenerateDatabaseResult result;
            try {
                UnoUndo.enterUndoContext(doc, "Changes during \"Export cited\"");
                result = ExportCited.generateDatabase(doc, databases);
            } finally {
                // There should be no changes, thus no Undo entry should appear
                // in LibreOffice.
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
        } catch (DisposedException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (NoSuchElementException
                 | UnknownPropertyException
                 | WrappedTargetException
                 | com.sun.star.lang.IllegalArgumentException ex) {
            LOGGER.warn("Problem generating new database.", ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
        }
        return FAIL;
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
            if (testDialog(title,
                           odoc.asVoidResult(),
                           styleIsRequired(style))) {
                return;
            }

            XTextDocument doc = odoc.get();

            Result<FunctionalTextViewCursor, OOError> fcursor = getFunctionalTextViewCursor(doc, title);

            if (testDialog(title,
                           fcursor.asVoidResult(),
                           checkStylesExistInTheDocument(style, doc),
                           checkIfOpenOfficeIsRecordingChanges(doc))) {
                return;
            }

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

                Update.SyncOptions syncOptions = new Update.SyncOptions(databases);
                syncOptions
                    .setUpdateBibliography(true)
                    .setAlwaysAddCitedOnPages(this.alwaysAddCitedOnPages);

                unresolvedKeys = Update.sync(doc, fr, style, fcursor.get(), syncOptions);

            } finally {
                UnoUndo.leaveUndoContext(doc);
                fcursor.get().restore(doc);
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
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (NoDocumentException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (DisposedException ex) {
            OOError.from(ex).setTitle(title).showErrorDialog(dialogService);
        } catch (CreationException
                 | NoSuchElementException
                 | PropertyVetoException
                 | UnknownPropertyException
                 | WrappedTargetException
                 | com.sun.star.lang.IllegalArgumentException ex) {
            LOGGER.warn("Could not update bibliography", ex);
            OOError.fromMisc(ex).setTitle(title).showErrorDialog(dialogService);
        }
    }

} // end of OOBibBase
