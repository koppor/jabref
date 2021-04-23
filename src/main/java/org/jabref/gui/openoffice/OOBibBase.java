package org.jabref.gui.openoffice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.gui.DialogService;
import org.jabref.logic.JabRefException;
import org.jabref.logic.bibtex.comparator.FieldComparator;
import org.jabref.logic.bibtex.comparator.FieldComparatorStack;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.layout.Layout;
import org.jabref.logic.openoffice.CitationEntry;
import org.jabref.logic.openoffice.CitationMarkerEntry;
import org.jabref.logic.openoffice.CitationMarkerEntryImpl;
import org.jabref.logic.openoffice.OOBibStyle;
import org.jabref.logic.openoffice.OOPreFormatter;
import org.jabref.logic.openoffice.OOUtil;
import org.jabref.logic.openoffice.UndefinedParagraphFormatException;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;

import com.sun.star.beans.IllegalTypeException;
import com.sun.star.beans.NotRemoveableException;
import com.sun.star.beans.PropertyExistException;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.comp.helper.BootstrapException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XEnumerationAccess;
import com.sun.star.container.XNameAccess;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XDesktop;
import com.sun.star.lang.DisposedException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextSection;
import com.sun.star.uno.Any;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import com.sun.star.util.InvalidStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for manipulating the Bibliography of the currently started
 * document in OpenOffice.
 */
@AllowedToUseAwt("Requires AWT for italics and bold")
class OOBibBase {
    private static final OOPreFormatter POSTFORMATTER = new OOPreFormatter();

    private static final String BIB_SECTION_NAME = "JR_bib";
    private static final String BIB_SECTION_END_NAME = "JR_bib_end";


    /* Types of in-text citation. (itcType)
     * Their numeric values are used in reference mark names.
     */
    private static final int AUTHORYEAR_PAR = 1;
    private static final int AUTHORYEAR_INTEXT = 2;
    private static final int INVISIBLE_CIT = 3;

    private static final Comparator<BibEntry> AUTHOR_YEAR_TITLE_COMPARATOR =
        makeAuthorYearTitleComparator();
    private static final Comparator<BibEntry> YEAR_AUTHOR_TITLE_COMPARATOR =
        makeYearAuthorTitleComparator();

    private static final Logger LOGGER =
        LoggerFactory.getLogger(OOBibBase.class);

    /* variables  */
    private final DialogService dialogService;
    private final XDesktop xDesktop;

    /**
     * Created when connected to a document.
     *
     * Cleared (to null) when we discover we lost the connection.
     */
    private DocumentConnection xDocumentConnection;

    /*
     * Constructor
     */
    public OOBibBase(Path loPath,
                     DialogService dialogService)
        throws
        BootstrapException,
        CreationException {

        this.dialogService = dialogService;
        this.xDesktop = simpleBootstrap(loPath);
    }

    private static Comparator<BibEntry> makeAuthorYearTitleComparator() {
        FieldComparator a = new FieldComparator(StandardField.AUTHOR);
        FieldComparator y = new FieldComparator(StandardField.YEAR);
        FieldComparator t = new FieldComparator(StandardField.TITLE);

        List<Comparator<BibEntry>> ayt = new ArrayList<>(3);
        ayt.add(a);
        ayt.add(y);
        ayt.add(t);
        return new FieldComparatorStack<>(ayt);
    }

    private static Comparator<BibEntry> makeYearAuthorTitleComparator() {
        FieldComparator y = new FieldComparator(StandardField.YEAR);
        FieldComparator a = new FieldComparator(StandardField.AUTHOR);
        FieldComparator t = new FieldComparator(StandardField.TITLE);

        List<Comparator<BibEntry>> yat = new ArrayList<>(3);
        yat.add(y);
        yat.add(a);
        yat.add(t);
        return new FieldComparatorStack<>(yat);
    }

    /* *****************************
     *
     *  Establish connection
     *
     * *****************************/

    private XDesktop simpleBootstrap(Path loPath)
        throws
        CreationException,
        BootstrapException {

        // Get the office component context:
        XComponentContext context = org.jabref.gui.openoffice.Bootstrap.bootstrap(loPath);
        XMultiComponentFactory sem = context.getServiceManager();

        // Create the desktop, which is the root frame of the
        // hierarchy of frames that contain viewable components:
        Object desktop;
        try {
            desktop = sem.createInstanceWithContext("com.sun.star.frame.Desktop", context);
        } catch (Exception e) {
            throw new CreationException(e.getMessage());
        }
        XDesktop result = unoQI(XDesktop.class, desktop);

        unoQI(XComponentLoader.class, desktop);

        return result;
    }

    private static List<XTextDocument> getTextDocuments(XDesktop desktop)
        throws
        NoSuchElementException,
        WrappedTargetException {

        List<XTextDocument> result = new ArrayList<>();

        XEnumerationAccess enumAccess = desktop.getComponents();
        XEnumeration compEnum = enumAccess.createEnumeration();

        while (compEnum.hasMoreElements()) {
            Object next = compEnum.nextElement();
            XComponent comp = unoQI(XComponent.class, next);
            XTextDocument doc = unoQI(XTextDocument.class, comp);
            if (doc != null) {
                result.add(doc);
            }
        }
        return result;
    }

    /**
     *  Run a dialog allowing the user to choose among the documents in `list`.
     *
     * @return Null if no document was selected. Otherwise the
     *         document selected.
     *
     */
    private static XTextDocument selectDocumentDialog(List<XTextDocument> list,
                                                      DialogService dialogService) {

        class DocumentTitleViewModel {

            private final XTextDocument xTextDocument;
            private final String description;

            public DocumentTitleViewModel(XTextDocument xTextDocument) {
                this.xTextDocument = xTextDocument;
                this.description = DocumentConnection.getDocumentTitle(xTextDocument).orElse("");
            }

            public XTextDocument getXtextDocument() {
                return xTextDocument;
            }

            @Override
            public String toString() {
                return description;
            }
        }

        List<DocumentTitleViewModel> viewModel = (list.stream()
                                                  .map(DocumentTitleViewModel::new)
                                                  .collect(Collectors.toList()));

        // This whole method is part of a background task when
        // auto-detecting instances, so we need to show dialog in FX
        // thread
        Optional<DocumentTitleViewModel> selectedDocument =
            (dialogService
             .showChoiceDialogAndWait(Localization.lang("Select document"),
                                      Localization.lang("Found documents:"),
                                      Localization.lang("Use selected document"),
                                      viewModel));

        return (selectedDocument
                .map(DocumentTitleViewModel::getXtextDocument)
                .orElse(null));
    }

