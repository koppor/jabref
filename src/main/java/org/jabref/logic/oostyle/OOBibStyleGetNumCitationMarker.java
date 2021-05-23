package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jabref.model.oostyle.CompareCitation;
import org.jabref.model.oostyle.OOText;

class OOBibStyleGetNumCitationMarker {

    /*
     * The number encoding "this entry is unresolved"
     */
    public final static int UNRESOLVED_ENTRY_NUMBER = 0;

    /*
     * Helper class for sorting citation numbers while
     * maintaining their correspondence to pageInfos.
     */
    private static class NumberWithPageInfo {
        int num;
        Optional<OOText> pageInfo;
        NumberWithPageInfo(int num, Optional<OOText> pageInfo) {
            this.num = num;
            this.pageInfo = pageInfo;
        }
    }

    /**
     * Defines sort order for NumberWithPageInfo entries.
     *
     * null comes before non-null
     */
    private static int compareNumberWithPageInfo(NumberWithPageInfo a, NumberWithPageInfo b) {
        int res = Integer.compare(a.num, b.num);
        if (res == 0) {
            res = CompareCitation.comparePageInfo(a.pageInfo, b.pageInfo);
        }
        return res;
    }

    private enum CitationMarkerPurpose {
        /** Creating citation marker for in-text citation. */
        CITATION,
        /** Creating citation marker for the bibliography. */
        BIBLIOGRAPHY
    }

