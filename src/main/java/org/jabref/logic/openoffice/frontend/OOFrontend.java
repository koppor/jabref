package org.jabref.logic.openoffice.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.openoffice.backend.Backend52;
import org.jabref.model.openoffice.CitationEntry;
import org.jabref.model.openoffice.ootext.OOText;
import org.jabref.model.openoffice.rangesort.FunctionalTextViewCursor;
import org.jabref.model.openoffice.rangesort.RangeKeyedMap;
import org.jabref.model.openoffice.rangesort.RangeKeyedMapList;
import org.jabref.model.openoffice.rangesort.RangeOverlap;
import org.jabref.model.openoffice.rangesort.RangeOverlapFinder;
import org.jabref.model.openoffice.rangesort.RangeSortEntry;
import org.jabref.model.openoffice.rangesort.RangeSortVisual;
import org.jabref.model.openoffice.rangesort.RangeSortable;
import org.jabref.model.openoffice.style.CitationGroup;
import org.jabref.model.openoffice.style.CitationGroupId;
import org.jabref.model.openoffice.style.CitationGroups;
import org.jabref.model.openoffice.style.CitationType;
import org.jabref.model.openoffice.style.OODataModel;
import org.jabref.model.openoffice.uno.CreationException;
import org.jabref.model.openoffice.uno.NoDocumentException;
import org.jabref.model.openoffice.uno.UnoCast;
import org.jabref.model.openoffice.uno.UnoCursor;
import org.jabref.model.openoffice.uno.UnoTextRange;
import org.jabref.model.openoffice.util.OOListUtil;
import org.jabref.model.openoffice.util.OOVoidResult;
import org.jabref.model.openoffice.util.OOTuple3;

