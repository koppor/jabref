package org.jabref.model.oostyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.openoffice.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CitationGroups : the set of citation groups in the document.
 */
public class CitationGroups {

    private static final Logger LOGGER = LoggerFactory.getLogger(CitationGroups.class);

    /**
     *  Original CitationGroups Data
     */
    private Map<CitationGroupID, CitationGroup> citationGroupsUnordered;

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
    public CitationGroups(Map<CitationGroupID, CitationGroup> citationGroups) {

        this.citationGroupsUnordered = citationGroups;

        this.globalOrder = Optional.empty();
        this.bibliography = Optional.empty();
    }

    public int numberOfCitationGroups() {
        return citationGroupsUnordered.size();
    }

    /**
     * For each citation in {@code where}
     * call {@code fun.accept(new Pair(citation, value));}
     */
    public <T> void distributeToCitations(Set<CitationPath> where,
                                          Consumer<Pair<Citation, T>> fun,
                                          T value) {

        for (CitationPath p : where) {
            CitationGroup cg = this.citationGroupsUnordered.get(p.group);
            if (cg == null) {
                LOGGER.warn("CitationGroups.distributeToCitations: group missing");
                continue;
            }
            Citation cit = cg.citationsInStorageOrder.get(p.storageIndexInGroup);
            fun.accept(new Pair(cit, value));
        }
    }

    public CitedKeys lookupCitations(List<BibDatabase> databases) {
        CitationGroups cgs = this;
        CitedKeys cks = cgs.getCitedKeysUnordered();

        cks.lookupInDatabases(databases);
        cks.distributeLookupResults(cgs);
        return cks;
    }

    public List<CitationGroup> getCitationGroupsUnordered() {
        return new ArrayList<>(citationGroupsUnordered.values());
    }

    /**
     * Citation groups in {@code globalOrder}
     */
    public List<CitationGroup> getCitationGroupsInGlobalOrder() {
        if (globalOrder.isEmpty()) {
            throw new RuntimeException("getCitationGroupsInGlobalOrder: not ordered yet");
        }
        return OOListUtil.map(globalOrder.get(), cgid -> citationGroupsUnordered.get(cgid));
    }

    public void setGlobalOrder(List<CitationGroupID> globalOrder) {
        Objects.requireNonNull(globalOrder);
        if (globalOrder.size() != numberOfCitationGroups()) {
            throw new RuntimeException("setGlobalOrder:"
                                       + " globalOrder.size() != numberOfCitationGroups()");
        }
        this.globalOrder = Optional.of(globalOrder);

        // Propagate to each CitationGroup
        int i = 0;
        for (CitationGroupID cgid : globalOrder) {
            citationGroupsUnordered.get(cgid).setIndexInGlobalOrder(Optional.of(i));
            i++;
        }
    }

    public void imposeLocalOrder(Comparator<BibEntry> entryComparator) {
        for (CitationGroup cg : citationGroupsUnordered.values()) {
            cg.imposeLocalOrder(entryComparator);
        }
    }

    public CitedKeys getCitedKeysUnordered() {
        LinkedHashMap<String, CitedKey> res = new LinkedHashMap<>();
        for (CitationGroup cg : citationGroupsUnordered.values()) {
            int storageIndexInGroup = 0;
            for (Citation cit : cg.citationsInStorageOrder) {
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
        if (!hasGlobalOrder()) {
            throw new RuntimeException("getSortedCitedKeys: no globalOrder");
        }
        for (CitationGroupID cgid : globalOrder.get()) {
            CitationGroup cg = getCitationGroup(cgid).orElseThrow(RuntimeException::new);
            for (int i : cg.getLocalOrder()) {
                Citation cit = cg.citationsInStorageOrder.get(i);
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

    public List<String> getUnresolvedKeys() {
        Optional<CitedKeys> bib = getBibliography();
        if (bib.isEmpty()) {
            throw new RuntimeException("getUnresolvedKeys:"
                                       + " CitationGroups does not have a bibliography");
        }
        List<String> unresolvedKeys = new ArrayList<>();
        for (CitedKey ck : bib.get().values()) {
            if (ck.getLookupResult().isEmpty()) {
                unresolvedKeys.add(ck.citationKey);
            }
        }
        return unresolvedKeys;
    }

    public void createNumberedBibliographySortedInOrderOfAppearance() {
        CitationGroups cgs = this;
        if (!cgs.bibliography.isEmpty()) {
            throw new RuntimeException("createNumberedBibliographySortedInOrderOfAppearance:"
                                       + " already have a bibliography");
        }
        CitedKeys citedKeys = cgs.getCitedKeysSortedInOrderOfAppearance();
        citedKeys.numberCitedKeysInCurrentOrder();
        citedKeys.distributeNumbers(cgs);
        cgs.bibliography = Optional.of(citedKeys);
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
        CitedKeys citedKeys = cgs.getCitedKeysUnordered();
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
        CitedKeys citedKeys = cgs.getCitedKeysUnordered();
        citedKeys.sortByComparator(entryComparator);
        citedKeys.numberCitedKeysInCurrentOrder();
        citedKeys.distributeNumbers(cgs);
        this.bibliography = Optional.of(citedKeys);
    }

    public Optional<CitationGroup> getCitationGroup(CitationGroupID cgid) {
        CitationGroup cg = citationGroupsUnordered.get(cgid);
        return Optional.ofNullable(cg);
    }

    /**
     * Call this when the citation group is unquestionably there.
     */
    public CitationGroup getCitationGroupOrThrow(CitationGroupID cgid) {
        CitationGroup cg = citationGroupsUnordered.get(cgid);
        if (cg == null) {
            throw new RuntimeException("getCitationGroupOrThrow:"
                                       + " the requested CitationGroup is not available");
        }
        return cg;
    }

    private Optional<InTextCitationType> getCitationType(CitationGroupID cgid) {
        return getCitationGroup(cgid).map(cg -> cg.citationType);
    }

    public Optional<List<Citation>> getCitationsInStorageOrder(CitationGroupID cgid) {
        return getCitationGroup(cgid).map(cg -> cg.citationsInStorageOrder);
    }

    public List<Citation> getCitationsInLocalOrder(CitationGroupID cgid) {
        Optional<CitationGroup> cg = getCitationGroup(cgid);
        if (cg.isEmpty()) {
            throw new RuntimeException("getCitationsInLocalOrder: invalid cgid");
        }
        return cg.get().getCitationsInLocalOrder();
    }

    /*
     * @return true if all citation groups have referenceMarkNameForLinking
     */
    public boolean citationGroupsProvideReferenceMarkNameForLinking() {
        for (CitationGroup cg : citationGroupsUnordered.values()) {
            if (cg.getReferenceMarkNameForLinking().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void afterCreateCitationGroup(CitationGroup cg) {
        // add to our data
        this.citationGroupsUnordered.put(cg.cgid, cg);
        // invalidate globalOrder.
        this.globalOrder = Optional.empty();
        // Note: we cannot impose localOrder, since we do not know
        // how it was imposed. We leave it to an upper level.
    }

    /*
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
    public void afterRemoveCitationGroup(CitationGroup cg) {

        this.citationGroupsUnordered.remove(cg.cgid);

        // Update what we can.
        this.globalOrder.map(l -> l.remove(cg.cgid));
        // Invalidate what we cannot
        this.bibliography = Optional.empty();
        // Could also: reset citation.number, citation.uniqueLetter.
        // Proper update would need style, we do not do it here.
    }

}