    /**
     * See {@see getNumCitationMarkerCommon} for details.
     */
    public static OOText getNumCitationMarker(OOBibStyle style,
                                              List<Integer> numbers,
                                              int minGroupingCount,
                                              List<Optional<OOText>> pageInfos) {
        return getNumCitationMarkerCommon(style,
                                          numbers,
                                          minGroupingCount,
                                          CitationMarkerPurpose.CITATION,
                                          pageInfos);
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
    public static OOText getNumCitationMarkerForBibliography(OOBibStyle style, int number) {
        return getNumCitationMarkerCommon(style,
                                          Collections.singletonList(number),
                                          0,
                                          CitationMarkerPurpose.BIBLIOGRAPHY,
                                          null);
    }

    /*
     * emitBlock
     *
     * Given a block containing 1 or (two or more)
     * NumberWithPageInfo entries that are either singletons or
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
    private static void emitBlock(List<NumberWithPageInfo> block,
                                  OOBibStyle style,
                                  int minGroupingCount,
                                  StringBuilder sb) {

        final int blockSize = block.size();
        if (blockSize == 0) {
            throw new RuntimeException("The block is empty");
        }

        if (blockSize == 1) {
            // Add single entry:
            final int num = block.get(0).num;
            sb.append(num == UNRESOLVED_ENTRY_NUMBER
                      ? OOBibStyle.UNDEFINED_CITATION_MARKER
                      : String.valueOf(num));
            // Emit pageInfo
            Optional<OOText> pageInfo = block.get(0).pageInfo;
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

            if (block.stream().anyMatch(x -> x.pageInfo.isPresent())) {
                throw new RuntimeException("Found pageInfo in a block with more than one elements");
            }

            if (block.stream().anyMatch(x -> x.num == UNRESOLVED_ENTRY_NUMBER)) {
                throw new RuntimeException("Found UNRESOLVED_ENTRY_NUMBER"
                                           + " in a block with more than one elements");
            }

            for (int j = 1; j < blockSize; j++) {
                if ((block.get(j).num - block.get(j - 1).num) != 1) {
                    throw new RuntimeException("Numbers are not consecutive");
                }
            }

            /*
             * Do the actual work
             */

            if (blockSize >= minGroupingCount) {
                int first = block.get(0).num;
                int last = block.get(blockSize - 1).num;
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
                    sb.append(block.get(j).num);
                }
            }
            return;
        }
    }

    /**
     * Format a number-based citation marker for the given number or numbers.
     *
     * This is the common implementation behind
     * getNumCitationMarker and
     * getNumCitationMarkerForBibliography. The latter could be easily
     * separated unless there is (or going to be) a need for handling
     * multiple numbers or page info by getNumCitationMarkerForBibliography.
     *
     * @param numbers The citation numbers.
     *
     *               A zero in the list means: could not look this up
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
     *                    pageInfo.get(),trim()) are considered equal (and missing).
     *
     * @param minGroupingCount Zero and negative means never group
     *
     * @param purpose BIBLIOGRAPHY (was: inList==True) when creating for a bibliography entry,
     *                CITATION (was: inList=false) when creating in-text citation.
     *
     *               If BIBLIOGRAPHY: Prefer BRACKET_BEFORE_IN_LIST over BRACKET_BEFORE,
     *                                   and BRACKET_AFTER_IN_LIST over BRACKET_AFTER.
     *                                Ignore pageInfos.
     *
     * @param pageInfosIn  Null for "none", or a list with an optional
     *        pageInfo for each citation. Any or all of these can be Optional.empty
     *
     * @return The text for the citation.
     *
     */
    private static OOText
    getNumCitationMarkerCommon(OOBibStyle style,
                               List<Integer> numbers,
                               int minGroupingCount,
                               CitationMarkerPurpose purpose,
                               List<Optional<OOText>> pageInfosIn) {

        final boolean joinIsDisabled = (minGroupingCount <= 0);
        final int nCitations = numbers.size();

        /*
         * strictPurpose: if true, require (nCitations == 1) when (purpose == BIBLIOGRAPHY),
         *                otherwise allow multiple citation numbers and process the BIBLIOGRAPHY case
         *                as CITATION with no pageInfo.
         */
        final boolean strictPurpose = true;

        String bracketBefore = style.getBracketBefore();
        String bracketAfter = style.getBracketAfter();

        /*
         * purpose == BIBLIOGRAPHY means: we are formatting for the
         *                       bibliography, (not for in-text citation).
         */
        if (purpose == CitationMarkerPurpose.BIBLIOGRAPHY) {
            // prefer BRACKET_BEFORE_IN_LIST and BRACKET_AFTER_IN_LIST
            bracketBefore = style.getBracketBeforeInListWithFallBack();
            bracketAfter = style.getBracketAfterInListWithFallBack();

            if (strictPurpose) {
                // If (purpose==BIBLIOGRAPHY), then
                // we expect exactly one number here, and can handle quickly
                if (nCitations != 1) {
                    throw new RuntimeException("getNumCitationMarker:"
                                               + "nCitations != 1 for purpose==BIBLIOGRAPHY."
                                               + String.format(" nCitations = %d", nCitations));
                }
                //
                StringBuilder sb = new StringBuilder();
                sb.append(style.getCitationGroupMarkupBefore());
                sb.append(bracketBefore);
                final int current = numbers.get(0);
                if (current < 0) {
                    throw new RuntimeException("getNumCitationMarker: found negative value");
                }
                sb.append(current != UNRESOLVED_ENTRY_NUMBER
                          ? String.valueOf(current)
                          : OOBibStyle.UNDEFINED_CITATION_MARKER);
                sb.append(bracketAfter);
                sb.append(style.getCitationGroupMarkupAfter());
                return OOText.fromString(sb.toString());
            }
        }

        /*
         * From here:
         *  - formatting for in-text (not for bibliography)
         *  - need to care about pageInfos
         *
         *  - In case {@code strictPurpose} above is set to false and allows us to
         *    get here, and {@code purpose==BIBLIOGRAPHY}, then we just fill
         *    pageInfos with null values.
         */
        List<Optional<OOText>> pageInfos =
            OOBibStyle.normalizePageInfos((purpose == CitationMarkerPurpose.BIBLIOGRAPHY
                                           ? null
                                           : pageInfosIn),
                                          numbers.size());

        // Sort the numbers, together with the corresponding pageInfo values
        List<NumberWithPageInfo> nps = new ArrayList<>();
        for (int i = 0; i < nCitations; i++) {
            nps.add(new NumberWithPageInfo(numbers.get(i), pageInfos.get(i)));
        }
        nps.sort(OOBibStyleGetNumCitationMarker::compareNumberWithPageInfo);

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
        List<NumberWithPageInfo> currentBlock = new ArrayList<>();
        List<NumberWithPageInfo> nextBlock = new ArrayList<>();

        for (int i = 0; i < nCitations; i++) {

            final NumberWithPageInfo current = nps.get(i);
            if (current.num < 0) {
                throw new RuntimeException("getNumCitationMarker: found negative value");
            }

            if (currentBlock.size() == 0) {
                currentBlock.add(current);
            } else {
                NumberWithPageInfo prev = currentBlock.get(currentBlock.size() - 1);
                if ((UNRESOLVED_ENTRY_NUMBER == current.num)
                     || (UNRESOLVED_ENTRY_NUMBER == prev.num)) {
                    nextBlock.add(current); // do not join if not found
                } else if (joinIsDisabled) {
                    nextBlock.add(current); // join disabled
                } else if (compareNumberWithPageInfo(current, prev) == 0) {
                    // Same as prev, just forget it.
                } else if ((current.num == (prev.num + 1))
                           && (prev.pageInfo.isEmpty())
                           && (current.pageInfo.isEmpty())) {
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
