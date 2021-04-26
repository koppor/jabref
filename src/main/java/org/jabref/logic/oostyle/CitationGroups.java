package org.jabref.logic.oostyle;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CitationGroups : the set of citation groups in the document.
 *
 *
 */
public class CitationGroups {

    private static final Logger LOGGER = LoggerFactory.getLogger(CitationGroups.class);

    private final Compat.DataModel dataModel;

    /**
     *  Original CitationGroups Data
     */
    private Map<CitationGroupID, CitationGroup> citationGroups;

    /*
     *  Extra Data
     */

    /**
     * Provides order of appearance for the citation groups.
     *
     * Background: just getting the reference marks does not provide
     *    this. Citation groups in footnotes, frames etc make getting
     *    this order right tricky. {@see class RangeSortVisual}
     */
    private Optional<List<CitationGroupID>> globalOrder;

    /**
     *  This is going to be the bibliography
     */
    private Optional<CitedKeys> bibliography;

    /**
     * Constructor
     */
    public CitationGroups(Compat.DataModel dataModel,
                          Map<CitationGroupID, CitationGroup> citationGroups) {

        this.dataModel = dataModel;
        this.citationGroups = citationGroups;

        // Now we have almost every information from the document about citations.
        // What is left out: the ranges controlled by the reference marks.
        // But (I guess) those change too easily, so we only ask when actually needed.

        this.globalOrder = Optional.empty();
        this.bibliography = Optional.empty();
    }

    public void setDatabaseLookupResults(Set<CitationPath> where,
                                         Optional<CitationDatabaseLookup.Result> db) {
        for (CitationPath p : where) {
            CitationGroup cg = this.citationGroups.get(p.group);
            if (cg == null) {
                LOGGER.warn("CitationGroups.setDatabaseLookupResult: group missing");
                continue;
            }
            Citation cit = cg.citations.get(p.storageIndexInGroup);
            cit.db = db;
        }
    }

    public CitedKeys lookupEntriesInDatabases(List<BibDatabase> databases) {
        CitationGroups cgs = this;

        CitedKeys cks = cgs.getCitedKeys();

        cks.lookupInDatabases(databases);
        cks.distributeDatabaseLookupResults(cgs);
        return cks;
    }

    public void setNumbers(Set<CitationPath> where,
                           Optional<Integer> number) {
        for (CitationPath p : where) {
            CitationGroup cg = this.citationGroups.get(p.group);
            if (cg == null) {
                LOGGER.warn("CitationGroups.setNumbers: group missing");
                continue;
            }
            Citation cit = cg.citations.get(p.storageIndexInGroup);
                cit.number = number;
        }
    }

    public void setUniqueLetters(Set<CitationPath> where,
                                 Optional<String> uniqueLetter) {
        for (CitationPath p : where) {
            CitationGroup cg = this.citationGroups.get(p.group);
            if (cg == null) {
                LOGGER.warn("CitationGroups.setUniqueLetters: group missing");
                continue;
            }
            Citation cit = cg.citations.get(p.storageIndexInGroup);
            cit.uniqueLetter = uniqueLetter;
        }
    }

    public void imposeLocalOrderByComparator(Comparator<BibEntry> entryComparator) {
        for (CitationGroup cg : citationGroups.values()) {
            cg.imposeLocalOrderByComparator(entryComparator);
        }
    }

    public CitedKeys getCitedKeys() {
        LinkedHashMap<String, CitedKey> res = new LinkedHashMap<>();
        for (CitationGroup cg : citationGroups.values()) {
            int storageIndexInGroup = 0;
            for (Citation cit : cg.citations) {
                String key = cit.citationKey;
                CitationPath p = new CitationPath(cg.cgid, storageIndexInGroup);
                if (res.containsKey(key)) {
                    res.get(key).addPath(p, cit);
                } else {
                    res.put(key, new CitedKey(key, p, cit));
                }
                storageIndexInGroup++;
            }
        }
        return new CitedKeys(res);
    }

    public boolean hasGlobalOrder() {
        return globalOrder.isPresent();
    }

    /**
     * CitedKeys created iterating citations in (globalOrder,localOrder)
     */
    public CitedKeys getCitedKeysSortedInOrderOfAppearance() {
        LinkedHashMap<String, CitedKey> res = new LinkedHashMap<>();
        if (globalOrder.isEmpty()) {
            throw new RuntimeException("getSortedCitedKeys: no globalOrder");
        }
        for (CitationGroupID cgid : globalOrder.get()) {
            CitationGroup cg = getCitationGroup(cgid)
                .orElseThrow(RuntimeException::new);
            for (int i : cg.localOrder) {
                Citation cit = cg.citations.get(i);
                String citationKey = cit.citationKey;
                CitationPath p = new CitationPath(cgid, i);
                if (res.containsKey(citationKey)) {
                    res.get(citationKey).addPath(p, cit);
                } else {
                    res.put(citationKey, new CitedKey(citationKey, p, cit));
                }
            }
        }
        return new CitedKeys(res);
    }

    public Optional<CitedKeys> getBibliography() {
        return bibliography;
    }

