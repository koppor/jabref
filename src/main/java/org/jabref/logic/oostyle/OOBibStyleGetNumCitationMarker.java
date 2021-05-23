package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jabref.model.oostyle.CitationMarkerNumericBibEntry;
import org.jabref.model.oostyle.CitationMarkerNumericEntry;
import org.jabref.model.oostyle.CitationMarkerNumericEntryImpl;
import org.jabref.model.oostyle.CompareCitation;
import org.jabref.model.oostyle.OOListUtil;
import org.jabref.model.oostyle.OOText;
import org.jabref.model.openoffice.Tuple3;

class OOBibStyleGetNumCitationMarker {

    /*
     * The number encoding "this entry is unresolved"
     */
    public final static int UNRESOLVED_ENTRY_NUMBER = 0;

    /**
     * Defines sort order for CitationMarkerNumericEntry.
     */
    private static int compareCitationMarkerNumericEntry(CitationMarkerNumericEntry a,
                                                         CitationMarkerNumericEntry b) {
        int na = a.getNumber().orElse(UNRESOLVED_ENTRY_NUMBER);
        int nb = b.getNumber().orElse(UNRESOLVED_ENTRY_NUMBER);
        int res = Integer.compare(na, nb);
        if (res == 0) {
            res = CompareCitation.comparePageInfo(a.getPageInfo(), b.getPageInfo());
        }
        return res;
    }

    /**
     *  Create a numeric marker for use in the bibliography as label for the entry.
     *
     *  To support for example numbers in superscript without brackets for the text,
     *  but "[1]" form for the bibliography, the style can provide
     *  the optional "BracketBeforeInList" and "BracketAfterInList" strings
     *  to be used in the bibliography instead of "BracketBefore" and "BracketAfter"
     *
     *  @return "[${number}]" where
     *       "[" stands for BRACKET_BEFORE_IN_LIST (with fallback BRACKET_BEFORE)
     *       "]" stands for BRACKET_AFTER_IN_LIST (with fallback BRACKET_AFTER)
     *       "${number}" stands for the formatted number.
     */
    public static OOText getNumCitationMarkerForBibliography(OOBibStyle style,
                                                             CitationMarkerNumericBibEntry entry) {
        // prefer BRACKET_BEFORE_IN_LIST and BRACKET_AFTER_IN_LIST
        String bracketBefore = style.getBracketBeforeInListWithFallBack();
        String bracketAfter = style.getBracketAfterInListWithFallBack();
        StringBuilder sb = new StringBuilder();
        sb.append(style.getCitationGroupMarkupBefore());
        sb.append(bracketBefore);
        final Optional<Integer> current = entry.getNumber();
        sb.append(current.isPresent()
                  ? String.valueOf(current.get())
                  : (OOBibStyle.UNDEFINED_CITATION_MARKER + entry.getCitationKey()));
        sb.append(bracketAfter);
        sb.append(style.getCitationGroupMarkupAfter());
        return OOText.fromString(sb.toString());
    }

    /*
     * emitBlock
     *
     * Given a block containing 1 or (two or more)
     * CitationMarkerNumericEntryImpl entries that are either singletons or
     * joinable into an "i-j" form, append to {@code sb} the
     * formatted text.
     *
     * Assumes:
     *
     * - block is not empty
     *
     * - For a block with a single element the element may have
     *    pageInfo and its num part may be UNRESOLVED_ENTRY_NUMBER.
     *
     * - For a block with two or more elements
     *
     *   - The elements do not have pageInfo and their num part is
     *     not zero.
     *
     *   - The elements num parts are consecutive positive integers,
     *     without repetition.
     *
     * Note: this function is long enough to move into a separate method.
     *       On the other hand, its assumptions strongly tie it to
     *       the loop below that collects the block.
     */
    private static void emitBlock(List<CitationMarkerNumericEntry> block,
                                  OOBibStyle style,
                                  int minGroupingCount,
                                  StringBuilder sb) {

        final int blockSize = block.size();
        if (blockSize == 0) {
            throw new RuntimeException("The block is empty");
        }

        if (blockSize == 1) {
            // Add single entry:
            CitationMarkerNumericEntry entry = block.get(0);
            final Optional<Integer> num = entry.getNumber();
            sb.append(num.isEmpty()
                      ? (OOBibStyle.UNDEFINED_CITATION_MARKER + entry.getCitationKey())
                      : String.valueOf(num));
            // Emit pageInfo
            Optional<OOText> pageInfo = entry.getPageInfo();
            if (pageInfo.isPresent()) {
                sb.append(style.getPageInfoSeparator());
                sb.append(OOText.toString(pageInfo.get()));
            }
            return;
        }

        if (blockSize >= 2) {

            /*
             * Check assumptions
             */

            if (block.stream().anyMatch(x -> x.getPageInfo().isPresent())) {
                throw new RuntimeException("Found pageInfo in a block with more than one elements");
            }

            if (block.stream().anyMatch(x -> x.getNumber().isEmpty())) {
                throw new RuntimeException("Found unresolved entry"
                                           + " in a block with more than one elements");
            }

            for (int j = 1; j < blockSize; j++) {
                if ((block.get(j).getNumber().get() - block.get(j - 1).getNumber().get()) != 1) {
                    throw new RuntimeException("Numbers are not consecutive");
                }
            }

            /*
             * Do the actual work
             */

            if (blockSize >= minGroupingCount) {
                int first = block.get(0).getNumber().get();
                int last = block.get(blockSize - 1).getNumber().get();
                if (last != (first + blockSize - 1)) {
                    throw new RuntimeException("blockSize and length of num range differ");
                }

                // Emit: "first-last"
                sb.append(first);
                sb.append(style.getGroupedNumbersSeparator());
                sb.append(last);
            } else {

                // Emit: first, first+1,..., last
                for (int j = 0; j < blockSize; j++) {
                    if (j > 0) {
                        sb.append(style.getCitationSeparator());
                    }
                    sb.append(block.get(j).getNumber().get());
                }
            }
            return;
        }
    }