    /**
     * Choose a document to work with.
     *
     * Assumes we have already connected to LibreOffice or OpenOffice.
     *
     * If there is a single document to choose from, selects that.
     * If there are more than one, shows selection dialog.
     * If there are none, throws NoDocumentException
     *
     * After successful selection connects to the selected document
     * and extracts some frequently used parts (starting points for
     * managing its content).
     *
     * Finally initializes this.xDocumentConnection with the selected
     * document and parts extracted.
     *
     */
    public void selectDocument()
        throws
        NoDocumentException,
        NoSuchElementException,
        WrappedTargetException {

        XTextDocument selected;
        List<XTextDocument> textDocumentList = getTextDocuments(this.xDesktop);
        if (textDocumentList.isEmpty()) {
            throw new NoDocumentException("No Writer documents found");
        } else if (textDocumentList.size() == 1) {
            selected = textDocumentList.get(0); // Get the only one
        } else { // Bring up a dialog
            selected = OOBibBase.selectDocumentDialog(textDocumentList,
                                                      this.dialogService);
        }

        if (selected == null) {
            return;
        }

        this.xDocumentConnection = new DocumentConnection(selected);
    }

    /**
     * Mark the current document as missing.
     *
     */
    private void forgetDocument() {
        this.xDocumentConnection = null;
    }

    /**
     * A simple test for document availability.
     *
     * See also `documentConnectionMissing` for a test
     * actually attempting to use teh connection.
     *
     */
    public boolean isConnectedToDocument() {
        return this.xDocumentConnection != null;
    }

    /**
     * @return true if we are connected to a document
     */
    public boolean documentConnectionMissing() {
        if (this.xDocumentConnection == null) {
            return true;
        }
        boolean res = this.xDocumentConnection.documentConnectionMissing();
        if (res) {
            forgetDocument();
        }
        return res;
    }

    /**
     * Either return a valid DocumentConnection or throw
     * NoDocumentException.
     */
    private DocumentConnection getDocumentConnectionOrThrow()
        throws
        NoDocumentException {
        if (documentConnectionMissing()) {
            throw new NoDocumentException("Not connected to document");
        }
        return this.xDocumentConnection;
    }

    /**
     *  The title of the current document, or Optional.empty()
     */
    public Optional<String> getCurrentDocumentTitle() {
        if (documentConnectionMissing()) {
            return Optional.empty();
        } else {
            return this.xDocumentConnection.getDocumentTitle();
        }
    }

    /* ****************************
     *
     *           Misc
     *
     * ****************************/

    /**
     * unoQI : short for UnoRuntime.queryInterface
     *
     * @return A reference to the requested UNO interface type if
     *         available, otherwise null.
     */
    private static <T> T unoQI(Class<T> zInterface,
                               Object object) {
        return UnoRuntime.queryInterface(zInterface, object);
    }

    /* ***************************************
     *
     *     Storage/retrieve of citations
     *
     *
     *  We store some information in the document about
     *
     *    Citation groups:
     *
     *       - citations belonging to the group.
     *       - Range of text owned (where the citation marks go).
     *       - pageInfo
     *
     *    Citations : citation key
     *        Each belongs to exactly one group.
     *
     *    From these, the databases and the style we create and update
     *    the presentation (citation marks)
     *
     *    How:
     *      database lookup yields: (BibEntry,whichDatabase)
     *                              (UndefinedBibtexEntry,null) if not found
     *
     *      Local order
     *          presentation order within groups from (style,BibEntry)
     *
     *      Global order:
     *          visualPosition (for first appearance order)
     *          bibliography-order
     *
     *      Make them unique
     *         numbering
     *         uniqueLetters from (Set<BibEntry>, firstAppearanceOrder, style)
     *
     *
     *  Bibliography uses parts of the information above:
     *      citation keys,
     *      location of citation groups (if ordered and/or numbered by first appearance)
     *
     *      and
     *      the range of text controlled (storage)
     *
     *      And fills the bibliography (presentation)
     *
     * **************************************/

    /**
     * @param requireSeparation Report range pairs that only share a boundary.
     * @param reportAtMost Limit number of overlaps reported (0 for no limit)
     */
    public void checkRangeOverlaps(CitationGroups cgs,
                                   DocumentConnection documentConnection,
                                   boolean requireSeparation,
                                   int reportAtMost)
        throws
        NoDocumentException,
        WrappedTargetException,
        JabRefException {

        final boolean debugPartitions = false;

        List<RangeForOverlapCheck> xs = cgs.citationRanges(documentConnection);
        xs.addAll(cgs.footnoteMarkRanges(documentConnection));

        RangeKeyedMapList<RangeForOverlapCheck> xall = new RangeKeyedMapList<>();
        for (RangeForOverlapCheck x : xs) {
            XTextRange key = x.range;
            xall.add(key, x);
        }

        List<RangeKeyedMapList<RangeForOverlapCheck>.RangeOverlap> ovs =
            xall.findOverlappingRanges(reportAtMost, requireSeparation);

        // checkSortedPartitionForOverlap(requireSeparation, oxs);
        if (ovs.size() > 0) {
            String msg = "";
            for (RangeKeyedMapList<RangeForOverlapCheck>.RangeOverlap e : ovs) {
                String l = (": "
                            + (e.vs.stream()
                               .map(v -> String.format("'%s'", v.format()))
                               .collect(Collectors.joining(", ")))
                            + "\n");

                switch (e.kind) {
                case EQUAL_RANGE: msg = msg + Localization.lang("Found identical ranges") + l;
                    break;
                case OVERLAP: msg = msg + Localization.lang("Found overlapping ranges") + l;
                    break;
                case TOUCH: msg = msg + Localization.lang("Found touching ranges") + l;
                    break;
                }
            }
            throw new JabRefException("Found overlapping or touching ranges", msg);
        }
    }

