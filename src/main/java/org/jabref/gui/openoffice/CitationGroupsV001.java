package org.jabref.gui.openoffice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;

import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XNameAccess;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XFootnote;
import com.sun.star.text.XText;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextRange;
import com.sun.star.uno.UnoRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * At the start of GUI actions we may want to check the state of the document.
 *
 * Operations:
 *   createCitationGroup
 *   deleteCitationGroup
 *
 */
class CitationGroupsV001 {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(CitedKeys.class);

    /**
     * This is what we get back from parsing a refMarkName.
     *
     */
    private static class ParsedRefMark {
        /**  "", "0", "1" ... */
        public String i;
        /** in-text-citation type */
        public int itcType;
        /** Citation keys embedded in the reference mark. */
        public List<String> citationKeys;

        ParsedRefMark(String i, int itcType, List<String> citationKeys) {
            this.i = i;
            this.itcType = itcType;
            this.citationKeys = citationKeys;
        }
    }

    private static class Codec {
        private static final String BIB_CITATION = "JR_cite";
        private static final Pattern CITE_PATTERN =
            Pattern.compile(BIB_CITATION + "(\\d*)_(\\d*)_(.*)");


        /**
         * Produce a reference mark name for JabRef for the given citation
         * key and itcType that does not yet appear among the reference
         * marks of the document.
         *
         * @param bibtexKey The citation key.
         * @param itcType   Encodes the effect of withText and
         *                  inParenthesis options.
         *
         * The first occurrence of bibtexKey gets no serial number, the
         * second gets 0, the third 1 ...
         *
         * Or the first unused in this series, after removals.
         */
        public static String getUniqueReferenceMarkName(DocumentConnection documentConnection,
                                                        String bibtexKey,
                                                        int itcType)
            throws NoDocumentException {

            XNameAccess xNamedRefMarks = documentConnection.getReferenceMarks();
            int i = 0;
            String name = BIB_CITATION + '_' + itcType + '_' + bibtexKey;
            while (xNamedRefMarks.hasByName(name)) {
                name = BIB_CITATION + i + '_' + itcType + '_' + bibtexKey;
                i++;
            }
            return name;
        }

        /**
         * Parse a JabRef reference mark name.
         *
         * @return Optional.empty() on failure.
         *
         */
        private static Optional<ParsedRefMark> parseRefMarkName(String refMarkName) {

            Matcher citeMatcher = CITE_PATTERN.matcher(refMarkName);
            if (!citeMatcher.find()) {
                return Optional.empty();
            }

            List<String> keys = Arrays.asList(citeMatcher.group(3).split(","));
            String i = citeMatcher.group(1);
            int itcType = Integer.parseInt(citeMatcher.group(2));
            return (Optional.of(new CitationGroupsV001.ParsedRefMark(i, itcType, keys)));
        }

        /**
         * Extract the list of citation keys from a reference mark name.
         *
         * @param name The reference mark name.
         * @return The list of citation keys encoded in the name.
         *
         *         In case of duplicated citation keys,
         *         only the first occurrence.
         *         Otherwise their order is preserved.
         *
         *         If name does not match CITE_PATTERN,
         *         an empty list of strings is returned.
         */
        private static List<String> parseRefMarkNameToUniqueCitationKeys(String name) {
            Optional<ParsedRefMark> op = parseRefMarkName(name);
            return (op.map(parsedRefMark ->
                           parsedRefMark.citationKeys.stream()
                           .distinct()
                           .collect(Collectors.toList()))
                    .orElseGet(ArrayList::new));
        }

        /**
         * @return true if name matches the pattern used for JabRef
         * reference mark names.
         */
        private static boolean isJabRefReferenceMarkName(String name) {
            return (CITE_PATTERN.matcher(name).find());
        }

        /**
         * Filter a list of reference mark names by `isJabRefReferenceMarkName`
         *
         * @param names The list to be filtered.
         */
        private static List<String> filterIsJabRefReferenceMarkName(List<String> names) {
            return (names
                    .stream()
                    .filter(CitationGroupsV001.Codec::isJabRefReferenceMarkName)
                    .collect(Collectors.toList()));
        }
        /**
         * Get reference mark names from the document matching the pattern
         * used for JabRef reference mark names.
         *
         * Note: the names returned are in arbitrary order.
         *
         *
         *
         */
        private static List<String> getJabRefReferenceMarkNames(StorageBase.NamedRangeManager manager,
                                                                DocumentConnection documentConnection)
            throws
            NoDocumentException {
            List<String> allNames = manager.getUsedNames(documentConnection);
            return filterIsJabRefReferenceMarkName(allNames);
        }

