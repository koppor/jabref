package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jabref.logic.openoffice.StorageBase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.OOFormattedText;
import org.jabref.model.oostyle.OOStyleDataModelVersion;

/*
 * A CitationGroup describes a group of citations.
 */
public class CitationGroup {

    public final OOStyleDataModelVersion dataModel;

    /*
     * Identifies this citation group.
     */
    public final CitationGroupID cgid;

    /*
     * Identifies location in the document for the backend.
     */
    public final StorageBase.NamedRange cgRangeStorage;

    /*
     * The core data, stored in the document:
     * The type of citation and citations in storage order.
     */
    public final InTextCitationType citationType;
    public final List<Citation> citationsInStorageOrder;

    /*
     * Extra data
     */

    /*
     * A name of a reference mark to link to by formatCitedOnPages.
     * May be ininitially empty, if backend does not use reference marks.
     *
     * produceCitationMarkers might want fill it to support
     * cross-references to citation groups from the bibliography.
     */
    private Optional<String> referenceMarkNameForLinking;

    /*
     * Indices into citations: citations[localOrder[i]] provides ith
     * citation according to the currently imposed local order for
     * presentation.
     *
     * Initialized to (0..(nCitations-1)) in the constructor.
     */
    private List<Integer> localOrder;

    /*
     * "Cited on pages" uses this to sort the cross-references.
     */
    private Optional<Integer> indexInGlobalOrder;

    public CitationGroup(OOStyleDataModelVersion dataModel,
                         CitationGroupID cgid,
                         StorageBase.NamedRange cgRangeStorage,
                         InTextCitationType citationType,
                         List<Citation> citationsInStorageOrder,
                         Optional<String> referenceMarkNameForLinking) {
        this.dataModel = dataModel;
        this.cgid = cgid;
        this.cgRangeStorage = cgRangeStorage;
        this.citationType = citationType;
        this.citationsInStorageOrder = Collections.unmodifiableList(citationsInStorageOrder);
        this.localOrder = makeIndices(citationsInStorageOrder.size());
        this.referenceMarkNameForLinking = referenceMarkNameForLinking;
        this.indexInGlobalOrder = Optional.empty();
    }

    public int numberOfCitations() {
        return citationsInStorageOrder.size();
    }

    /*
     * localOrder
     */

    /** Integers 0..(n-1) */
    static List<Integer> makeIndices(int n) {
        return Stream.iterate(0, i -> i + 1).limit(n).collect(Collectors.toList());
    }

    /*
     * Helper class for imposeLocalOrderByComparator: a citation
     * paired with its storage index.
     */
    private class CitationAndIndex implements CitationSort.ComparableCitation {
        Citation c;
        int i;

        CitationAndIndex(Citation c, int i) {
            this.c = c;
            this.i = i;
        }

        @Override
        public String getCitationKey() {
            return c.getCitationKey();
        }

        @Override
        public Optional<BibEntry> getBibEntry() {
            return c.getBibEntry();
        }

        @Override
        public Optional<OOFormattedText> getPageInfo() {
            return c.pageInfo;
        }
    }

    /**
     * Sort citations for presentation within a CitationGroup.
     */
    void imposeLocalOrderByComparator(Comparator<BibEntry> entryComparator) {

        // Pair citations with their storage index in citations
        final int nCitations = citationsInStorageOrder.size();

        // For JabRef52 the single pageInfo is always in the
        // last-in-localorder citation.
        // We adjust here accordingly.
        final int last = nCitations - 1;
        Optional<OOFormattedText> lastPageInfo = Optional.empty();
        if (dataModel == OOStyleDataModelVersion.JabRef52) {
            lastPageInfo = getCitationsInLocalOrder().get(last).pageInfo;
            getCitationsInLocalOrder().get(last).pageInfo = Optional.empty();
        }

        List<CitationAndIndex> cis = new ArrayList<>(nCitations);
        for (int i = 0; i < nCitations; i++) {
            Citation c = citationsInStorageOrder.get(i);
            cis.add(new CitationAndIndex(c, i));
        }

        // Sort the list
        cis.sort(new CitationSort.CitationComparator(entryComparator, true));

        // Copy ordered storage indices to localOrder
        List<Integer> ordered = new ArrayList<>(nCitations);
        for (CitationAndIndex ci : cis) {
            ordered.add(ci.i);
        }
        this.localOrder = ordered;

        if (dataModel == OOStyleDataModelVersion.JabRef52) {
            getCitationsInLocalOrder().get(last).pageInfo = lastPageInfo;
        }
    }

    public List<Integer> getLocalOrder() {
        return Collections.unmodifiableList(localOrder);
    }

    /*
     * citations
     */

    public List<Citation> getCitationsInLocalOrder() {
        List<Citation> res = new ArrayList<>(citationsInStorageOrder.size());
        for (int i : localOrder) {
            res.add(citationsInStorageOrder.get(i));
        }
        return res;
    }

    /*
     * Values of the number fields of the citations according to
     * localOrder.
     */
    public List<Integer> getCitationNumbersInLocalOrder() {
        List<Citation> cits = getCitationsInLocalOrder();
        return (cits.stream()
                .map(cit -> cit.number.orElseThrow(RuntimeException::new))
                .collect(Collectors.toList()));
    }

    /**
     * @return List of nullable pageInfo values, one for each citation,
     *         instrage order.
     *
     *         Result contains null for missing pageInfo values.
     *         The list itself is not null.
     *
     */
    public List<OOFormattedText> getPageInfosForCitationsInStorageOrder() {
        CitationGroup cg = this;
        // pageInfo values from citations, empty mapped to null.
        return (cg.citationsInStorageOrder.stream()
                .map(cit -> cit.pageInfo.orElse(null))
                .collect(Collectors.toList()));
    }

    /**
     * @return List of nullable pageInfo values, one for each citation, in localOrder.
     */
    public List<OOFormattedText> getPageInfosForCitationsInLocalOrder() {
        CitationGroup cg = this;
        // pageInfo values from citations, empty mapped to null.
        return (cg.getCitationsInLocalOrder().stream()
                .map(cit -> cit.pageInfo.orElse(null))
                .collect(Collectors.toList()));
    }

    /*
     * indexInGlobalOrder
     */

    public void setIndexInGlobalOrder(Optional<Integer> i) {
        this.indexInGlobalOrder = i;
    }

    public Optional<Integer> getIndexInGlobalOrder() {
        return this.indexInGlobalOrder;
    }

    /*
     * referenceMarkNameForLinking
     */

    public Optional<String> getReferenceMarkNameForLinking() {
        return referenceMarkNameForLinking;
    }

    public void setReferenceMarkNameForLinking(Optional<String> referenceMarkNameForLinking) {
        this.referenceMarkNameForLinking = referenceMarkNameForLinking;
    }
} // class CitationGroup