    /**
     * GUI: Get a list of CitationEntry objects corresponding to citations
     * in the document.
     *
     * Called from: ManageCitationsDialogViewModel constructor.
     *
     * @return A list with entries corresponding to citations in the
     *         text, in arbitrary order (same order as from
     *         getJabRefReferenceMarkNames).
     *
     *               Note: visual or alphabetic order could be more
     *               manageable for the user. We could provide these
     *               here, but switching between them needs change on
     *               GUI (adding a toggle or selector).
     *
     *               Note: CitationEntry implements Comparable, where
     *                     compareTo() and equals() are based on refMarkName.
     *                     The order used in the "Manage citations" dialog
     *                     does not seem to use that.
     *
     *                     The 1st is labeled "Citation" (show citation in bold,
     *                     and some context around it).
     *
     *                     The columns can be sorted by clicking on the column title.
     *                     For the "Citation" column, the sorting is based on the content,
     *                     (the context before the citation), not on the citation itself.
     *
     *                     In the "Extra information ..." column some visual indication
     *                     of the editable part could be helpful.
     *
     *         Wish: selecting an entry (or a button in the line) in
     *               the GUI could move the cursor in the document to
     *               the entry.
     */
    public List<CitationEntry> getCitationEntries()
        throws
        UnknownPropertyException,
        WrappedTargetException,
        NoDocumentException,
        CreationException {

        DocumentConnection documentConnection = this.getDocumentConnectionOrThrow();
        CitationGroups cgs = new CitationGroups(documentConnection);

        int n = cgs.numberOfCitationGroups();
        List<CitationEntry> citations = new ArrayList<>(n);
        for (CitationGroupID cgid : cgs.getCitationGroupIDs()) {
            String name = cgid.asString();
            String context = this.getCitationContext(cgs, cgid, documentConnection, 30, 30, true);
            CitationEntry entry = new CitationEntry(name,
                                                    context,
                                                    cgs.getPageInfo(cgid));
            citations.add(entry);
        }
        return citations;
    }

