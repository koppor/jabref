package org.jabref.gui.openoffice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jabref.model.entry.BibEntry;

class CitationGroup {
    CitationGroupID cgid;
    StorageBase.NamedRange cgRangeStorage; // knows referenceMarkName
    int itcType;
    List<Citation> citations;
    List<Integer> localOrder;

    /** For Compat.DataModel.JabRef52 pageInfo belongs to the group */
    Optional<String> pageInfo;

    /**
     * Locator in document, replaced with cgRangeStorage
     * TODO: replace referenceMarkName with
     *       getReferenceMarkName(){ return backed.cgRangeStorage.getName(); }
     */
    String referenceMarkName;

    CitationGroup(CitationGroupID cgid,
                  StorageBase.NamedRange cgRangeStorage,
                  int itcType,
                  List<Citation> citations,
                  Optional<String> pageInfo,
                  String referenceMarkName) {
        this.cgid = cgid;
        this.cgRangeStorage = cgRangeStorage;
        this.itcType = itcType;
        this.citations = citations;
        this.pageInfo = pageInfo;
        this.referenceMarkName = referenceMarkName;
        this.localOrder = makeIndices(citations.size());
    }

    /** Integers 0..(n-1) */
    static List<Integer> makeIndices(int n) {
        return Stream.iterate(0, i -> i + 1).limit(n).collect(Collectors.toList());
    }

    List<Citation> getSortedCitations() {
        List<Citation> res = new ArrayList<>(citations.size());
        for (int i : localOrder) {
            res.add(citations.get(i));
        }
        return res;
    }

    List<Integer> getSortedNumbers() {
        List<Citation> cits = getSortedCitations();
        return (cits.stream()
                .map(cit -> cit.number.orElseThrow(RuntimeException::new))
                .collect(Collectors.toList()));
    }

    class CitationAndIndex implements CitationSort.ComparableCitation {
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
        public Optional<String> getPageInfo() {
            return c.pageInfo;
        }
    }

    /**
     * Sort citations for presentation within a CitationGroup.
     */
    void imposeLocalOrderByComparator(Comparator<BibEntry> entryComparator) {
        List<CitationAndIndex> cks = new ArrayList<>();
        for (int i = 0; i < citations.size(); i++) {
            Citation c = citations.get(i);
            cks.add(new CitationAndIndex(c, i));
        }
        cks.sort(new CitationSort.CitationComparator(entryComparator, true));

        List<Integer> o = new ArrayList<>();
        for (CitationAndIndex ck : cks) {
            o.add(ck.i);
        }
        this.localOrder = o;
    }
} // class CitationGroup
