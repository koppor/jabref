package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.oostyle.CitationGroup;
import org.jabref.logic.oostyle.CitationGroups;
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
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OOFrontend {
    private static final Logger LOGGER = LoggerFactory.getLogger(OOFrontend.class);
    public final Backend52 backend;
    public final CitationGroups cgs;

    public OOFrontend(DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException {

        // TODO: dataModel should come from looking at the
        // document and preferences.
        //
        this.backend = new Backend52();

        // Get the citationGroupNames
        List<String> citationGroupNames = this.backend.getJabRefReferenceMarkNames(documentConnection);

        Map<CitationGroupID, CitationGroup> citationGroups =
            readCitationGroupsFromDocument(this.backend,
                                           documentConnection,
                                           citationGroupNames);
        this.cgs = new CitationGroups(backend.dataModel, citationGroups);
    }

    public OOStyleDataModelVersion getDataModel() {
        return backend.dataModel;
    }

    public Optional<String> healthReport(DocumentConnection documentConnection)
        throws
        NoDocumentException {
        return backend.healthReport(documentConnection);
    }

    private static Map<CitationGroupID, CitationGroup>
    readCitationGroupsFromDocument(Backend52 backend,
                                   DocumentConnection documentConnection,
                                   List<String> citationGroupNames)
        throws
        WrappedTargetException,
        NoDocumentException {

        Map<CitationGroupID, CitationGroup> citationGroups = new HashMap<>();
        for (int i = 0; i < citationGroupNames.size(); i++) {
            final String name = citationGroupNames.get(i);
            CitationGroup cg =
                backend.readCitationGroupFromDocumentOrThrow(documentConnection, name);
            citationGroups.put(cg.cgid, cg);
        }
        return citationGroups;
    }

    /**
     * Creates a list of {@code RangeSortable<CitationGroupID>} values for
     * our {@code CitationGroup} values. Originally designed to be
     * passed to {@code visualSort}.
     *
     * The elements of the returned list are actually of type {@code RangeSortEntry<CitationGroupID>}.
     *
     * The result is sorted within {@code XTextRange.getText()}
     * partitions of the citation groups according to their {@code XTextRange}
     * (before mapping to footnote marks).
     *
     * In the result, RangeSortable.getIndexInPosition() contains
     * unique indexes within the original partition (not after
     * mapFootnotesToFootnoteMarks).
     *
     * @param documentConnection Connection to the document.
     * @param mapFootnotesToFootnoteMarks If true, replace ranges in
     *        footnotes with the range of the corresponding footnote
     *        mark. This is used for numbering the citations.
     *
     */
    private List<RangeSort.RangeSortable<CitationGroupID>>
    createVisualSortInput(DocumentConnection documentConnection,
                          boolean mapFootnotesToFootnoteMarks)
        throws
        NoDocumentException,
        WrappedTargetException {

        XTextDocument doc = documentConnection.asXTextDocument();
        List<CitationGroupID> cgids = new ArrayList<>(cgs.getCitationGroupIDs());

        List<RangeSort.RangeSortEntry> vses = new ArrayList<>();
        for (CitationGroupID cgid : cgids) {
            XTextRange range = (this
                                .getMarkRange(doc, cgid)
                                .orElseThrow(RuntimeException::new));
            vses.add(new RangeSort.RangeSortEntry(range, 0, cgid));
        }

        /*
         *  At this point we are almost ready to return vses.
         *
         *  For example we may want to number citations in a footnote
         *  as if it appeared where the footnote mark is.
         *
         *  The following code replaces ranges within footnotes with
         *  the range for the corresponding footnote mark.
         *
         *  This brings further ambiguity if we have multiple
         *  citations within the same footnote: for the comparison
         *  they become indistinguishable. Numbering between them is
         *  not controlled. Also combineCiteMarkers will see them in
         *  the wrong order (if we use this comparison), and will not
         *  be able to merge. To avoid these, we sort textually within
         *  each .getText() partition and add indexInPosition
         *  accordingly.
         *
         */

        // Sort within partitions
        RangeKeyedMapList<RangeSort.RangeSortEntry<CitationGroupID>> xxs
            = new RangeKeyedMapList<>();

        for (RangeSort.RangeSortEntry v : vses) {
            xxs.add(v.getRange(), v);
        }

        // build final list
        List<RangeSort.RangeSortEntry<CitationGroupID>> res = new ArrayList<>();

        for (TreeMap<XTextRange, List<RangeSort.RangeSortEntry<CitationGroupID>>>
                 xs : xxs.partitionValues()) {

            List<XTextRange> oxs = new ArrayList<>(xs.keySet());

            int indexInPartition = 0;
            for (int i = 0; i < oxs.size(); i++) {
                XTextRange a = oxs.get(i);
                List<RangeSort.RangeSortEntry<CitationGroupID>> avs = xs.get(a);
                for (int j = 0; j < avs.size(); j++) {
                    RangeSort.RangeSortEntry<CitationGroupID> v = avs.get(j);
                    v.indexInPosition = indexInPartition++;
                    if (mapFootnotesToFootnoteMarks) {
                        Optional<XTextRange> fmr = DocumentConnection.getFootnoteMarkRange(v.getRange());
                        // Adjust range if we are inside a footnote:
                        if (fmr.isPresent()) {
                            v.range = fmr.get();
                        }
                    }
                    res.add(v);
                }
            }
        }
        // convert
        // List<RangeSortEntry<CitationGroupID>>
        // to
        // List<RangeSortable<CitationGroupID>>
        return res.stream().map(e -> e).collect(Collectors.toList());
    }

    /**
     *  Return JabRef reference mark names sorted by their visual positions.
     *
     *  @param mapFootnotesToFootnoteMarks If true, sort reference
     *         marks in footnotes as if they appeared at the
     *         corresponding footnote mark.
     *
     *  @return JabRef reference mark names sorted by these positions.
     *
     *  Limitation: for two column layout visual (top-down,
     *        left-right) order does not match the expected (textual)
     *        order.
     *
     */
    private List<CitationGroupID> getVisuallySortedCitationGroupIDs(DocumentConnection documentConnection,
                                                                    boolean mapFootnotesToFootnoteMarks)
        throws
        WrappedTargetException,
        NoDocumentException,
        JabRefException {

        List<RangeSort.RangeSortable<CitationGroupID>> vses =
            createVisualSortInput(documentConnection,
                                  mapFootnotesToFootnoteMarks);

        if (vses.size() != this.cgs.numberOfCitationGroups()) {
            throw new RuntimeException("getVisuallySortedCitationGroupIDs:"
                                       + " vses.size() != cgs.citationGroups.size()");
        }

        String messageOnFailureToObtainAFunctionalXTextViewCursor =
            Localization.lang("Please move the cursor into the document text.")
            + "\n"
            + Localization.lang("To get the visual positions of your citations"
                                + " I need to move the cursor around,"
                                + " but could not get it.");
        List<RangeSort.RangeSortable<CitationGroupID>> sorted =
            RangeSortVisual.visualSort(vses,
                                       documentConnection,
                                       messageOnFailureToObtainAFunctionalXTextViewCursor);

        if (sorted.size() != this.cgs.numberOfCitationGroups()) {
            // This Fired
            throw new RuntimeException("getVisuallySortedCitationGroupIDs:"
                                       + " sorted.size() != cgs.numberOfCitationGroups()");
        }

        return (sorted.stream()
                .map(e -> e.getContent())
                .collect(Collectors.toList()));
    }

    /**
     * Calculate and return citation group IDs in visual order.
     */
    public List<CitationGroupID>
    getCitationGroupIDsSortedWithinPartitions(DocumentConnection documentConnection,
                                              boolean mapFootnotesToFootnoteMarks)
        throws
        NoDocumentException,
        WrappedTargetException {
        // This is like getVisuallySortedCitationGroupIDs,
        // but we skip the visualSort part.
        List<RangeSort.RangeSortable<CitationGroupID>> vses =
            createVisualSortInput(documentConnection,
                                  mapFootnotesToFootnoteMarks);

        if (vses.size() != this.cgs.numberOfCitationGroups()) {
            throw new RuntimeException("getCitationGroupIDsSortedWithinPartitions:"
                                       + " vses.size() != cgs.numberOfCitationGroups()");
        }
        return (vses.stream()
                .map(e -> e.getContent())
                .collect(Collectors.toList()));
    }

    /**
     *  Create a citation group for the given citation keys, at the
     *  end of position.
     *
     *  To reduce the difference from the original representation, we
     *  only insist on having at least two characters inside reference
     *  marks. These may be ZERO_WIDTH_SPACE characters or other
     *  placeholder not likely to appear in a citation mark.
     *
     *  This placeholder is only needed if the citation mark is
     *  otherwise empty (e.g. when we just create it).
     *
     *  getFillCursorForCitationGroup yields a bracketed cursor, that
     *  can be used to fill in / overwrite the value inside.
     *
     *  After each getFillCursorForCitationGroup, we require a call to
     *  cleanFillCursorForCitationGroup, which removes the brackets,
     *  unless if it would make the content less than two
     *  characters. If we need only one placeholder, we keep the left
     *  bracket.  If we need two, then the content is empty. The
     *  removeBracketsFromEmpty parameter of
     *  cleanFillCursorForCitationGroup overrides this, and for empty
     *  citations it will remove the brackets, leaving an empty
     *  reference mark. The idea behind this is that we do not need to
     *  refill empty marks (itcTypes INVISIBLE_CIT), and the caller
     *  can tell us that we are dealing with one of these.
     *
     *  Thus the only user-visible difference in citation marks is
     *  that instead of empty marks we use two brackets, for
     *  single-character marks we add a left bracket before.
     *
     *  Character-attribute inheritance: updates inherit from the
     *  first character inside, not from the left.
     *
     *  On return {@code position} is collapsed, and is after the
     *  inserted space, or at the end of the reference mark.
     *
     * @param documentConnection Connection to document.
     * @param citationKeys
     * @param pageInfosForCitations
     * @param itcType
     * @param position Collapsed to its end.
     * @param insertSpaceAfter If true, we insert a space after the mark, that
     *                         carries on format of characters from
     *                         the original position.
     *
     * @param withoutBrackets  Force empty reference mark (no brackets).
     *                         For use with INVISIBLE_CIT.
     *
     */
    public CitationGroupID createCitationGroup(DocumentConnection documentConnection,
                                               List<String> citationKeys,
                                               List<OOFormattedText> pageInfosForCitations,
                                               InTextCitationType itcType,
                                               XTextCursor position,
                                               boolean insertSpaceAfter,
                                               boolean withoutBrackets)
        throws
        CreationException,
        NoDocumentException,
        WrappedTargetException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        IllegalTypeException {

        CitationGroup cg = backend.createCitationGroup(documentConnection,
                                                       citationKeys,
                                                       pageInfosForCitations,
                                                       itcType,
                                                       position,
                                                       insertSpaceAfter,
                                                       withoutBrackets);

        this.cgs.afterCreateCitationGroup(cg);
        return cg.cgid;
    }

    /**
     * Remove {@code cg} both from {@code cgs} and the document.
     *
     * Note: we invalidate the extra data we are storing
     *       (bibliography).
     *
     *       Update would be complicated, since we do not know how the
     *       bibliography was generated: it was partially done outside
     *       CitationGroups, and we did not store how.
     *
     *       So we stay with invalidating.
     *       Note: localOrder, numbering, uniqueLetters are not adjusted,
     *             it is easier to reread everything for a refresh.
     *
     */
    public void removeCitationGroup(CitationGroup cg,
                                    DocumentConnection documentConnection)
        throws
        WrappedTargetException,
        NoDocumentException,
        NoSuchElementException,
        NotRemoveableException,
        PropertyExistException,
        IllegalTypeException {

        // Apply
        backend.removeCitationGroup(cg, documentConnection);
        this.cgs.afterRemoveCitationGroup(cg);
    }

    public void removeCitationGroups(List<CitationGroup> xcgs, DocumentConnection documentConnection)
        throws
        WrappedTargetException,
        NoDocumentException,
        NoSuchElementException,
        NotRemoveableException,
        PropertyExistException,
        IllegalTypeException {

        for (CitationGroup cg : xcgs) {
            removeCitationGroup(cg, documentConnection);
        }
    }

    /**
     * ranges controlled by citation groups should not overlap with each other.
     *
     * @param cgid : Must be known, throws if not.
     * @return Null if the reference mark is missing.
     *
     */
    public Optional<XTextRange> getMarkRange(XTextDocument doc,
                                             CitationGroupID cgid)
        throws
        NoDocumentException,
        WrappedTargetException {
        CitationGroup cg = this.cgs.getCitationGroup(cgid).orElseThrow(RuntimeException::new);
        return backend.getMarkRange(cg, doc);
    }

    /**
     * Cursor for the reference marks as is, not prepared for filling,
     * but does not need cleanFillCursorForCitationGroup either.
     */
    public Optional<XTextCursor> getRawCursorForCitationGroup(CitationGroupID cgid,
                                                              DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {

        Optional<CitationGroup> cg = this.cgs.getCitationGroup(cgid);
        if (cg.isEmpty()) {
            return Optional.empty();
        }
        return backend.getRawCursorForCitationGroup(cg.get(), documentConnection);
    }

    public XTextCursor getFillCursorForCitationGroup(DocumentConnection documentConnection,
                                                     CitationGroupID cgid)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {

        CitationGroup cg = this.cgs.getCitationGroup(cgid).orElseThrow(RuntimeException::new);
        return backend.getFillCursorForCitationGroup(cg, documentConnection);
    }

        /**
     * Remove brackets, but if the result would become empty, leave
     * them; if the result would be a single characer, leave the left bracket.
     */
    public void cleanFillCursorForCitationGroup(DocumentConnection documentConnection,
                                                CitationGroupID cgid)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {

        CitationGroup cg = this.cgs.getCitationGroup(cgid).orElseThrow(RuntimeException::new);
        backend.cleanFillCursorForCitationGroup(cg, documentConnection);
    }

    /**
     * @return A RangeForOverlapCheck for each citation group.
     *
     *  result.size() == nRefMarks
     */
    public List<RangeForOverlapCheck> citationRanges(DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException {

        XTextDocument doc = documentConnection.asXTextDocument();
        List<RangeForOverlapCheck> xs = new ArrayList<>(cgs.numberOfCitationGroups());

        List<CitationGroupID> cgids = new ArrayList<>(cgs.getCitationGroupIDs());

        for (CitationGroupID cgid : cgids) {
            XTextRange r = this.getMarkRange(doc, cgid).orElseThrow(RuntimeException::new);
            CitationGroup cg = cgs.getCitationGroup(cgid).orElseThrow(RuntimeException::new);
            String name = cg.cgRangeStorage.getName();
            xs.add(new RangeForOverlapCheck(r,
                                            cgid,
                                            RangeForOverlapCheck.REFERENCE_MARK_KIND,
                                            name));
        }
        return xs;
    }

    /**
     * @return A range for each footnote mark where the footnote
     *         contains at least one citation group.
     *
     *  Purpose: We do not want markers of footnotes containing
     *  reference marks to overlap with reference
     *  marks. Overwriting these footnote marks might kill our
     *  reference marks in the footnote.
     *
     *  Note: Here we directly communicate to the document, not
     *        through the backend. This is because mapping ranges to
     *        footnote marks does not depend on how do we mark or
     *        structure those ranges.
     */
    public List<RangeForOverlapCheck> footnoteMarkRanges(DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException {

        // Avoid inserting the same mark twice.
        // Could use RangeSet if we had that.
        RangeKeyedMap<Boolean> seen = new RangeKeyedMap<>();

        List<RangeForOverlapCheck> xs = new ArrayList<>();

        List<RangeForOverlapCheck> citRanges = citationRanges(documentConnection);

        for (RangeForOverlapCheck base : citRanges) {
            XTextRange r = base.range;

            Optional<XTextRange> footnoteMarkRange = DocumentConnection.getFootnoteMarkRange(r);

            if (footnoteMarkRange.isEmpty()) {
                // not in footnote
                continue;
            }

            boolean seenContains = seen.containsKey(footnoteMarkRange.get());
            if (!seenContains) {
                seen.put(footnoteMarkRange.get(), true);
                xs.add(new RangeForOverlapCheck(footnoteMarkRange.get(),
                                                base.i, // cgid :: identifies of citation group
                                                RangeForOverlapCheck.FOOTNOTE_MARK_KIND,
                                                "FootnoteMark for " + base.format()));
            }
        }
        return xs;
    }

    /**
     * @param requireSeparation Report range pairs that only share a boundary.
     * @param reportAtMost Limit number of overlaps reported (0 for no limit)
     *
     */
    public void checkRangeOverlaps(DocumentConnection documentConnection,
                                   boolean requireSeparation,
                                   int reportAtMost)
        throws
        NoDocumentException,
        WrappedTargetException,
        JabRefException {

        final boolean debugPartitions = false;

        List<RangeForOverlapCheck> xs = citationRanges(documentConnection);
        xs.addAll(footnoteMarkRanges(documentConnection));

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
    public List<CitationEntry> getCitationEntries(DocumentConnection documentConnection)
        throws
        UnknownPropertyException,
        WrappedTargetException,
        NoDocumentException,
        CreationException {
        return this.backend.getCitationEntries(documentConnection, cgs);
    }

    public void applyCitationEntries(DocumentConnection documentConnection,
                                     List<CitationEntry> citationEntries)
        throws
        UnknownPropertyException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        IllegalTypeException,
        IllegalArgumentException,
        NoDocumentException,
        WrappedTargetException {
        this.backend.applyCitationEntries(documentConnection, citationEntries);
    }

    public void imposeGlobalOrder(DocumentConnection documentConnection)
        throws
        WrappedTargetException,
        NoDocumentException,
        JabRefException {

        boolean mapFootnotesToFootnoteMarks = true;
        List<CitationGroupID> sortedCitationGroupIDs =
            getVisuallySortedCitationGroupIDs(documentConnection,
                                              mapFootnotesToFootnoteMarks);
        cgs.setGlobalOrder(sortedCitationGroupIDs);
    }
}
