package org.jabref.model.oostyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private Map<CitationGroupID, CitationGroup> citationGroupsUnordered;

    /**
     * Provides order of appearance for the citation groups.
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
    public <T> void distributeToCitations(List<CitationPath> where,
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

    /*
     * Look up each Citation in databases.
     */
    public void lookupCitations(List<BibDatabase> databases) {
        CitationGroups cgs = this;
        /*
         * It is not clear which of the two solutions below is better.
         */
        if (true) {
            // collect-lookup-distribute
            //
            // CitationDatabaseLookupResult for the same citation key
            // is the same object. Until we insert a new citation from the GUI.
            CitedKeys cks = cgs.getCitedKeysUnordered();
            cks.lookupInDatabases(databases);
            cks.distributeLookupResults(cgs);
        } else {
            // lookup each citation directly
            //
            // CitationDatabaseLookupResult for the same citation key
            // may be a different object: CitedKey.addPath has to use equals,
            // so CitationDatabaseLookupResult has to override Object.equals,
            // which depends on BibEntry.equals and BibDatabase.equals
            // doing the right thing. Seems to work. But what we gained
            // from avoiding collect-and-distribute may be lost in more
            // complicated consistency checking in addPath.
            //
            for (CitationGroup cg : getCitationGroupsUnordered()) {
                for (Citation cit : cg.citationsInStorageOrder) {
                    cit.lookupInDatabases(databases);
                }
            }
        }
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

    /**
     * Impose an order of citation groups by providing the order
     * of their citation group idendifiers.
     *
     * Also set indexInGlobalOrder for each citation group.
     */
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

    public boolean hasGlobalOrder() {
        return globalOrder.isPresent();
    }

    /**
     * Impose an order for citations within each group.
     */
    public void imposeLocalOrder(Comparator<BibEntry> entryComparator) {
        for (CitationGroup cg : citationGroupsUnordered.values()) {
            cg.imposeLocalOrder(entryComparator);
        }
    }

    /**
     * Collect citations into a list of cited sources using neither
     * CitationGroup.globalOrder or Citation.localOrder
     */
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

    /**
     * CitedKeys created iterating citations in (globalOrder,localOrder)
     */
    public CitedKeys getCitedKeysSortedInOrderOfAppearance() {
        LinkedHashMap<String, CitedKey> res = new LinkedHashMap<>();
        if (!hasGlobalOrder()) {
            throw new RuntimeException("getSortedCitedKeys: no globalOrder");
        }
        for (CitationGroup cg : getCitationGroupsInGlobalOrder()) {
            for (int i : cg.getLocalOrder()) {
                Citation cit = cg.citationsInStorageOrder.get(i);
                String citationKey = cit.citationKey;
                CitationPath p = new CitationPath(cg.cgid, i);
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

    /**
     * @return Citation keys where lookupCitations() failed.
     */
    public List<String> getUnresolvedKeys() {

        CitedKeys bib = getBibliography().orElse(getCitedKeysUnordered());

        List<String> unresolvedKeys = new ArrayList<>();
        for (CitedKey ck : bib.values()) {
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

    /*
     * Query by CitationGroupID
     */

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

    /*
     * Callbacks.
     */

    public void afterCreateCitationGroup(CitationGroup cg) {
        this.citationGroupsUnordered.put(cg.cgid, cg);

        this.globalOrder = Optional.empty();
        this.bibliography = Optional.empty();
    }

    public void afterRemoveCitationGroup(CitationGroup cg) {
        this.citationGroupsUnordered.remove(cg.cgid);
        this.globalOrder.map(l -> l.remove(cg.cgid));

        this.bibliography = Optional.empty();
    }

}