        /**
         * For each name in referenceMarkNames set types[i] and
         * bibtexKeys[i] to values parsed from referenceMarkNames.get(i)
         *
         * @param referenceMarkNames Should only contain parsable names.
         * @param types              OUT Must be same length as referenceMarkNames.
         * @param bibtexKeys         OUT First level must be same length as referenceMarkNames.
         */
        private static void parseRefMarkNamesToArrays(List<String> referenceMarkNames,
                                                      int[] types,
                                                      String[][] bibtexKeys) {

            final int nRefMarks = referenceMarkNames.size();
            assert (types.length == nRefMarks);
            assert (bibtexKeys.length == nRefMarks);
            for (int i = 0; i < nRefMarks; i++) {
                final String name = referenceMarkNames.get(i);
                Optional<ParsedRefMark> op = parseRefMarkName(name);
                if (op.isEmpty()) {
                    // We have a problem. We want types[i] and bibtexKeys[i]
                    // to correspond to referenceMarkNames.get(i).
                    // And do not want null in bibtexKeys (or error code in types)
                    // on return.
                    throw new IllegalArgumentException(
                        "parseRefMarkNamesToArrays expects parsable referenceMarkNames");
                }
                ParsedRefMark ov = op.get();
                types[i] = ov.itcType;
                bibtexKeys[i] = ov.citationKeys.toArray(String[]::new);
            }
        }

    }

    static class CitationGroupID {
        String id;
        CitationGroupID(String id) {
            this.id = id;
        }

        /**
         *  CitationEntry needs refMark or other identifying string
         */
        String asString() {
            return id;
        }
    }

    static class Citation  implements CitationSort.ComparableCitation {

        /** key in database */
        String citationKey;
        /** Result from database lookup. Optional.empty() if not found. */
        Optional<CitationDatabaseLookup.Result> db;
        /** The number used for numbered citation styles . */
        Optional<Integer> number;
        /** Letter that makes the in-text citation unique. */
        Optional<String> uniqueLetter;

        /* missing: something that differentiates this from other
         * citations of the same citationKey. In particular, a
         * CitationGroup may contain multiple citations of the same
         * source. We use CitationPath.storageIndexInGroup to refer to
         * citations.
         */

        Citation(String citationKey) {
            this.citationKey = citationKey;
            this.db = Optional.empty();
            this.number = Optional.empty();
            this.uniqueLetter = Optional.empty();
        }

        @Override
        public String getCitationKey(){
            return citationKey;
        }

        @Override
        public Optional<BibEntry> getBibEntry(){
            return (db.isPresent()
                    ? Optional.of(db.get().entry)
                    : Optional.empty());
        }
    }

    public static class CitationGroup {
        CitationGroupID cgid;
        StorageBase.NamedRange cgRangeStorage;
        int itcType;
        List<Citation> citations;
        List<Integer> localOrder;
        // Currently pageInfo belongs to the group
        Optional<String> pageInfo;

        /**
         * Locator in document
         */
        String referenceMarkName;

