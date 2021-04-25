package org.jabref.logic.openoffice;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *  How and what is encoded in a mark names.
 *
 *  - pageInfo does not appear here. It is not encoded in the mark name.
 *  - Does not depend on the type of marks (reference mark of bookmark) used.
 */
class Codec52 {
    private static final String BIB_CITATION = "JR_cite";
    private static final Pattern CITE_PATTERN =
        // Pattern.compile(BIB_CITATION + "(\\d*)_(\\d*)_(.*)");
        // itcType is always "0" "1" or "2"
        Pattern.compile(BIB_CITATION + "(\\d*)_([012])_(.*)");

    /**
     * This is what we get back from parsing a refMarkName.
     *
     */
    public static class ParsedMarkName {
        /**  "", "0", "1" ... */
        public final String i;
        /** in-text-citation type */
        public final int itcType;
        /** Citation keys embedded in the reference mark. */
        public final List<String> citationKeys;

        ParsedMarkName(String i, int itcType, List<String> citationKeys) {
            Objects.requireNonNull(i);
            Objects.requireNonNull(citationKeys);
            this.i = i;
            this.itcType = itcType;
            this.citationKeys = citationKeys;
        }
    }

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
    public static String getUniqueMarkName(Set<String> usedNames,
                                           String bibtexKey,
                                           int itcType)
        throws NoDocumentException {

        // XNameAccess xNamedRefMarks = documentConnection.getReferenceMarks();
        int i = 0;
        String name = BIB_CITATION + '_' + itcType + '_' + bibtexKey;
        while (usedNames.contains(name)) {
            name = BIB_CITATION + i + '_' + itcType + '_' + bibtexKey;
            i++;
        }
        return name;
    }

    /**
     * Parse a JabRef (reference) mark name.
     *
     * @return Optional.empty() on failure.
     *
     */
    public static Optional<ParsedMarkName> parseMarkName(String refMarkName) {

        Matcher citeMatcher = CITE_PATTERN.matcher(refMarkName);
        if (!citeMatcher.find()) {
            return Optional.empty();
        }

        List<String> keys = Arrays.asList(citeMatcher.group(3).split(","));
        String i = citeMatcher.group(1);
        int itcType = Integer.parseInt(citeMatcher.group(2));
        return (Optional.of(new Codec52.ParsedMarkName(i, itcType, keys)));
    }

    /**
     * @return true if name matches the pattern used for JabRef
     * reference mark names.
     */
    public static boolean isJabRefReferenceMarkName(String name) {
        return (CITE_PATTERN.matcher(name).find());
    }

    /**
     * Filter a list of reference mark names by `isJabRefReferenceMarkName`
     *
     * @param names The list to be filtered.
     */
    public static List<String> filterIsJabRefReferenceMarkName(List<String> names) {
        return (names
                .stream()
                .filter(Codec52::isJabRefReferenceMarkName)
                .collect(Collectors.toList()));
    }
}
