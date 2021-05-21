package org.jabref.model.oostyle;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.openoffice.StorageBase;

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
        this.localOrder = ListUtil.makeIndices(citationsInStorageOrder.size());
        this.referenceMarkNameForLinking = referenceMarkNameForLinking;
        this.indexInGlobalOrder = Optional.empty();
    }

    public int numberOfCitations() {
        return citationsInStorageOrder.size();
    }

    /*
     * localOrder
     */

    /**
     * Sort citations for presentation within a CitationGroup.
     */
    void imposeLocalOrderByComparator(Comparator<BibEntry> entryComparator) {

        final int nCitations = citationsInStorageOrder.size();

        // For JabRef52 the single pageInfo is always in the last-in-localorder citation.
        // We adjust here accordingly by taking it out and adding it back after sorting.
        final int last = nCitations - 1;
        Optional<OOFormattedText> lastPageInfo = Optional.empty();
        if (dataModel == OOStyleDataModelVersion.JabRef52) {
            Citation lastCitation = getCitationsInLocalOrder().get(last);
            lastPageInfo = lastCitation.getPageInfo();
            lastCitation.setPageInfo(Optional.empty());
        }

        this.localOrder = ListUtil.order(citationsInStorageOrder,
                                         new CitationSort.CitationComparator(entryComparator, true));

        if (dataModel == OOStyleDataModelVersion.JabRef52) {
            getCitationsInLocalOrder().get(last).setPageInfo(lastPageInfo);
        }
    }

    public List<Integer> getLocalOrder() {
        return Collections.unmodifiableList(localOrder);
    }

    /*
     * citations
     */

    public List<Citation> getCitationsInLocalOrder() {
        return ListUtil.map(localOrder, i -> citationsInStorageOrder.get(i));
    }

    /*
     * Values of the number fields of the citations according to
     * localOrder.
     */
    public List<Integer> getCitationNumbersInLocalOrder() {
        return ListUtil.map(localOrder, i -> (citationsInStorageOrder.get(i)
                                              .getNumber()
                                              .orElseThrow(RuntimeException::new)));
    }

    /**
     * @return List of pageInfo values, one for each citation, in
     *         storage order.
     */
    public List<Optional<OOFormattedText>> getPageInfosForCitationsInStorageOrder() {
        return ListUtil.map(citationsInStorageOrder, Citation::getPageInfo);
    }

    /**
     * @return List of optional pageInfo values, one for each citation, in localOrder.
     */
    public List<Optional<OOFormattedText>> getPageInfosForCitationsInLocalOrder() {
        return ListUtil.map(getCitationsInLocalOrder(), Citation::getPageInfo);
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