    /**
     * Format a number-based citation marker for the given number or numbers.
     *
     * @param numbers The citation numbers.
     *
     *               A zero (UNRESOLVED_ENTRY_NUMBER) in the list means: could not look this up
     *               in the databases. Positive integers are the valid numbers.
     *
     *               Duplicate citation numbers are allowed:
     *
     *                 - If their pageInfos are identical, only a
     *                   single instance is emitted.
     *
     *                 - If their pageInfos differ, the number is emitted with each
     *                    distinct pageInfo.
     *
     *                    For pageInfo Optional.empty and "" (after
     *                    pageInfo.get().trim()) are considered equal (and missing).
     *
     * @param minGroupingCount Zero and negative means never group
     *
     * @param pageInfos  Null for "none", or a list with an optional
     *        pageInfo for each citation. Any or all of these can be Optional.empty
     *
     * @return The text for the citation.
     *
     */
    public static OOText getNumCitationMarker2(OOBibStyle style,
                                               List<String> citationKeys,
                                               List<Integer> numbers,
                                               int minGroupingCount,
                                               List<Optional<OOText>> pageInfos) {
        final int nCitations = numbers.size();

        List<Optional<OOText>> pageInfosNormalized =
            OOBibStyle.normalizePageInfos(pageInfos, numbers.size());

        List<Tuple3<String, Integer, Optional<OOText>>> xs =
            OOListUtil.zip3(citationKeys, numbers, pageInfosNormalized);

        List<CitationMarkerNumericEntry> entries =
            OOListUtil.map(xs, CitationMarkerNumericEntryImpl::from);

        return getNumCitationMarker2(style, entries, minGroupingCount);
    }

    public static OOText getNumCitationMarker2(OOBibStyle style,
                                               List<CitationMarkerNumericEntry> entries,
                                               int minGroupingCount) {

        final boolean joinIsDisabled = (minGroupingCount <= 0);
        final int nCitations = entries.size();

        String bracketBefore = style.getBracketBefore();
        String bracketAfter = style.getBracketAfter();

        // Sort a copy of entries
        List<CitationMarkerNumericEntry> sorted = OOListUtil.map(entries, e -> e);
        sorted.sort(OOBibStyleGetNumCitationMarker::compareCitationMarkerNumericEntry);

        // "["
        StringBuilder sb = new StringBuilder(bracketBefore);

        /*
         *  Original:
         *  [2,3,4]   -> [2-4]
         *  [0,1,2]   -> [??,1,2]
         *  [0,1,2,3] -> [??,1-3]
         *
         *  Now we have to consider: duplicate numbers and pageInfos
         *  [1,1] -> [1]
         *  [1,1 "pp nn"] -> keep separate if pageInfo differs
         *  [1 "pp nn",1 "pp nn"] -> [1 "pp nn"]
         */

        boolean blocksEmitted = false;
        List<CitationMarkerNumericEntry> currentBlock = new ArrayList<>();
        List<CitationMarkerNumericEntry> nextBlock = new ArrayList<>();

        for (int i = 0; i < nCitations; i++) {

            final CitationMarkerNumericEntry current = sorted.get(i);
            if (current.getNumber().isPresent() && current.getNumber().get() < 0) {
                throw new RuntimeException("getNumCitationMarker2: found negative value");
            }

            if (currentBlock.size() == 0) {
                currentBlock.add(current);
            } else {
                CitationMarkerNumericEntry prev = currentBlock.get(currentBlock.size() - 1);
                if (current.getNumber().isEmpty() || prev.getNumber().isEmpty()) {
                    nextBlock.add(current); // do not join if not found
                } else if (joinIsDisabled) {
                    nextBlock.add(current); // join disabled
                } else if (compareCitationMarkerNumericEntry(current, prev) == 0) {
                    // Same as prev, just forget it.
                } else if ((current.getNumber().get() == (prev.getNumber().get() + 1))
                           && (prev.getPageInfo().isEmpty())
                           && (current.getPageInfo().isEmpty())) {
                    // Just two consecutive numbers without pageInfo: join
                    currentBlock.add(current);
                } else {
                    // do not join
                    nextBlock.add(current);
                }
            }

            if (nextBlock.size() > 0) {
                // emit current block
                if (blocksEmitted) {
                    sb.append(style.getCitationSeparator());
                }
                emitBlock(currentBlock, style, minGroupingCount, sb);
                blocksEmitted = true;
                currentBlock = nextBlock;
                nextBlock = new ArrayList<>();
            }

        }

        if (nextBlock.size() != 0) {
            throw new RuntimeException("impossible: (nextBlock.size() != 0) after loop");
        }

        if (currentBlock.size() > 0) {
            // We are emitting a block
            if (blocksEmitted) {
                sb.append(style.getCitationSeparator());
            }
            emitBlock(currentBlock, style, minGroupingCount, sb);
        }

        // Emit: "]"
        sb.append(bracketAfter);
        return OOText.fromString(sb.toString());
    }

}