    public void createNumberedBibliographySortedInOrderOfAppearance() {
        CitationGroups cgs = this;
        if (!cgs.bibliography.isEmpty()) {
            throw new RuntimeException("createNumberedBibliographySortedInOrderOfAppearance:"
                                       + " already have a bibliography");
        }
        CitedKeys sortedCitedKeys = cgs.getCitedKeysSortedInOrderOfAppearance();
        sortedCitedKeys.numberCitedKeysInCurrentOrder();
        sortedCitedKeys.distributeNumbers(cgs);
        cgs.bibliography = Optional.of(sortedCitedKeys);
    }

    /**
     * precondition: database lookup already performed (otherwise we just sort citation keys)
     */
    public void createPlainBibliographySortedByComparator(Comparator<BibEntry> entryComparator) {
        CitationGroups cgs = this;
        if (!this.bibliography.isEmpty()) {
            throw new RuntimeException("createPlainBibliographySortedByComparator:"
                                       + " already have a bibliography");
        }
        CitedKeys citedKeys = cgs.getCitedKeys();
        citedKeys.sortByComparator(entryComparator);
        this.bibliography = Optional.of(citedKeys);
    }

    /**
     * precondition: database lookup already performed (otherwise we just sort citation keys)
     */
    public void createNumberedBibliographySortedByComparator(Comparator<BibEntry> entryComparator) {
        CitationGroups cgs = this;
        if (!cgs.bibliography.isEmpty()) {
            throw new RuntimeException("createNumberedBibliographySortedByComparator:"
                                       + " already have a bibliography");
        }
        CitedKeys citedKeys = cgs.getCitedKeys();
        citedKeys.sortByComparator(entryComparator);
        citedKeys.numberCitedKeysInCurrentOrder();
        citedKeys.distributeNumbers(cgs);
        this.bibliography = Optional.of(citedKeys);
    }

    public Set<CitationGroupID> getCitationGroupIDs() {
        return citationGroups.keySet();
    }

    /**
     * Citation group IDs in {@code globalOrder}
     */
    public List<CitationGroupID> getSortedCitationGroupIDs() {
        if (globalOrder.isEmpty()) {
            throw new RuntimeException("getSortedCitationGroupIDs: not ordered yet");
        }
        return globalOrder.get();
    }

    public void setGlobalOrder(List<CitationGroupID> globalOrder) {
        Objects.requireNonNull(globalOrder);
        if (globalOrder.size() != citationGroups.size()) {
            throw new RuntimeException("setGlobalOrder:"
                                       + " globalOrder.size() != citationGroups.size()");
        }
        this.globalOrder = Optional.of(globalOrder);
    }

    public Optional<CitationGroup> getCitationGroup(CitationGroupID cgid) {
        CitationGroup e = citationGroups.get(cgid);
        return Optional.ofNullable(e);
    }

    /**
     * Call this when the citation group is unquestionably there.
     */
    public CitationGroup getCitationGroupOrThrow(CitationGroupID cgid) {
        CitationGroup e = citationGroups.get(cgid);
        if (e == null) {
            throw new RuntimeException("getCitationGroupOrThrow:"
                                       + " the requested CitationGroup is not available");
        }
        return e;
    }

    private Optional<Integer> getItcType(CitationGroupID cgid) {
        return getCitationGroup(cgid).map(cg -> cg.itcType);
    }

    public int numberOfCitationGroups() {
        return citationGroups.size();
    }

    public Optional<OOFormattedText> getPageInfo(CitationGroupID cgid) {
        return (getCitationGroup(cgid)
                .map(cg -> cg.pageInfo)
                .flatMap(x -> x));
    }

    public List<OOFormattedText> getPageInfosForCitations(CitationGroup cg) {
        return Compat.getPageInfosForCitations(this.dataModel, cg);
    }

    public List<OOFormattedText> getPageInfosForCitations(CitationGroupID cgid) {
        CitationGroup cg = getCitationGroupOrThrow(cgid);
        return getPageInfosForCitations(cg);
    }

    public Optional<List<Citation>> getCitations(CitationGroupID cgid) {
        return getCitationGroup(cgid).map(cg -> cg.citations);
    }

    public List<Citation> getSortedCitations(CitationGroupID cgid) {
        Optional<CitationGroup> cg = getCitationGroup(cgid);
        if (cg.isEmpty()) {
            throw new RuntimeException("getSortedCitations: invalid cgid");
        }
        return cg.get().getSortedCitations();
    }

    public void afterCreateCitationGroup(CitationGroup cg) {

        // add to our data
        this.citationGroups.put(cg.cgid, cg);
        // invalidate globalOrder.
        // TODO: look out for localOrder!
        this.globalOrder = Optional.empty();
    }

    public void afterRemoveCitationGroup(CitationGroup cg) {

        this.citationGroups.remove(cg.cgid);

        // Update what we can.
        this.globalOrder.map(l -> l.remove(cg.cgid));
        // Invalidate what we cannot
        this.bibliography = Optional.empty();
        // Could also: reset citation.number, citation.uniqueLetter.
    }

    /**
     *  This is for debugging, can be removed.
     */
    public void xshow() {
        System.out.printf("CitationGroups%n");
        System.out.printf("  citationGroups.size: %d%n", citationGroups.size());
        System.out.printf("  globalOrder: %s%n",
                          (globalOrder.isEmpty()
                           ? "isEmpty"
                           : String.format("%d", globalOrder.get().size())));
    }

}
