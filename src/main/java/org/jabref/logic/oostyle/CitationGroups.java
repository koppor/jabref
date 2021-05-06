package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.CitationDatabaseLookup;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.OOFormattedText;
import org.jabref.model.oostyle.OOStyleDataModelVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CitationGroups : the set of citation groups in the document.
 *
 *
 */
public class CitationGroups {

    private static final Logger LOGGER = LoggerFactory.getLogger(CitationGroups.class);

    private final OOStyleDataModelVersion dataModel;

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
    public CitationGroups(OOStyleDataModelVersion dataModel,
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

    private Optional<InTextCitationType> getItcType(CitationGroupID cgid) {
        return getCitationGroup(cgid).map(cg -> cg.itcType);
    }

    public int numberOfCitationGroups() {
        return citationGroups.size();
    }

    /**
     * @return List of nullable pageInfo values, one for each citation,
     *         instrage order.
     *
     *         Result contains null for missing pageInfo values.
     *         The list itself is not null.
     *
     *         For JabRef52 compatibility the last citation in
     *         localOrder gets the single pageInfo from the last in
     *         storage order, to make sure it is presented after the citations.
     *         This can only be done after localOrder is set.
     *
     *         The result is passed to OOBibStyle.getCitationMarker or
     *         OOBibStyle.getNumCitationMarker
     *
     *         TODO: we may want class DataModel52, DataModel53 and split this.
     */
    private static List<OOFormattedText> getPageInfosForCitations(OOStyleDataModelVersion dataModel,
                                                                  CitationGroup cg) {
        switch (dataModel) {
        case JabRef52:
            // check conformance to dataModel
            final int nCitations = cg.citations.size();
            for (int i = 0; i < nCitations - 1; i++) {
                if (cg.citations.get(i).pageInfo.isPresent()) {
                    throw new RuntimeException("getPageInfosForCitations:"
                                               + " found Citation.pageInfo"
                                               + " outside last citation under JabRef52 dataModel");
                }
            }
            // A list of null values, except the last that comes from this.pageInfo
            List<OOFormattedText> result = new ArrayList<>(cg.citations.size());
            for (int i = 0; i < nCitations; i++) {
                int j = cg.localOrder.get(i);
                OOFormattedText value = cg.citations.get(j).pageInfo.orElse(null);
                result.add(value);
            }
            return result;
        case JabRef53:
            // pageInfo values from citations, empty mapped to null.
            return (cg.citations.stream()
                    .map(cit -> cit.pageInfo.orElse(null))
                    .collect(Collectors.toList()));

        default:
            throw new RuntimeException("getPageInfosForCitations:"
                                       + "unhandled dataModel");
        }
    }

    public List<OOFormattedText> getPageInfosForCitations(CitationGroup cg) {
        return getPageInfosForCitations(this.dataModel, cg);
    }

    public List<OOFormattedText> getPageInfosForCitations(CitationGroupID cgid) {
        CitationGroup cg = getCitationGroupOrThrow(cgid);
        return getPageInfosForCitations(cg);
    }

    public Optional<List<Citation>> getCitations(CitationGroupID cgid) {
        return getCitationGroup(cgid).map(cg -> cg.citations);
    }

    public List<Citation> getCitationsInLocalOrder(CitationGroupID cgid) {
        Optional<CitationGroup> cg = getCitationGroup(cgid);
        if (cg.isEmpty()) {
            throw new RuntimeException("getCitationsInLocalOrder: invalid cgid");
        }
        return cg.get().getCitationsInLocalOrder();
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