        CitationGroup(
            CitationGroupID cgid,
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

        List<Citation>
        getSortedCitations() {
            List<Citation> res = new ArrayList<>(citations.size());
            for (int i : localOrder) {
                res.add(citations.get(i));
            }
            return res;
        }

        List<Integer>
        getSortedNumbers() {
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
            public String getCitationKey(){
                return c.getCitationKey();
            }

            @Override
            public Optional<BibEntry> getBibEntry(){
                return c.getBibEntry();
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

    /**
     * 
     */
    public static class CitationGroups {
        private StorageBase.NamedRangeManager citationStorageManager;

        /**
         *  Original CitationGroups Data
         */
        private Map<CitationGroupID, CitationGroup> citationGroups;

        /**
         *  Extra Data
         */
        // For custom properties belonging to us, but
        // without a corresponding reference mark.
        // These can be deleted.
        private List<String> pageInfoThrash;

        private Optional<List<CitationGroupID>> globalOrder;

        private Optional<CitedKeys> citedKeysAfterDatabaseLookup;

        /**
         *  This is going to be the bibliography
         */
        private Optional<CitedKeys> bibliography;

        /**
         * Constructor
         */
        public CitationGroups(DocumentConnection documentConnection)
            throws
            NoDocumentException,
            WrappedTargetException {
            
            this.citationStorageManager = new StorageBaseRefMark.Manager();

            // Get the citationGroupNames
            List<String> citationGroupNames =
                Codec.getJabRefReferenceMarkNames(this.citationStorageManager,
                                                  documentConnection);

            this.pageInfoThrash = findUnusedJabrefPropertyNames(documentConnection,
                                                                citationGroupNames);

            this.citationGroups = readCitationGroupsFromDocument(this.citationStorageManager,
                                                                 documentConnection,
                                                                 citationGroupNames);

            // Now we have almost every information from the document about citations.
            // What is left out: the ranges controlled by the reference marks.
            // But (I guess) those change too easily, so we only ask when actually needed.

            this.globalOrder = Optional.empty();
            this.citedKeysAfterDatabaseLookup = Optional.empty();
            this.bibliography = Optional.empty();
        }

        private static List<String> findUnusedJabrefPropertyNames(DocumentConnection documentConnection,
                                                                  List<String> citationGroupNames) {
            // Collect unused jabrefPropertyNames
            Set<String> citationGroupNamesSet =
                citationGroupNames.stream().collect(Collectors.toSet());

            List<String> pageInfoThrash = new ArrayList<>();
            List<String> jabrefPropertyNames =
                documentConnection.getCustomPropertyNames()
                .stream()
                .filter(CitationGroupsV001.Codec::isJabRefReferenceMarkName)
                .collect(Collectors.toList());
            for (String pn : jabrefPropertyNames) {
                if (!citationGroupNamesSet.contains(pn)) {
                    pageInfoThrash.add(pn);
                }
            }
            return pageInfoThrash;
        }

        private static Map<CitationGroupID, CitationGroup>
        readCitationGroupsFromDocument(StorageBase.NamedRangeManager citationStorageManager,
                                       DocumentConnection documentConnection,
                                       List<String> citationGroupNames)
            throws
            WrappedTargetException,
            NoDocumentException {

            Map<CitationGroupID, CitationGroup> citationGroups = new HashMap<>();
            for (int i = 0; i < citationGroupNames.size(); i++) {
                final String name = citationGroupNames.get(i);
                CitationGroup cg =
                    readCitationGroupFromDocumentOrThrow(citationStorageManager, documentConnection, name);
                citationGroups.put(cg.cgid, cg);
            }
            return citationGroups;
        }

        private static CitationGroup
        readCitationGroupFromDocumentOrThrow(StorageBase.NamedRangeManager citationStorageManager,
                                             DocumentConnection documentConnection,
                                             String refMarkName)
            throws
            WrappedTargetException,
            NoDocumentException {

            Optional<ParsedRefMark> op = Codec.parseRefMarkName(refMarkName);
            if (op.isEmpty()) {
                // We have a problem. We want types[i] and bibtexKeys[i]
                // to correspond to referenceMarkNames.get(i).
                // And do not want null in bibtexKeys (or error code in types)
                // on return.
                throw new IllegalArgumentException("readCitationGroupFromDocumentOrThrow:"
                                                   + " found unparsable referenceMarkName");
            }
            ParsedRefMark ov = op.get();
            CitationGroupID id = new CitationGroupID(refMarkName);
            List<Citation> citations = ((ov.citationKeys == null)
                                        ? new ArrayList<>()
                                        : (ov.citationKeys.stream()
                                           .map(Citation::new)
                                           .collect(Collectors.toList())));

            Optional<String> pageInfo = documentConnection.getCustomProperty(refMarkName);

            StorageBase.NamedRange sr = citationStorageManager.getFromDocumentOrNull(documentConnection,
                                                                                     refMarkName);

            if (sr == null) {
                throw new IllegalArgumentException(
                    "readCitationGroupFromDocumentOrThrow: referenceMarkName is not in the document");
            }

            CitationGroup cg = new CitationGroup(id,
                                                 sr,
                                                 ov.itcType,
                                                 citations,
                                                 pageInfo,
                                                 refMarkName);
            return cg;
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

        public CitationGroupsV001.CitedKeys lookupEntriesInDatabases(List<BibDatabase> databases) {
            CitationGroupsV001.CitationGroups cgs = this;

            CitationGroupsV001.CitedKeys cks = cgs.getCitedKeys();

            cks.lookupInDatabases(databases);
            cks.distributeDatabaseLookupResults(cgs);
            // record we did a database lookup
            // and allow extracting unresolved keys.
            this.citedKeysAfterDatabaseLookup = Optional.of(cks);
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

        Optional<CitedKeys> getBibliography() {
            return bibliography;
        }

        public void createNumberedBibliographySortedInOrderOfAppearance() {
            CitationGroupsV001.CitationGroups cgs = this;
            if (!cgs.bibliography.isEmpty()) {
                throw new RuntimeException(
                    "createNumberedBibliographySortedInOrderOfAppearance: already have a bibliography");
            }
            CitationGroupsV001.CitedKeys sortedCitedKeys =
                cgs.getCitedKeysSortedInOrderOfAppearance();
            sortedCitedKeys.numberCitedKeysInCurrentOrder();
            sortedCitedKeys.distributeNumbers(cgs);
            cgs.bibliography = Optional.of(sortedCitedKeys);
        }

        public void createNumberedBibliographySortedByComparator(Comparator<BibEntry> entryComparator) {
            CitationGroupsV001.CitationGroups cgs = this;
            if (!cgs.bibliography.isEmpty()) {
                throw new RuntimeException(
                    "createNumberedBibliographySortedByComparator: already have a bibliography");
            }
            CitationGroupsV001.CitedKeys citedKeys = cgs.getCitedKeys();
            citedKeys.sortByComparator(entryComparator); // TODO: must be after database lookup
            citedKeys.numberCitedKeysInCurrentOrder();
            citedKeys.distributeNumbers(cgs);
            this.bibliography = Optional.of(citedKeys);
        }

        public void createPlainBibliographySortedByComparator(Comparator<BibEntry> entryComparator) {
            CitationGroupsV001.CitationGroups cgs = this;
            if (!this.bibliography.isEmpty()) {
                throw new RuntimeException(
                    "createPlainBibliographySortedByComparator: already have a bibliography");
            }
            CitationGroupsV001.CitedKeys citedKeys = cgs.getCitedKeys();
            citedKeys.sortByComparator(entryComparator); // TODO: must be after database lookup
            // citedKeys.numberCitedKeysInCurrentOrder();
            // citedKeys.distributeNumbers();
            this.bibliography = Optional.of(citedKeys);
        }

        public Set<CitationGroupID> getCitationGroupIDs() {
            return citationGroups.keySet();
        }

        /**
         * Creates a list of {@code
         * RangeSortable<CitationGroupsV001.CitationGroupID>} values for
         * our {@code CitationGroup} values. Originally designed to be
         * passed to {@code visualSort}.
         *
         * The elements of the returned list are actually of type {@code
         * RangeSortEntry<CitationGroupID>}.
         *
         * The result is sorted within {@code XTextRange.getText()}
         * partitions of the citation groups according to their {@code
         * XTextRange} (before mapping to footnote marks).
         *
         * In the result, RangeSortable.getIndexInPosition() contains
         * unique indexes within the original partition (not after
         * mapFootnotesToFootnoteMarks).
         *
         * @param cgs The source of CitationGroup values.
         * @param documentConnection Connection to the document.
         * @param mapFootnotesToFootnoteMarks If true, replace ranges in
         *        footnotes with the range of the corresponding footnote
         *        mark. This is used for numbering the citations.
         *
         */
        private static List<RangeSort.RangeSortable<CitationGroupsV001.CitationGroupID>>
        createVisualSortInput(CitationGroupsV001.CitationGroups cgs,
                              DocumentConnection documentConnection,
                              boolean mapFootnotesToFootnoteMarks)
            throws
            NoDocumentException,
            WrappedTargetException {

            List<CitationGroupsV001.CitationGroupID> cgids =
                new ArrayList<>(cgs.getCitationGroupIDs());

            List<RangeSort.RangeSortEntry> vses = new ArrayList<>();
            for (CitationGroupsV001.CitationGroupID cgid : cgids) {
                XTextRange range = cgs.getReferenceMarkRangeOrNull(documentConnection, cgid);
                if (range == null) {
                    throw new RuntimeException("getReferenceMarkRangeOrNull returned null");
                }
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
            RangeKeyedMapList<RangeSort.RangeSortEntry<CitationGroupsV001.CitationGroupID>> xxs
                = new RangeKeyedMapList<>();

            for (RangeSort.RangeSortEntry v : vses) {
                xxs.add(v.getRange(), v);
            }

            // build final list
            List<RangeSort.RangeSortEntry<CitationGroupsV001.CitationGroupID>> res = new ArrayList<>();

            for (TreeMap<XTextRange, List<RangeSort.RangeSortEntry<CitationGroupsV001.CitationGroupID>>>
                     xs : xxs.partitionValues()) {

                List<XTextRange> oxs = new ArrayList<>(xs.keySet());

                int indexInPartition = 0;
                for (int i = 0; i < oxs.size(); i++) {
                    XTextRange a = oxs.get(i);
                    List<RangeSort.RangeSortEntry<CitationGroupsV001.CitationGroupID>> avs = xs.get(a);
                    for (int j = 0; j < avs.size(); j++) {
                        RangeSort.RangeSortEntry<CitationGroupsV001.CitationGroupID> v = avs.get(j);
                        v.indexInPosition = indexInPartition++;
                        if (mapFootnotesToFootnoteMarks) {
                            // Adjust range if we are inside a footnote:
                            if (unoQI(XFootnote.class, v.range.getText()) != null) {
                                // Find the linking footnote marker:
                                XFootnote footer = unoQI(XFootnote.class, v.range.getText());
                                // The footnote's anchor gives the correct position in the text:
                                v.range = footer.getAnchor();
                            }
                        }
                        res.add(v);
                    }
                }
            }
            // convert
            // List<RangeSortEntry<CitationGroupsV001.CitationGroupID>>
            // to
            // List<RangeSortable<CitationGroupsV001.CitationGroupID>>
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
        public List<CitationGroupsV001.CitationGroupID>
        getVisuallySortedCitationGroupIDs(DocumentConnection documentConnection,
                                          boolean mapFootnotesToFootnoteMarks)
            throws
            WrappedTargetException,
            NoDocumentException,
            JabRefException {
            CitationGroupsV001.CitationGroups cgs = this;
            List<RangeSort.RangeSortable<CitationGroupsV001.CitationGroupID>> vses =
                createVisualSortInput(cgs,
                                      documentConnection,
                                      mapFootnotesToFootnoteMarks);

            if (vses.size() != cgs.citationGroups.size()) {
                throw new RuntimeException("getVisuallySortedCitationGroupIDs:"
                                           + " vses.size() != cgs.citationGroups.size()");
            }

            String messageOnFailureToObtainAFunctionalXTextViewCursor =
                Localization.lang("Please move the cursor into the document text.")
                + "\n"
                + Localization.lang("To get the visual positions of your citations"
                                    + " I need to move the cursor around,"
                                    + " but could not get it.");
            List<RangeSort.RangeSortable<CitationGroupsV001.CitationGroupID>> sorted =
                RangeSortVisual.visualSort(vses,
                                           documentConnection,
                                           messageOnFailureToObtainAFunctionalXTextViewCursor);

            if (sorted.size() != cgs.citationGroups.size()) {
                // This Fired
                throw new RuntimeException("getVisuallySortedCitationGroupIDs:"
                                           + " sorted.size() != cgs.citationGroups.size()");
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
            CitationGroupsV001.CitationGroups cgs = this;
            // boolean mapFootnotesToFootnoteMarks = false;
            List<RangeSort.RangeSortable<CitationGroupID>> vses =
                CitationGroupsV001.CitationGroups.createVisualSortInput(cgs,
                                                                        documentConnection,
                                                                        mapFootnotesToFootnoteMarks);

            if (vses.size() != cgs.citationGroups.size()) {
                throw new RuntimeException("getCitationGroupIDsSortedWithinPartitions:"
                                           + " vses.size() != cgs.citationGroups.size()");
            }
            return (vses.stream()
                    .map(e -> e.getContent())
                    .collect(Collectors.toList()));
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
                throw new RuntimeException(
                    "CitationGroupsV001.setGlobalOrder: globalOrder.size() != citationGroups.size()");
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
                throw new RuntimeException("CitationGroupsV001.getCitationGroupOrThrow:"
                                           + " the requested CitationGroup is not available");
            }
            return e;
        }

        private Optional<String> getReferenceMarkName(CitationGroupID cgid) {
            return getCitationGroup(cgid).map(cg -> cg.referenceMarkName);
        }

        private Optional<Integer> getItcType(CitationGroupID cgid) {
            return getCitationGroup(cgid).map(cg -> cg.itcType);
        }

        public int numberOfCitationGroups() {
            return citationGroups.size();
        }

        public Optional<String> getPageInfo(CitationGroupID cgid) {
            return (getCitationGroup(cgid)
                    .map(cg -> cg.pageInfo)
                    .flatMap(x -> x));
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

        /**
         *  Create a reference mark with the given name, at the
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
         *  @param documentConnection Connection to document.
         *  @param position Collapsed to its end.
         *  @param insertSpaceAfter We insert a space after the mark, that
         *                          carries on format of characters from
         *                          the original position.
         *
         *  @param withoutBrackets  Force empty reference mark (no brackets).
         *                          For use with INVISIBLE_CIT.
         *
         */
        public CitationGroupID createCitationGroup(DocumentConnection documentConnection,
                                                   List<String> citationKeys,
                                                   Optional<String> pageInfo,
                                                   int itcType,
                                                   XTextCursor position,
                                                   boolean insertSpaceAfter,
                                                   boolean withoutBrackets)
            throws
            CreationException,
            NoDocumentException,
            WrappedTargetException {

            String xkey =
                citationKeys.stream()
                .collect(Collectors.joining(","));

            String refMarkName =
                Codec.getUniqueReferenceMarkName(documentConnection,
                                                 xkey,
                                                 itcType);

            CitationGroupID cgid = new CitationGroupID(refMarkName);

            List<Citation> citations =
                citationKeys.stream()
                .map(Citation::new)
                .collect(Collectors.toList());

            /*
             * Apply to document
             */
            StorageBase.NamedRange sr = createReferenceMarkForCitationGroup(
                this.citationStorageManager,
                documentConnection,
                refMarkName,
                position,
                insertSpaceAfter,
                withoutBrackets);

            CitationGroup cg = new CitationGroup(cgid,
                                                 sr,
                                                 itcType,
                                                 citations,
                                                 pageInfo,
                                                 refMarkName);

            // add to our data
            this.citationGroups.put(cgid, cg);
            // invalidate globalOrder.
            // TODO: look out for localOrder!
            this.globalOrder = Optional.empty();

            return cgid;
        }

        private static StorageBase.NamedRange
        createReferenceMarkForCitationGroup(StorageBase.NamedRangeManager manager,
                                            DocumentConnection documentConnection,
                                            String refMarkName,
                                            XTextCursor position,
                                            boolean insertSpaceAfter,
                                            boolean withoutBrackets)
            throws
            CreationException {

            return manager.create(documentConnection,
                                  refMarkName,
                                  position,
                                  insertSpaceAfter,
                                  withoutBrackets);
        }
        
        /*
         * Remove it from the {@code this} and the document.
         *
         * TODO: either invalidate or update the extra data we are storing
         *       (bibliography). Update may be complicated, since we do
         *       not know how the bibliography was generated: it was partially done
         *       outside CitationGroupsV001, and we did not store how.
         */
        public void removeCitationGroups(List<CitationGroup> cgs, DocumentConnection documentConnection)
            throws
            WrappedTargetException,
            NoDocumentException,
            NoSuchElementException {

            for (CitationGroup cg : cgs) {
                //documentConnection.removeReferenceMark(cg.referenceMarkName);
                cg.cgRangeStorage.removeFromDocument(documentConnection);
                this.citationGroups.remove(cg.cgid);
                this.globalOrder.map(l -> l.remove(cg.cgid));

                // Invalidate CitedKeys
                this.citedKeysAfterDatabaseLookup = Optional.empty();
                this.bibliography = Optional.empty();
                /*
                 * this.citedKeysAfterDatabaseLookup.map(cks -> cks.forgetCitationGroup(cg.cgid));
                 * this.bibliography.map(cks -> cks.forgetCitationGroup(cg.cgid));
                 */
            }
        }

        public void removeCitationGroup(CitationGroup cg, DocumentConnection documentConnection)
            throws
            WrappedTargetException,
            NoDocumentException,
            NoSuchElementException {

            removeCitationGroups(Collections.singletonList(cg), documentConnection);
        }

        /**
         * Remove brackets, but if the result would become empty, leave
         * them; if the result would be a single characer, leave the left bracket.
         *
         * @param removeBracketsFromEmpty is intended to force removal if
         *        we are working on an "Empty citation" (INVISIBLE_CIT).
         */
        public void cleanFillCursorForCitationGroup(DocumentConnection documentConnection,
                                                    CitationGroupID cgid)
            throws
            NoDocumentException,
            WrappedTargetException,
            CreationException {

            CitationGroup cg = this.getCitationGroup(cgid).orElseThrow(RuntimeException::new);
            cg.cgRangeStorage.cleanFillCursor(documentConnection);
        }

        /**
         * Cursor for the reference marks as is, not prepared for filling,
         * but does not need cleanFillCursorForCitationGroup either.
         */
        public XTextCursor getRawCursorForCitationGroup(CitationGroupID cgid,
                                                        DocumentConnection documentConnection)
            throws
            NoDocumentException,
            WrappedTargetException,
            CreationException {

            CitationGroup cg = this.getCitationGroup(cgid).orElseThrow(RuntimeException::new);
            return cg.cgRangeStorage.getRawCursor(documentConnection);
        }

        public XTextCursor getFillCursorForCitationGroup(DocumentConnection documentConnection,
                                                         CitationGroupID cgid)
            throws
            NoDocumentException,
            WrappedTargetException,
            CreationException {

            CitationGroup cg = this.getCitationGroup(cgid).orElseThrow(RuntimeException::new);
            return cg.cgRangeStorage.getFillCursor(documentConnection);
        }



    /**
     * Extract citation keys from names of referenceMarks in the document.
     *
     * Each citation key is listed only once, in the order of first appearance
     * (in `names`, which itself is in arbitrary order)
     *
     * doc.referenceMarks.names.map(parse).flatten.unique
     *
     * TODO: avoid direct reference mark manipulation
     */
    private List<String> findCitedKeys(DocumentConnection documentConnection)
        throws
        NoSuchElementException,
        WrappedTargetException,
        NoDocumentException {

        List<String> names =
            Codec.getJabRefReferenceMarkNames(this.citationStorageManager,
                                              documentConnection);

        // assert it supports XTextContent
        XNameAccess xNamedMarks = documentConnection.getReferenceMarks();
        for (String name1 : names) {
            Object bookmark = xNamedMarks.getByName(name1);
            assert (null != DocumentConnection.asTextContent(bookmark));
        }

        // Collect to a flat list while keep only the first appearance.
        List<String> keys = new ArrayList<>();
        for (String name1 : names) {
            List<String> newKeys = Codec.parseRefMarkNameToUniqueCitationKeys(name1);
            for (String key : newKeys) {
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
        }

        return keys;
    }

    /**
     *  Given the name of a reference mark, get the corresponding
     *  pageInfo text.
     *
     *  @param documentConnection Connection to a document.
     *  @param name Name of the custom property to query.
     *  @return "" for missing or empty pageInfo
     */
    private static String getPageInfoForReferenceMarkName(DocumentConnection documentConnection,
                                                          String name)
        throws
        WrappedTargetException,
        UnknownPropertyException {

        Optional<String> pageInfo = documentConnection.getCustomProperty(name);
        if (pageInfo.isEmpty() || pageInfo.get().isEmpty()) {
            return "";
        }
        return pageInfo.get();
    }

    /**
     * ranges controlled by citation groups should not overlap with each other.
     *
     * @param cgid : Must be known.
     * @return Null if the reference mark is missing.
     *
     * TODO: getReferenceMarkRangeOrNull vs getRawCursorForCitationGroup
     */
    public XTextRange getReferenceMarkRangeOrNull(DocumentConnection documentConnection,
                                                  CitationGroupID cgid)
        throws
        NoDocumentException,
        WrappedTargetException {
        String name = (this.getReferenceMarkName(cgid)
                       .orElseThrow(RuntimeException::new));
        return documentConnection.getReferenceMarkRangeOrNull(name);
    }

    /**
     * @return A RangeForOverlapCheck for each citation group.
     *
     *  result.size() == nRefMarks
     */
    List<RangeForOverlapCheck> citationRanges(DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException {

        List<RangeForOverlapCheck> xs = new ArrayList<>(numberOfCitationGroups());

        List<CitationGroupsV001.CitationGroupID> cgids =
            new ArrayList<>(this.getCitationGroupIDs());

        for (CitationGroupID cgid : cgids) {
            XTextRange r = this.getReferenceMarkRangeOrNull(documentConnection, cgid);
            String name = this.getCitationGroup(cgid).get().referenceMarkName;
            xs.add(new RangeForOverlapCheck(
                       r, cgid,
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
     */
    List<RangeForOverlapCheck> footnoteMarkRanges(DocumentConnection documentConnection)
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

            XTextRange footnoteMarkRange =
                DocumentConnection.getFootnoteMarkRangeOrNull(r);

            if (footnoteMarkRange == null) {
                // not in footnote
                continue;
            }

            boolean seenContains = seen.containsKey(footnoteMarkRange);
            if (!seenContains) {
                seen.put(footnoteMarkRange, true);
                xs.add(new RangeForOverlapCheck(
                           footnoteMarkRange,
                           base.i, // cgid :: identifies of citation group
                           RangeForOverlapCheck.FOOTNOTE_MARK_KIND,
                           "FootnoteMark for " + base.description));
            }
        }
        return xs;
    }

    public void show() {
        System.out.printf("CitationGroupsV001%n");
        System.out.printf("  citationGroups.size: %d%n", citationGroups.size());
        System.out.printf("  pageInfoThrash.size: %d%n", pageInfoThrash.size());
        System.out.printf("  globalOrder: %s%n",
                          (globalOrder.isEmpty()
                           ? "isEmpty"
                           : String.format("%d", globalOrder.get().size())));
    }
}

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



    /**
     * Identifies a citation with the citation group containing it and
     * its storage index within.
     */
    public static class CitationPath {
        CitationGroupID group;
        int storageIndexInGroup;
        CitationPath(CitationGroupID group,
                     int storageIndexInGroup) {
            this.group = group;
            this.storageIndexInGroup = storageIndexInGroup;
        }
    }


    public static class CitedKey implements CitationSort.ComparableCitation {
        String citationKey;
        LinkedHashSet<CitationPath> where;
        Optional<CitationDatabaseLookup.Result> db;
        Optional<Integer> number; // For Numbered citation styles.
        Optional<String> uniqueLetter; // For AuthorYear citation styles.
        Optional<String> normCitMarker;  // For AuthorYear citation styles.

        CitedKey(String citationKey, CitationPath p, Citation cit) {
            this.citationKey = citationKey;
            this.where = new LinkedHashSet<>(); // remember order
            this.where.add(p);
            this.db = cit.db;
            this.number = cit.number;
            this.uniqueLetter = cit.uniqueLetter;
            this.normCitMarker = Optional.empty();
        }

        @Override
        public String getCitationKey(){
            return citationKey;
        }

        @Override
        public Optional<BibEntry> getBibEntry(){
            return (db.isPresent()
                    ? Optional.of(db.get().entry)
                    : Optional.empty());
        }

        /**
         * Appends to end of {@code where}
         */
        void addPath(CitationPath p, Citation cit) {
            this.where.add(p);
            if (cit.db != this.db) {
                throw new RuntimeException("CitedKey.addPath: mismatch on cit.db");
            }
            if (cit.number != this.number) {
                throw new RuntimeException("CitedKey.addPath: mismatch on cit.number");
            }
            if (cit.uniqueLetter != this.uniqueLetter) {
                throw new RuntimeException("CitedKey.addPath: mismatch on cit.uniqueLetter");
            }
        }

        void lookupInDatabases(List<BibDatabase> databases) {
            this.db = CitationDatabaseLookup.lookup(databases, this.citationKey);
        }

        void distributeDatabaseLookupResult(CitationGroupsV001.CitationGroups cgs) {
            cgs.setDatabaseLookupResults(where, db);
        }

        void distributeNumber(CitationGroupsV001.CitationGroups cgs) {
            cgs.setNumbers(where, number);
        }

        void distributeUniqueLetter(CitationGroupsV001.CitationGroups cgs) {
            cgs.setUniqueLetters(where, uniqueLetter);
        }
    } // class CitedKey

    public static class CitedKeys {

        /**
         * Order-preserving map from citation keys to associated data.
         */
        LinkedHashMap<String, CitedKey> data;

        CitedKeys(LinkedHashMap<String, CitedKey> data) {
            this.data = data;
        }

        /**
         *  The cited keys in sorted order.
         */
        public List<CitedKey> values() {
            return new ArrayList<>(data.values());
        }

        /**
         * Sort entries for the bibliography.
         */
        void sortByComparator(Comparator<BibEntry> entryComparator) {
            List<CitedKey> cks = new ArrayList<>(data.values());
            cks.sort(new CitationSort.CitationComparator(entryComparator, true));
            LinkedHashMap<String, CitedKey> newData = new LinkedHashMap<>();
            for (CitedKey ck : cks) {
                newData.put(ck.citationKey, ck);
            }
            data = newData;
        }

        void numberCitedKeysInCurrentOrder() {
            int i = 1;
            for (CitedKey ck : data.values()) {
                ck.number = Optional.of(i); // was: -1 for UndefinedBibtexEntry
                i++;
            }
        }

        void lookupInDatabases(List<BibDatabase> databases) {
            for (CitedKey ck : this.data.values()) {
                ck.lookupInDatabases(databases);
            }
        }

        void distributeDatabaseLookupResults(CitationGroupsV001.CitationGroups cgs) {
            for (CitedKey ck : this.data.values()) {
                ck.distributeDatabaseLookupResult(cgs);
            }
        }

        void distributeNumbers(CitationGroupsV001.CitationGroups cgs) {
            for (CitedKey ck : this.data.values()) {
                ck.distributeNumber(cgs);
            }
        }

        void distributeUniqueLetters(CitationGroupsV001.CitationGroups cgs) {
            for (CitedKey ck : this.data.values()) {
                ck.distributeUniqueLetter(cgs);
            }
        }

    } // class CitedKeys


} // class CitationGroupsV001