    /**
     *  Get the text belonging to refMarkName with up to
     *  charBefore and charAfter characters of context.
     *
     *  The actual context may be smaller than requested.
     *
     *  @param cgs
     *  @param cgid
     *  @param documentConnection
     *  @param charBefore Number of characters requested.
     *  @param charAfter  Number of characters requested.
     *  @param htmlMarkup If true, the text belonging to the
     *                    reference mark is surrounded by bold html tag.
     */
    private String getCitationContext(CitationGroups cgs,
                                      CitationGroupID cgid,
                                      DocumentConnection documentConnection,
                                      int charBefore,
                                      int charAfter,
                                      boolean htmlMarkup)
        throws
        WrappedTargetException,
        NoDocumentException,
        CreationException {

        XTextCursor cursor = (cgs
                              .getRawCursorForCitationGroup(cgid, documentConnection)
                              .orElseThrow(RuntimeException::new));

        String citPart = cursor.getString();

        // extend cursor range left
        int flex = 8;
        for (int i = 0; i < charBefore; i++) {
            try {
                cursor.goLeft((short) 1, true);
                // If we are close to charBefore and see a space,
                // then cut here. Might avoid cutting a word in half.
                if ((i >= (charBefore - flex))
                    && Character.isWhitespace(cursor.getString().charAt(0))) {
                    break;
                }
            } catch (IndexOutOfBoundsException ex) {
                LOGGER.warn("Problem going left", ex);
            }
        }

        int lengthWithBefore = cursor.getString().length();
        int addedBefore = lengthWithBefore - citPart.length();

        cursor.collapseToStart();
        for (int i = 0; i < (charAfter + lengthWithBefore); i++) {
            try {
                cursor.goRight((short) 1, true);
                if (i >= ((charAfter + lengthWithBefore) - flex)) {
                    String strNow = cursor.getString();
                    if (Character.isWhitespace(strNow.charAt(strNow.length() - 1))) {
                        break;
                    }
                }
            } catch (IndexOutOfBoundsException ex) {
                LOGGER.warn("Problem going right", ex);
            }
        }

        String result = cursor.getString();
        if (htmlMarkup) {
            result = (result.substring(0, addedBefore)
                      + "<b>" + citPart + "</b>"
                      + result.substring(lengthWithBefore));
        }
        return result.trim();
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
        NoDocumentException {

        DocumentConnection documentConnection = getDocumentConnectionOrThrow();

        for (CitationEntry entry : citationEntries) {
            Optional<String> pageInfo = entry.getPageInfo();
            if (pageInfo.isPresent()) {
                documentConnection.setCustomProperty(entry.getRefMarkName(),
                                                     pageInfo.get());
            }
        }
    }

    /* ***************************************
     *
     *     Local order: Presentation order within citation groups
     *
     * **************************************/

    /**
     *  The comparator used to sort within a group of merged
     *  citations.
     *
     *  The term used here is "multicite". The option controlling the
     *  order is "MultiCiteChronological" in style files.
     *
     *  Yes, they are always sorted one way or another.
     */
    private Comparator<BibEntry> comparatorForMulticite(OOBibStyle style) {
        if (style.getMultiCiteChronological()) {
            return OOBibBase.YEAR_AUTHOR_TITLE_COMPARATOR;
        } else {
            return OOBibBase.AUTHOR_YEAR_TITLE_COMPARATOR;
        }
    }

    /* ***************************************
     *
     *     Global order: by first appearance or by bibliography order
     *
     * **************************************/

    /* bibliography order */

    /**
     *  Return a TreeMap(entryComparator) copy of entries.
     *
     *  For sorting the bibliography.
     */
    SortedMap<BibEntry, BibDatabase> sortEntriesByComparator(Map<BibEntry, BibDatabase> entries,
                                                             Comparator<BibEntry> entryComparator) {

        SortedMap<BibEntry, BibDatabase> newMap = new TreeMap<>(entryComparator);
        for (Map.Entry<BibEntry, BibDatabase> kv : entries.entrySet()) {
            newMap.put(kv.getKey(),
                       kv.getValue());
        }
        return newMap;
    }

    /* ***************************************
     *
     *     Make them unique: uniqueLetters or numbers
     *
     * **************************************/

    private String normalizedCitationMarkerForNormalStyle(CitedKey ck,
                                                          OOBibStyle style) {
        if (ck.db.isEmpty()) {
            return String.format("(Unresolved(%s))", ck.citationKey);
        }
        // We need "normalized" (in parenthesis) markers
        // for uniqueness checking purposes:
        // createNormalizedCitationMarker
        CitationMarkerEntry ce = new CitationMarkerEntryImpl(ck.citationKey,
                                                             ck.db.map(e -> e.entry),
                                                             ck.db.map(e -> e.database),
                                                             Optional.empty(), // uniqueLetter
                                                             Optional.empty(), // pageInfo
                                                             false /* isFirstAppearanceOfSource */);
        return style.getNormalizedCitationMarker(ce);
    }

    /**
     *  Fills {@code sortedCitedKeys//normCitMarker}
     */
    private void
    createNormalizedCitationMarkersForNormalStyle(CitedKeys sortedCitedKeys,
                                                  OOBibStyle style) {

        for (CitedKey ck : sortedCitedKeys.data.values()) {
            ck.normCitMarker = Optional.of(normalizedCitationMarkerForNormalStyle(ck, style));
        }
    }

    /**
     *  For each cited source make the citation keys unique by setting
     *  the uniqueLetter fields to letters ("a", "b") or Optional.empty()
     *
     * precondition: sortedCitedKeys already has normalized citation markers.
     * precondition: sortedCitedKeys is sorted (according to the order we want the letters to be assigned)
     *
     * Expects to see data for all cited sources here.
     * Clears uniqueLetters before filling.
     *
     * On return: Each citedKey in sortedCitedKeys has uniqueLetter set as needed.
     *            The same values are copied to the corresponding citations in cgs.
     *
     *  Depends on: style, citations and their order.
     */
    void createUniqueLetters(CitedKeys sortedCitedKeys,
                             CitationGroups cgs) {

        // ncm2clks: ncm (normCitMarker) to clks (clashing keys : list of citation keys fighting for it).
        //
        //          The entries in the clks lists preserve firstAppearance order
        //          from sortedCitedKeys.data.values().
        //
        //          The index of the citationKey in this order will decide which
        //          unique letter it receives.
        //
        Map<String, List<String>> ncm2clks = new HashMap<>();
        for (CitedKey ck : sortedCitedKeys.values()) {
            String ncm = ck.normCitMarker.get();
            String citationKey = ck.citationKey;

            if (!ncm2clks.containsKey(ncm)) {
                // Found new normCitMarker
                List<String> clks = new ArrayList<>(1);
                ncm2clks.put(ncm, clks);
                clks.add(citationKey);
            } else {
                List<String> clks = ncm2clks.get(ncm);
                if (!clks.contains(citationKey)) {
                    // First appearance of citationKey, add to list.
                    clks.add(citationKey);
                }
            }
        }

        // Clear old uniqueLetter values.
        for (CitedKey ck : sortedCitedKeys.data.values()) {
            ck.uniqueLetter = Optional.empty();
        }

        // For sets of citation keys figthing for a normCitMarker
        // add unique letters to the year.
        for (List<String> clks : ncm2clks.values()) {
            if (clks.size() <= 1) {
                continue; // No fight, no letters.
            }
            // Multiple citation keys: they get their letters according to their order in clks.
            int nextUniqueLetter = 'a';
            for (String citationKey : clks) {
                // uniqueLetters.put(citationKey, String.valueOf((char) nextUniqueLetter));
                String ul = String.valueOf((char) nextUniqueLetter);
                sortedCitedKeys.data.get(citationKey).uniqueLetter = Optional.of(ul);
                nextUniqueLetter++;
            }
        }
        sortedCitedKeys.distributeUniqueLetters(cgs);
    }

    /* ***************************************
     *
     *     Calculate presentation of citation groups
     *     (create citMarkers)
     *
     * **************************************/

    /**
     * Given the withText and inParenthesis options,
     * return the corresponding itcType.
     *
     * @param withText False means invisible citation (no text).
     * @param inParenthesis True means "(Au and Thor 2000)".
     *                      False means "Au and Thor (2000)".
     */
    private static int citationTypeFromOptions(boolean withText, boolean inParenthesis) {
        if (!withText) {
            return OOBibBase.INVISIBLE_CIT;
        }
        return (inParenthesis
                ? OOBibBase.AUTHORYEAR_PAR
                : OOBibBase.AUTHORYEAR_INTEXT);
    }

    /**
     *  Produce citation markers for the case when the citation
     *  markers are the citation keys themselves, separated by commas.
     */
    private static Map<CitationGroupID, String>
    produceCitationMarkersForIsCitationKeyCiteMarkers(CitationGroups cgs,
                                                      OOBibStyle style) {

        assert style.isCitationKeyCiteMarkers();

        cgs.createPlainBibliographySortedByComparator(OOBibBase.AUTHOR_YEAR_TITLE_COMPARATOR);

        Map<CitationGroupID, String> citMarkers = new HashMap<>();

        for (CitationGroupID cgid : cgs.getSortedCitationGroupIDs()) {
            List<Citation> cits = cgs.getSortedCitations(cgid);
            String citMarker =
                style.getCitationGroupMarkupBefore()
                + (cits.stream()
                   .map(cit -> cit.citationKey)
                   .collect(Collectors.joining(",")))
                + style.getCitationGroupMarkupAfter();
            citMarkers.put(cgid, citMarker);
        }
        return citMarkers;
    }

    /**
     * Produce citation markers for the case of numbered citations
     * with bibliography sorted by first appearance in the text.
     *
     * @param cgs
     * @param style
     *
     * @return Numbered citation markers for each CitationGroupID.
     *         Numbering is according to first appearance.
     *         Assumes global order and local order ae already applied.
     *
     */
    private static Map<CitationGroupID, String>
    produceCitationMarkersForIsNumberEntriesIsSortByPosition(CitationGroups cgs,
                                                             OOBibStyle style) {

        assert style.isNumberEntries();
        assert style.isSortByPosition();

        cgs.createNumberedBibliographySortedInOrderOfAppearance();

        final int minGroupingCount = style.getMinimumGroupingCount();

        Map<CitationGroupID, String> citMarkers = new HashMap<>();

        for (CitationGroupID cgid : cgs.getSortedCitationGroupIDs()) {
            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
            List<Integer> numbers = cg.getSortedNumbers();
            List<String> pageInfos = cgs.backend.getPageInfosForCitations(cg);
            citMarkers.put(cgid,
                           style.getNumCitationMarker(numbers,
                                                      minGroupingCount,
                                                      pageInfos));
        }

        return citMarkers;
    }

    /**
     * Produce citation markers for the case of numbered citations
     * when the bibliography is not sorted by position.
     */
    private Map<CitationGroupID, String>
    produceCitationMarkersForIsNumberEntriesNotSortByPosition(CitationGroups cgs,
                                                              OOBibStyle style) {
        assert style.isNumberEntries();
        assert !style.isSortByPosition();

        cgs.createNumberedBibliographySortedByComparator(OOBibBase.AUTHOR_YEAR_TITLE_COMPARATOR);

        final int minGroupingCount = style.getMinimumGroupingCount();

        Map<CitationGroupID, String> citMarkers = new HashMap<>();

        for (CitationGroupID cgid : cgs.getSortedCitationGroupIDs()) {
            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
            List<Integer> numbers = cg.getSortedNumbers();
            List<String> pageInfos = cgs.backend.getPageInfosForCitations(cg);
            citMarkers.put(cgid,
                           style.getNumCitationMarker(numbers,
                                                      minGroupingCount,
                                                      pageInfos));
        }
        return citMarkers;
    }

    /**
     * Produce citMarkers for normal
     * (!isCitationKeyCiteMarkers &amp;&amp; !isNumberEntries) styles.
     *
     * @param cgs
     * @param style              Bibliography style.
     */
    private Map<CitationGroupID, String> produceCitationMarkersForNormalStyle(CitationGroups cgs,
                                                                              OOBibStyle style) {

        assert !style.isCitationKeyCiteMarkers();
        assert !style.isNumberEntries();
        // Citations in (Au1, Au2 2000) form

        CitedKeys sortedCitedKeys = cgs.getCitedKeysSortedInOrderOfAppearance();

        createNormalizedCitationMarkersForNormalStyle(sortedCitedKeys, style);
        createUniqueLetters(sortedCitedKeys, cgs); // calls distributeUniqueLetters(cgs)
        cgs.createPlainBibliographySortedByComparator(OOBibBase.AUTHOR_YEAR_TITLE_COMPARATOR);

        // Finally, go through all citation markers, and update
        // those referring to entries in our current list:
        final int maxAuthorsFirst = style.getMaxAuthorsFirst();

        Set<String> seenBefore = new HashSet<>();

        Map<CitationGroupID, String> citMarkers = new HashMap<>();

        for (CitationGroupID cgid : cgs.getSortedCitationGroupIDs()) {
            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
            List<Citation> cits = cg.getSortedCitations();
            final int nCitedEntries = cits.size();
            List<String> pageInfosForCitations = cgs.backend.getPageInfosForCitations(cg);

            List<CitationMarkerEntry> citationMarkerEntries = new ArrayList<>(nCitedEntries);

            boolean hasUnresolved = false;
            for (int j = 0; j < nCitedEntries; j++) {
                Citation cit = cits.get(j);
                String currentKey = cit.citationKey;
                boolean isFirst = false;
                if (!seenBefore.contains(currentKey)) {
                    isFirst = true;
                    seenBefore.add(currentKey);
                }
                Optional<String> uniqueLetterForKey = cit.uniqueLetter;
                if (cit.db.isEmpty()) {
                    hasUnresolved = true;
                }
                Optional<BibDatabase> database = cit.db.map(e -> e.database);
                Optional<BibEntry> bibEntry = cit.db.map(e -> e.entry);

                CitationMarkerEntry cm =
                    new CitationMarkerEntryImpl(currentKey,
                                                bibEntry,
                                                database,
                                                uniqueLetterForKey,
                                                Optional.ofNullable(pageInfosForCitations.get(j)),
                                                isFirst);
                citationMarkerEntries.add(cm);
            }

            // TODO: Now we can pass CitationMarkerEntry values with unresolved
            //       keys to style.getCitationMarker,
            //       maybe the fall back to ungrouped citations here is
            //       not needed anymore.

            if (hasUnresolved) {
                /*
                 * Some entries are unresolved.
                 */
                String s = "";
                for (int j = 0; j < nCitedEntries; j++) {

                    CitationMarkerEntry cm = citationMarkerEntries.get(j);
                    if (cm.getBibEntry().isPresent()) {
                        s = (s
                             + style.getCitationMarker(citationMarkerEntries.subList(j, j + 1),
                                                       cg.itcType == OOBibBase.AUTHORYEAR_PAR,
                                                       OOBibStyle.NonUniqueCitationMarker.THROWS));
                    } else {
                        s = s + String.format("(Unresolved(%s))", cm.getCitationKey());
                    }
                }
                citMarkers.put(cgid, s);
            } else {
                /*
                 * All entries are resolved.
                 */
                String citMarker = style.getCitationMarker(citationMarkerEntries,
                                                           cg.itcType == OOBibBase.AUTHORYEAR_PAR,
                                                           OOBibStyle.NonUniqueCitationMarker.THROWS);
                citMarkers.put(cgid, citMarker);
            }
        }

        return citMarkers;
    }

    private static void fillCitationMarkInCursor(DocumentConnection documentConnection,
                                                 CitationGroups cgs,
                                                 CitationGroupID cgid,
                                                 XTextCursor cursor,
                                                 String citationText,
                                                 boolean withText,
                                                 OOBibStyle style)
        throws
        UnknownPropertyException,
        WrappedTargetException,
        PropertyVetoException,
        IllegalArgumentException,
        UndefinedCharacterFormatException {

        Objects.requireNonNull(documentConnection);
        Objects.requireNonNull(cgs);
        Objects.requireNonNull(cgid);
        Objects.requireNonNull(cursor);
        Objects.requireNonNull(citationText);
        Objects.requireNonNull(style);

        if (withText) {
            OOUtil.insertOOFormattedTextAtCurrentLocation(cursor, citationText);
            DocumentConnection.setCharLocaleNone(cursor);
            if (style.isFormatCitations()) {
                String charStyle = style.getCitationCharacterFormat();
                DocumentConnection.setCharStyle(cursor, charStyle);
            }
        } else {
            cursor.setString("");
        }
    }

    /**
     *  Inserts a citation group in the document: creates and fills it.
     *
     * @param cgs
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
    private void createAndFillCitationGroup(CitationGroups cgs,
                                            DocumentConnection documentConnection,
                                            List<String> citationKeys,
                                            List<String> pageInfosForCitations,
                                            int itcType,
                                            String citationText,
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
        IllegalTypeException {

        CitationGroupID cgid = cgs.createCitationGroup(documentConnection,
                                                       citationKeys,
                                                       pageInfosForCitations,
                                                       itcType,
                                                       position,
                                                       insertSpaceAfter,
                                                       !withText /* withoutBrackets */);