import com.sun.star.beans.IllegalTypeException;
import com.sun.star.beans.NotRemoveableException;
import com.sun.star.beans.PropertyExistException;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XText;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextRangeCompare;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OOFrontend {
    private static final Logger LOGGER = LoggerFactory.getLogger(OOFrontend.class);
    public final Backend52 backend;
    public final CitationGroups citationGroups;

    public OOFrontend(XTextDocument doc)
        throws
        NoDocumentException,
        WrappedTargetException {

        // TODO: dataModel should come from looking at the
        // document and preferences.
        //
        this.backend = new Backend52();

        // Get the citationGroupNames
        List<String> citationGroupNames = this.backend.getJabRefReferenceMarkNames(doc);

        Map<CitationGroupId, CitationGroup> citationGroups =
            readCitationGroupsFromDocument(this.backend, doc, citationGroupNames);
        this.citationGroups = new CitationGroups(citationGroups);
    }

    public OODataModel getDataModel() {
        return backend.dataModel;
    }

    public Optional<String> healthReport(XTextDocument doc)
        throws
        NoDocumentException {
        return backend.healthReport(doc);
    }

    private static Map<CitationGroupId, CitationGroup>
    readCitationGroupsFromDocument(Backend52 backend,
                                   XTextDocument doc,
                                   List<String> citationGroupNames)
        throws
        WrappedTargetException,
        NoDocumentException {

        Map<CitationGroupId, CitationGroup> citationGroups = new HashMap<>();
        for (int i = 0; i < citationGroupNames.size(); i++) {
            final String name = citationGroupNames.get(i);
            CitationGroup cg = backend.readCitationGroupFromDocumentOrThrow(doc, name);
            citationGroups.put(cg.cgid, cg);
        }
        return citationGroups;
    }

    /**
     * Creates a list of {@code RangeSortable<CitationGroup>} values for
     * our {@code CitationGroup} values. Originally designed to be
     * passed to {@code visualSort}.
     *
     * The elements of the returned list are actually of type
     * {@code RangeSortEntry<CitationGroup>}.
     *
     * The result is sorted within {@code XTextRange.getText()}
     * partitions of the citation groups according to their {@code XTextRange}
     * (before mapping to footnote marks).
     *
     * In the result, RangeSortable.getIndexInPosition() contains
     * unique indexes within the original partition (not after
     * mapFootnotesToFootnoteMarks).
     *
     * @param mapFootnotesToFootnoteMarks If true, replace ranges in
     *        footnotes with the range of the corresponding footnote
     *        mark. This is used for numbering the citations.
     *
     */
    private List<RangeSortable<CitationGroup>>
    createVisualSortInput(XTextDocument doc, boolean mapFootnotesToFootnoteMarks)
        throws
        NoDocumentException,
        WrappedTargetException {

        List<RangeSortEntry<CitationGroup>> sortables = new ArrayList<>();
        for (CitationGroup cg : citationGroups.getCitationGroupsUnordered()) {
            XTextRange range = (this
                                .getMarkRange(doc, cg)
                                .orElseThrow(RuntimeException::new));
            sortables.add(new RangeSortEntry<>(range, 0, cg));
        }

        /*
         *  At this point we are almost ready to return sortables.
         *
         *  But we may want to number citations in a footnote
         *  as if it appeared where the footnote mark is.
         *
         *  The following code replaces ranges within footnotes with
         *  the range for the corresponding footnote mark.
         *
         *  This brings further ambiguity if we have multiple
         *  citation groups within the same footnote: for the comparison
         *  they become indistinguishable. Numbering between them is
         *  not controlled. Also combineCiteMarkers will see them in
         *  the wrong order (if we use this comparison), and will not
         *  be able to merge. To avoid these, we sort textually within
         *  each .getText() partition and add indexInPosition
         *  accordingly.
         *
         */

        // Sort within partitions
        RangeKeyedMapList<RangeSortEntry<CitationGroup>> rangeSorter =
            new RangeKeyedMapList<>();
        for (RangeSortEntry<CitationGroup> sortable : sortables) {
            rangeSorter.add(sortable.getRange(), sortable);
        }

        // build final list
        List<RangeSortEntry<CitationGroup>> result = new ArrayList<>();

        for (TreeMap<XTextRange, List<RangeSortEntry<CitationGroup>>>
                 partition : rangeSorter.partitionValues()) {

            List<XTextRange> orderedRanges = new ArrayList<>(partition.keySet());

            int indexInPartition = 0;
            for (int i = 0; i < orderedRanges.size(); i++) {
                XTextRange aRange = orderedRanges.get(i);
                List<RangeSortEntry<CitationGroup>> sortablesAtARange = partition.get(aRange);
                for (RangeSortEntry<CitationGroup> sortable : sortablesAtARange) {
                    sortable.setIndexInPosition(indexInPartition++);
                    if (mapFootnotesToFootnoteMarks) {
                        Optional<XTextRange> footnoteMarkRange =
                            UnoTextRange.getFootnoteMarkRange(sortable.getRange());
                        // Adjust range if we are inside a footnote:
                        if (footnoteMarkRange.isPresent()) {
                            sortable.setRange(footnoteMarkRange.get());
                        }
                    }
                    result.add(sortable);
                }
            }
        }
        return result.stream().map(e -> e).collect(Collectors.toList());
    }

    /**
     *  @param mapFootnotesToFootnoteMarks If true, sort reference
     *         marks in footnotes as if they appeared at the
     *         corresponding footnote mark.
     *
     *  @return citation groups sorted by their visual positions.
     *
     *  Limitation: for two column layout visual (top-down,
     *        left-right) order does not match the expected (textual)
     *        order.
     *
     */
    private List<CitationGroup>
    getVisuallySortedCitationGroups(XTextDocument doc,
                                    boolean mapFootnotesToFootnoteMarks,
                                    FunctionalTextViewCursor fcursor)
        throws
        WrappedTargetException,
        NoDocumentException,
        JabRefException {

        List<RangeSortable<CitationGroup>> sortables =
            createVisualSortInput(doc, mapFootnotesToFootnoteMarks);

        List<RangeSortable<CitationGroup>> sorted =
            RangeSortVisual.visualSort(sortables,
                                       doc,
                                       fcursor);

        return (sorted.stream().map(e -> e.getContent()).collect(Collectors.toList()));
    }

    /**
     * Return citation groups in visual order within (but not across)
     * XText partitions.
     *
     * This is (1) sufficient for combineCiteMarkers which looks for
     * consecutive XTextRanges within each XText, (2) not confused by
     * multicolumn layout or multipage display.
     */
    public List<CitationGroup>
    getCitationGroupsSortedWithinPartitions(XTextDocument doc, boolean mapFootnotesToFootnoteMarks)
        throws
        NoDocumentException,
        WrappedTargetException {
        // This is like getVisuallySortedCitationGroups,
        // but we skip the visualSort part.
        List<RangeSortable<CitationGroup>> sortables =
            createVisualSortInput(doc, mapFootnotesToFootnoteMarks);

        return (sortables.stream().map(e -> e.getContent()).collect(Collectors.toList()));
    }

    /**
     *  Create a citation group for the given citation keys, at the
     *  end of position.
     *
     *  On return {@code position} is collapsed, and is after the
     *  inserted space, or at the end of the reference mark.
     *
     * @param citationKeys In storage order
     * @param pageInfos In storage order
     * @param citationType
     * @param position Collapsed to its end.
     * @param insertSpaceAfter If true, we insert a space after the mark, that
     *                         carries on format of characters from
     *                         the original position.
     */
    public CitationGroup createCitationGroup(XTextDocument doc,
                                             List<String> citationKeys,
                                             List<Optional<OOText>> pageInfos,
                                             CitationType citationType,
                                             XTextCursor position,
                                             boolean insertSpaceAfter)
        throws
        CreationException,
        NoDocumentException,
        WrappedTargetException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        IllegalTypeException {

        Objects.requireNonNull(pageInfos);
        if (pageInfos.size() != citationKeys.size()) {
            throw new RuntimeException("pageInfos.size != citationKeys.size");
        }
        CitationGroup cg = backend.createCitationGroup(doc,
                                                       citationKeys,
                                                       pageInfos,
                                                       citationType,
                                                       position,
                                                       insertSpaceAfter);

        this.citationGroups.afterCreateCitationGroup(cg);
        return cg;
    }

    /**
     * Remove {@code cg} both from the document and notify {@code citationGroups}
     */
    public void removeCitationGroup(CitationGroup cg, XTextDocument doc)
        throws
        WrappedTargetException,
        NoDocumentException,
        NoSuchElementException,
        NotRemoveableException,
        PropertyExistException,
        IllegalTypeException {

        backend.removeCitationGroup(cg, doc);
        this.citationGroups.afterRemoveCitationGroup(cg);
    }

    public void removeCitationGroups(List<CitationGroup> cgs, XTextDocument doc)
        throws
        WrappedTargetException,
        NoDocumentException,
        NoSuchElementException,
        NotRemoveableException,
        PropertyExistException,
        IllegalTypeException {

        for (CitationGroup cg : cgs) {
            removeCitationGroup(cg, doc);
        }
    }

    /**
     * ranges controlled by citation groups should not overlap with each other.
     *
     * @return Optional.empty() if the reference mark is missing.
     *
     */
    public Optional<XTextRange> getMarkRange(XTextDocument doc, CitationGroup cg)
        throws
        NoDocumentException,
        WrappedTargetException {
        return backend.getMarkRange(cg, doc);
    }

    public XTextCursor getFillCursorForCitationGroup(XTextDocument doc, CitationGroup cg)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {
        return backend.getFillCursorForCitationGroup(cg, doc);
    }

    /**
     * Remove brackets added by getFillCursorForCitationGroup.
     */
    public void cleanFillCursorForCitationGroup(XTextDocument doc, CitationGroup cg)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {

        backend.cleanFillCursorForCitationGroup(cg, doc);
    }

    /**
     * @return A RangeForOverlapCheck for each citation group.
     *
     *  result.size() == nRefMarks
     */
    public List<RangeForOverlapCheck<CitationGroupId>> citationRanges(XTextDocument doc)
        throws
        NoDocumentException,
        WrappedTargetException {

        List<RangeForOverlapCheck<CitationGroupId>> result =
            new ArrayList<>(citationGroups.numberOfCitationGroups());

        for (CitationGroup cg : citationGroups.getCitationGroupsUnordered()) {
            XTextRange range = this.getMarkRange(doc, cg).orElseThrow(RuntimeException::new);
            String description = cg.cgid.citationGroupIdAsString(); // cg.cgRangeStorage.nrGetRangeName();
            result.add(new RangeForOverlapCheck<>(range,
                                                  cg.cgid,
                                                  RangeForOverlapCheck.REFERENCE_MARK_KIND,
                                                  description));
        }
        return result;
    }

    public List<RangeForOverlapCheck<CitationGroupId>> bibliographyRanges(XTextDocument doc)
        throws
        NoDocumentException,
        WrappedTargetException {

        List<RangeForOverlapCheck<CitationGroupId>> result = new ArrayList<>();

        Optional<XTextRange> range = UpdateBibliography.getBibliographyRange(doc);
        if (range.isPresent()) {
            String description = "bibliography";
            result.add(new RangeForOverlapCheck<>(range.get(),
                                                  new CitationGroupId("bibliography"),
                                                  RangeForOverlapCheck.BIBLIOGRAPHY_MARK_KIND,
                                                  description));
        }
        return result;
    }

    public List<RangeForOverlapCheck<CitationGroupId>> viewCursorRanges(XTextDocument doc)
        throws
        NoDocumentException,
        WrappedTargetException {

        List<RangeForOverlapCheck<CitationGroupId>> result = new ArrayList<>();

        Optional<XTextRange> range = UnoCursor.getViewCursor(doc).map(e -> e);
        if (range.isPresent()) {
            String description = "cursor";
            result.add(new RangeForOverlapCheck<>(range.get(),
                                                  new CitationGroupId("cursor"),
                                                  RangeForOverlapCheck.CURSOR_MARK_KIND,
                                                  description));
        }
        return result;
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
    public List<RangeForOverlapCheck<CitationGroupId>>
    footnoteMarkRanges(XTextDocument doc,
                       List<RangeForOverlapCheck<CitationGroupId>> citationRanges)
        throws
        NoDocumentException,
        WrappedTargetException {

        // Avoid inserting the same mark twice.
        // Could use RangeSet if we had that.
        RangeKeyedMap<Boolean> seen = new RangeKeyedMap<>();

        List<RangeForOverlapCheck<CitationGroupId>> result = new ArrayList<>();

        for (RangeForOverlapCheck<CitationGroupId> citationRange : citationRanges) {

            Optional<XTextRange> footnoteMarkRange =
                UnoTextRange.getFootnoteMarkRange(citationRange.range);

            if (footnoteMarkRange.isEmpty()) {
                // not in footnote
                continue;
            }

            boolean seenContains = seen.containsKey(footnoteMarkRange.get());
            if (!seenContains) {
                seen.put(footnoteMarkRange.get(), true);
                result.add(new RangeForOverlapCheck<>(footnoteMarkRange.get(),
                                                      citationRange.idWithinKind,
                                                      RangeForOverlapCheck.FOOTNOTE_MARK_KIND,
                                                      "FootnoteMark for " + citationRange.format()));
            }
        }
        return result;
    }

    /**
     * Check for any overlap between userRanges and protected ranges.
     *
     * Assume userRanges is small (usually 1 elements for checking the cursor)
     * Returns on first problem found.
     */
    public OOVoidResult<JabRefException>
    checkRangeOverlapsWithCursor(XTextDocument doc,
                                 List<RangeForOverlapCheck<CitationGroupId>> userRanges,
                                 boolean requireSeparation)
        throws
        NoDocumentException,
        WrappedTargetException {

        List<RangeForOverlapCheck<CitationGroupId>> citationRanges = citationRanges(doc);
        List<RangeForOverlapCheck<CitationGroupId>> ranges = new ArrayList<>();

        // ranges.addAll(userRanges);
        ranges.addAll(bibliographyRanges(doc));
        ranges.addAll(citationRanges);
        ranges.addAll(footnoteMarkRanges(doc, citationRanges));

        if (userRanges.size() == 0) {
            return OOVoidResult.ok();
        }
        List<OOTuple3<XText, XTextRangeCompare, RangeForOverlapCheck<CitationGroupId>>> userTuples =
            new ArrayList<>(userRanges.size());
        for (RangeForOverlapCheck<CitationGroupId> userRange : userRanges) {
            XText text = userRange.range.getText();
            userTuples.add(new OOTuple3<>(text,
                                          UnoCast.unoQI(XTextRangeCompare.class, text),
                                          userRange));
        }

        for (RangeForOverlapCheck<CitationGroupId> range : ranges) {
            XTextRange bRange = range.range;
            XText bRangeText = bRange.getText();
            XTextRange bRangeStart = bRange.getStart();
            XTextRange bRangeEnd = bRange.getEnd();
            for (OOTuple3<XText, XTextRangeCompare, RangeForOverlapCheck<CitationGroupId>> tup :
                     userTuples){
                if (tup.a != bRangeText) {
                    continue;
                }
                XTextRangeCompare cmp = tup.b;
                XTextRange aRange = tup.c.range;
                int abEndToStart = -1 * cmp.compareRegionStarts(aRange.getEnd(), bRangeStart);
                if (abEndToStart < 0 || (!requireSeparation && (abEndToStart==0))) {
                    continue;
                }
                int baEndToStart = -1 * cmp.compareRegionStarts(bRangeEnd, aRange.getStart());
                if (baEndToStart < 0 || (!requireSeparation && (baEndToStart==0))) {
                    continue;
                }

                StringBuilder msg = new StringBuilder();
                boolean touching = (abEndToStart == 0 || baEndToStart == 0);

                if (touching) {
                    msg.append(Localization.lang("Found touching ranges"));
                } else {
                    msg.append(Localization.lang("Found overlapping ranges"));
                }
                msg.append(": ");
                msg.append(String.format("'%s'", tup.c.format()));
                msg.append(", ");
                msg.append(String.format("'%s'", range.format()));
                msg.append("\n");

                return OOVoidResult.error(new JabRefException("Found overlapping or touching ranges",
                                                              msg.toString()));
            }
        }
        return OOVoidResult.ok();
    }

    /**
     * @param requireSeparation Report range pairs that only share a boundary.
     * @param reportAtMost Limit number of overlaps reported (0 for no limit)
     *
     */
    public OOVoidResult<JabRefException>
    checkRangeOverlaps(XTextDocument doc,
                       List<RangeForOverlapCheck<CitationGroupId>> userRanges,
                       boolean requireSeparation,
                       int reportAtMost)
        throws
        NoDocumentException,
        WrappedTargetException {

        List<RangeForOverlapCheck<CitationGroupId>> citationRanges = citationRanges(doc);
        List<RangeForOverlapCheck<CitationGroupId>> ranges = new ArrayList<>();
        ranges.addAll(userRanges);
        ranges.addAll(bibliographyRanges(doc));
        ranges.addAll(citationRanges);
        ranges.addAll(footnoteMarkRanges(doc, citationRanges));

        RangeKeyedMapList<RangeForOverlapCheck<CitationGroupId>> sorted = new RangeKeyedMapList<>();
        for (RangeForOverlapCheck<CitationGroupId> aRange : ranges) {
            sorted.add(aRange.range, aRange);
        }

        List<RangeOverlap<RangeForOverlapCheck<CitationGroupId>>> overlaps =
            RangeOverlapFinder.findOverlappingRanges(sorted, reportAtMost, requireSeparation);

        if (overlaps.size() > 0) {
            StringBuilder msg = new StringBuilder();
            for (RangeOverlap<RangeForOverlapCheck<CitationGroupId>> overlap : overlaps) {
                String listOfRanges = (overlap.valuesForOverlappingRanges.stream()
                                       .map(v -> String.format("'%s'", v.format()))
                                       .collect(Collectors.joining(", ")));

                msg.append(
                    switch (overlap.kind) {
                    case EQUAL_RANGE -> Localization.lang("Found identical ranges");
                    case OVERLAP -> Localization.lang("Found overlapping ranges");
                    case TOUCH -> Localization.lang("Found touching ranges");
                    });
                msg.append(": ");
                msg.append(listOfRanges);
                msg.append("\n");
            }
            return OOVoidResult.error(new JabRefException("Found overlapping or touching ranges",
                                                          msg.toString()));
        } else {
            return OOVoidResult.ok();
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
    public List<CitationEntry> getCitationEntries(XTextDocument doc)
        throws
        UnknownPropertyException,
        WrappedTargetException,
        NoDocumentException {
        return this.backend.getCitationEntries(doc, citationGroups);
    }

    public void applyCitationEntries(XTextDocument doc, List<CitationEntry> citationEntries)
        throws
        UnknownPropertyException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        IllegalTypeException,
        IllegalArgumentException,
        NoDocumentException,
        WrappedTargetException {
        this.backend.applyCitationEntries(doc, citationEntries);
    }

    public void imposeGlobalOrder(XTextDocument doc, FunctionalTextViewCursor fcursor)
        throws
        WrappedTargetException,
        NoDocumentException,
        JabRefException {

        boolean mapFootnotesToFootnoteMarks = true;
        List<CitationGroup> sortedCitationGroups =
            getVisuallySortedCitationGroups(doc, mapFootnotesToFootnoteMarks, fcursor);
        List<CitationGroupId> sortedCitationGroupIds =
            OOListUtil.map(sortedCitationGroups, cg -> cg.cgid);
        citationGroups.setGlobalOrder(sortedCitationGroupIds);
    }
}