        if (withText) {
            XTextCursor c2 = cgs.getFillCursorForCitationGroup(documentConnection,
                                                               cgid);

            fillCitationMarkInCursor(documentConnection,
                                     cgs,
                                     cgid,
                                     c2,
                                     citationText,
                                     withText,
                                     style);

            cgs.cleanFillCursorForCitationGroup(documentConnection, cgid);
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
        CitationGroups cgs = new CitationGroups(documentConnection);
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

            int itcType = citationTypeFromOptions(withText, inParenthesis);

            assertCitationCharacterFormatIsOK(cursor, style);

            // JabRef53 style pageInfo list
            List<String> pageInfosForCitations = Backend52.fakePageInfosForCitations(pageInfo, nEntries);

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
            String citeText = (style.isNumberEntries()
                               ? "[-]" // A dash only. Only refresh later.
                               : style.getCitationMarker(citationMarkerEntries,
                                                         inParenthesis,
                                                         OOBibStyle.NonUniqueCitationMarker.FORGIVEN));

            if (citeText.equals("")) {
                citeText = "[?]";
            }

            createAndFillCitationGroup(cgs,
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
                ProduceCitationMarkersResult x =
                    produceCitationMarkers(documentConnection, allBases, style);
                try {
                    documentConnection.lockControllers();
                    applyNewCitationMarkers(documentConnection,
                                            x.cgs,
                                            x.citMarkers,
                                            style);
                    // Insert it at the current position:
                    rebuildBibTextSection(documentConnection,
                                          style,
                                          x.cgs,
                                          x.getBibliography());
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
     * @param cgs
     *
     * @param citMarkers Corresponding text for each reference mark,
     *                   that replaces the old text.
     *
     * @param style Bibliography style to use.
     *
     */
    private void applyNewCitationMarkers(DocumentConnection documentConnection,
                                         CitationGroups cgs,
                                         Map<CitationGroupID, String> citMarkers,
                                         OOBibStyle style)
        throws
        NoDocumentException,
        UndefinedCharacterFormatException,
        UnknownPropertyException,
        CreationException,
        WrappedTargetException,
        PropertyVetoException {

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

        for (Map.Entry<CitationGroupID, String> kv : citMarkers.entrySet()) {

            CitationGroupID cgid = kv.getKey();
            Objects.requireNonNull(cgid);

            String citationText = kv.getValue();
            Objects.requireNonNull(citationText);

            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);

            boolean withText = (cg.itcType != OOBibBase.INVISIBLE_CIT);

            if (withText) {

                XTextCursor cursor = cgs.getFillCursorForCitationGroup(documentConnection,
                                                                       cgid);

                if (mustTestCharFormat) {
                    assertCitationCharacterFormatIsOK(cursor, style);
                    mustTestCharFormat = false;
                }

                fillCitationMarkInCursor(documentConnection,
                                         cgs,
                                         cgid,
                                         cursor,
                                         citationText,
                                         withText,
                                         style);

                cgs.cleanFillCursorForCitationGroup(documentConnection, cgid);
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

    /**
     * The main field is citMarkers, the rest is for reuse in caller.
     */
    static class ProduceCitationMarkersResult {

        CitationGroups cgs;

        /** citation markers */
        Map<CitationGroupID, String> citMarkers;

        ProduceCitationMarkersResult(CitationGroups cgs,
                                     Map<CitationGroupID, String> citMarkers) {
            this.cgs = cgs;
            this.citMarkers = citMarkers;
            if (cgs.getBibliography().isEmpty()) {
                throw new RuntimeException("ProduceCitationMarkersResult.constructor:"
                                           + " cgs does not have a bibliography");
            }
        }

        public CitedKeys getBibliography() {
            if (cgs.getBibliography().isEmpty()) {
                throw new RuntimeException("ProduceCitationMarkersResult.getBibliography:"
                                           + " cgs does not have a bibliography");
            }
            return cgs.getBibliography().get();
        }

        public List<String> getUnresolvedKeys() {
            CitedKeys bib = getBibliography();
            List<String> unresolvedKeys = new ArrayList<>();
            for (CitedKey ck : bib.values()) {
                if (ck.db.isEmpty()) {
                    unresolvedKeys.add(ck.citationKey);
                }
            }
            return unresolvedKeys;
        }
    }

    private ProduceCitationMarkersResult produceCitationMarkers(DocumentConnection documentConnection,
                                                                List<BibDatabase> databases,
                                                                OOBibStyle style)
        throws
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException,
        NoDocumentException,
        UnknownPropertyException,
        JabRefException {

        CitationGroups cgs = new CitationGroups(documentConnection);

        cgs.lookupEntriesInDatabases(databases);

        // requires cgs.lookupEntryInDatabases: needs BibEntry data
        cgs.imposeLocalOrderByComparator(comparatorForMulticite(style));

        // Normally we sort the reference marks according to their
        // order of appearance.
        //
        // This only depends on location of ranges and footnote marks
        // in the text. Does not touch localOrder, only order between
        // reference marks.
        //
        // Question: is there a case when we do not need order-of-appearance?
        //
        // style.isCitationKeyCiteMarkers() : ???
        // style.isNumberEntries() && style.isSortByPosition() :
        //             needs order-of-appearance for numbering
        // style.isNumberEntries() && !style.isSortByPosition() : ???
        // produceCitationMarkersForNormalStyle : needs order-of-appearance for uniqueLetters
        //
        boolean mapFootnotesToFootnoteMarks = true;
        List<CitationGroupID> sortedCitationGroupIDs =
            cgs.getVisuallySortedCitationGroupIDs(documentConnection,
                                                  mapFootnotesToFootnoteMarks);
        cgs.setGlobalOrder(sortedCitationGroupIDs);
        // localOrder and globalOrder together gives us order-of-appearance of citations

        Map<CitationGroupID, String> citMarkers;

        // fill citMarkers
        Map<String, String> uniqueLetters = new HashMap<>();

        if (style.isCitationKeyCiteMarkers()) {
            citMarkers = produceCitationMarkersForIsCitationKeyCiteMarkers(cgs, style);
        } else if (style.isNumberEntries()) {
            if (style.isSortByPosition()) {
                citMarkers = produceCitationMarkersForIsNumberEntriesIsSortByPosition(cgs, style);
            } else {
                citMarkers = produceCitationMarkersForIsNumberEntriesNotSortByPosition(cgs, style);
            }
        } else {
            /* Normal case, (!isCitationKeyCiteMarkers && !isNumberEntries) */
            citMarkers = produceCitationMarkersForNormalStyle(cgs, style);
        }

        return new ProduceCitationMarkersResult(cgs, /* has bibliography as a side effect */
                                                citMarkers);
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
                                       CitationGroups cgs,
                                       CitedKeys bibliography)
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
                               cgs,
                               bibliography,
                               style);
    }

    /**
     * Insert body of bibliography at `cursor`.
     *
     * @param documentConnection Connection.
     * @param cursor  Where to
     * @param cgs
     * @param bibliography
     * @param style Style.
     *
     * Only called from populateBibTextSection (and that from rebuildBibTextSection)
     */
    private void insertFullReferenceAtCursor(DocumentConnection documentConnection,
                                             XTextCursor cursor,
                                             CitationGroups cgs,
                                             CitedKeys bibliography,
                                             OOBibStyle style)
        throws
        IllegalArgumentException,
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        CreationException {

        final boolean debugThisFun = false;

        if (debugThisFun) {
            System.out.printf("Ref IsSortByPosition %s\n", style.isSortByPosition());
            System.out.printf("Ref IsNumberEntries  %s\n", style.isNumberEntries());
        }

        String parStyle = style.getReferenceParagraphFormat();

        for (CitedKey ck : bibliography.values()) {

            if (debugThisFun) {
                System.out.printf("Ref cit %-20s ck.number %7s%n",
                                  String.format("'%s'", ck.citationKey),
                                  (ck.number.isEmpty()
                                   ? "(empty)"
                                   : String.format("%02d", ck.number.get())));
            }

            // this is where we create the paragraph.
            OOUtil.insertParagraphBreak(documentConnection.xText, cursor);
                // format the paragraph
                try {
                    if (parStyle != null) {
                        DocumentConnection.setParagraphStyle(cursor,
                                                             parStyle);
                    }
                } catch (UndefinedParagraphFormatException ex) {
                    // TODO: precheck or remember if we already emitted this message.
                    String message =
                        String.format("Could not apply paragraph format '%s' to bibliography entry",
                                      parStyle);
                    LOGGER.warn(message); // no stack trace
                }

            // insert marker "[1]"
            if (style.isNumberEntries()) {

                if (ck.number.isEmpty()) {
                    throw new RuntimeException("insertFullReferenceAtCursor:"
                                               + " numbered style, but found unnumbered entry");
                }

                int number = ck.number.get();
                String marker = style.getNumCitationMarkerForBibliography(number);
                OOUtil.insertOOFormattedTextAtCurrentLocation(cursor, marker);
                cursor.collapseToEnd();
            } else {
                // !style.isNumberEntries() : emit no prefix
                // TODO: We might want [citationKey] prefix for style.isCitationKeyCiteMarkers();
            }

            if (ck.db.isEmpty()) {
                // Unresolved entry
                String referenceDetails = String.format("Unresolved(%s)", ck.citationKey);
                OOUtil.insertOOFormattedTextAtCurrentLocation(cursor, referenceDetails);
                cursor.collapseToEnd();
                // Try to list citations:
                if (true) {
                    String prefix = String.format(" (%s: ", Localization.lang("Cited on pages"));
                    String suffix = ")";
                    OOUtil.insertTextAtCurrentLocation(documentConnection.xText,
                                                       cursor,
                                                       prefix,
                                                       Collections.emptyList());

                    int last = ck.where.size();
                    int i = 0;
                    for (CitationPath p : ck.where) {
                        CitationGroupID cgid = p.group;
                        CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
                        String refMarkName = cg.referenceMarkName;

                        if (i > 0) {
                            OOUtil.insertTextAtCurrentLocation(documentConnection.xText,
                                                               cursor,
                                                               String.format(", "),
                                                               Collections.emptyList());
                        }
                        documentConnection
                            .insertGetreferenceToPageNumberOfReferenceMark(refMarkName, cursor);
                        i++;
                    }
                    documentConnection.refresh();

                    OOUtil.insertTextAtCurrentLocation(documentConnection.xText,
                                                       cursor,
                                                       suffix,
                                                       Collections.emptyList());
                }

            } else {
                // Resolved entry
                BibEntry bibentry = ck.db.get().entry;

                // insert the actual details.
                Layout layout = style.getReferenceFormat(bibentry.getType());
                layout.setPostFormatter(POSTFORMATTER);

                String formattedText = OOUtil.formatFullReference(layout,
                                                                  bibentry,
                                                                  ck.db.get().database,
                                                                  ck.uniqueLetter.orElse(null));

                // Insert the formatted text:
                OOUtil.insertOOFormattedTextAtCurrentLocation(cursor, formattedText);
                cursor.collapseToEnd();
            }
        }
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

        // Always creating at the end of documentConnection.xText
        // Alternatively, we could receive a cursor.
        XTextCursor textCursor = documentConnection.xText.createTextCursor();
        textCursor.gotoEnd(false);

        OOUtil.insertParagraphBreak(documentConnection.xText, textCursor);

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

            XTextCursor cursor = documentConnection.xText.createTextCursorByRange(section.getAnchor());

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
                                        CitationGroups cgs,
                                        CitedKeys bibliography,
                                        OOBibStyle style)
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

        XTextCursor cursor = (documentConnection.xText
                              .createTextCursorByRange(section.getAnchor()));

        // emit the title of the bibliography
        OOUtil.insertOOFormattedTextAtCurrentLocation(cursor, style.getReferenceHeaderText());
        String parStyle = style.getReferenceHeaderParagraphFormat();
        try {
            if (parStyle != null) {
                DocumentConnection.setParagraphStyle(cursor, parStyle);
            }
        } catch (UndefinedParagraphFormatException ex) {
            String message =
                String.format("Could not apply paragraph format '%s' to bibliography header",
                              parStyle);
            LOGGER.warn(message); // No stack trace.
        }
        cursor.collapseToEnd();

        // emit body
        insertFullReferenceAtCursor(documentConnection,
                                    cursor,
                                    cgs,
                                    bibliography,
                                    style);

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
        CitationGroups cgs = new CitationGroups(documentConnection);

        try {
            documentConnection.enterUndoContext("Merge citations");

            boolean madeModifications = false;

            List<CitationGroupID> referenceMarkNames =
                cgs.getCitationGroupIDsSortedWithinPartitions(documentConnection,
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
                // List<Integer> itcTypes = new ArrayList<>();

                if (referenceMarkNames.size() > 0) {
                    // current group of CitationGroup values
                    List<CitationGroup> currentGroup = new ArrayList<>();
                    XTextCursor currentGroupCursor = null;
                    XTextCursor cursorBetween = null;
                    CitationGroup prev = null;
                    XTextRange prevRange = null;

                    for (CitationGroupID cgid : referenceMarkNames) {
                        CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);

                        XTextRange currentRange = (cgs
                                                   .getMarkRange(documentConnection, cgid)
                                                   .orElseThrow(RuntimeException::new));

                        boolean addToGroup = true;
                        /*
                         * Decide if we add cg to the group
                         */

                        // Only combine (Author 2000) type citations
                        if (cg.itcType != OOBibBase.AUTHORYEAR_PAR
                            // allow "Author (2000)"
                            // && itcTypes[i] != OOBibBase.AUTHORYEAR_INTEXT
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
                        boolean canStartGroup = (cg.itcType == OOBibBase.AUTHORYEAR_PAR);

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

                    int itcType = joinableGroup.get(0).itcType;

                    // cgPageInfos belong to the CitationGroup (DataModel JabRef52),
                    // but it is not clear how should we handle them here.
                    // We delegate the problem to the backend.
                    List<String> pageInfosForCitations =
                        cgs.backend.combinePageInfos(joinableGroup);

                    // Remove the old citation groups from the document.
                    for (int gj = 0; gj < joinableGroup.size(); gj++) {
                        cgs.removeCitationGroups(joinableGroup, documentConnection);
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
                    createAndFillCitationGroup(cgs,
                                               documentConnection,
                                               citationKeys,
                                               pageInfosForCitations,
                                               itcType, // OOBibBase.AUTHORYEAR_PAR
                                               "tmp",
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
                ProduceCitationMarkersResult x = produceCitationMarkers(documentConnection,
                                                                        databases,
                                                                        style);
                try {
                    if (useLockControllers) {
                        documentConnection.lockControllers();
                    }
                    applyNewCitationMarkers(documentConnection,
                                            x.cgs,
                                            x.citMarkers,
                                            style);
                } finally {
                    if (useLockControllers) {
                        documentConnection.unlockControllers();
                    }
                }
            }
        } finally {
            documentConnection.leaveUndoContext();
        }
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
        CitationGroups cgs = new CitationGroups(documentConnection);

        try {
            documentConnection.enterUndoContext("Separate citations");
            boolean madeModifications = false;

            // {@code names} does not need to be sorted.
            List<CitationGroupID> names = new ArrayList<>(cgs.getCitationGroupIDs());

            try {
                if (useLockControllers) {
                    documentConnection.lockControllers();
                }

                int pivot = 0;
                boolean setCharStyleTested = false;

                while (pivot < (names.size())) {
                    CitationGroupID cgid = names.get(pivot);
                    CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);
                    XTextRange range1 = (cgs
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
                    List<String> pageInfosForCitations = cgs.backend.getPageInfosForCitations(cg);

                    List<Citation> cits = cg.citations;
                    if (cits.size() <= 1) {
                        pivot++;
                        continue;
                    }

                    List<String> keys =
                        cits.stream().map(cit -> cit.citationKey).collect(Collectors.toList());

                    cgs.removeCitationGroup(cg, documentConnection);

                    // Now we own the content of cits

                    // Insert mark for each key
                    final int last = keys.size() - 1;
                    for (int i = 0; i < keys.size(); i++) {
                        // Note: by using createAndFillCitationGroup (and not something
                        //       that accepts List<Citation>, we lose the extra
                        //       info stored in the citations.
                        //       We just reread below.

                        boolean insertSpaceAfter = (i != last);
                        createAndFillCitationGroup(cgs,
                                                   documentConnection,
                                                   keys.subList(i, i + 1), // citationKeys,
                                                   pageInfosForCitations.subList(i, i + 1), // pageInfos,
                                                   OOBibBase.AUTHORYEAR_PAR, // itcType,
                                                   "tmp",
                                                   textCursor,
                                                   true, /* withText.
                                                          * Should be itcType != OOBibBase.INVISIBLE_CIT */
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
                ProduceCitationMarkersResult x =
                    produceCitationMarkers(documentConnection, databases, style);
                try {
                    if (useLockControllers) {
                        documentConnection.lockControllers();
                    }
                    applyNewCitationMarkers(documentConnection, x.cgs, x.citMarkers, style);
                } finally {
                    if (useLockControllers) {
                        documentConnection.unlockControllers();
                    }
                }
            }
        } finally {
            documentConnection.leaveUndoContext();
        }
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

        CitationGroups cgs = new CitationGroups(documentConnection);
        CitedKeys cks = cgs.getCitedKeys();
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

        try {
            documentConnection.enterUndoContext("Refresh bibliography");

            boolean requireSeparation = false;
            CitationGroups cgs = new CitationGroups(documentConnection);

            // Check Range overlaps
            int maxReportedOverlaps = 10;
            checkRangeOverlaps(cgs, this.xDocumentConnection, requireSeparation, maxReportedOverlaps);

            final boolean useLockControllers = true;
            ProduceCitationMarkersResult x = produceCitationMarkers(documentConnection,
                                                                    databases,
                                                                    style);
            try {
                if (useLockControllers) {
                    documentConnection.lockControllers();
                }
                applyNewCitationMarkers(documentConnection,
                                        x.cgs,
                                        x.citMarkers,
                                        style);
                rebuildBibTextSection(documentConnection,
                                      style,
                                      x.cgs,
                                      x.getBibliography());
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
