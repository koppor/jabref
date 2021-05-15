package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jabref.logic.JabRefException;
import org.jabref.logic.oostyle.Citation;
import org.jabref.logic.oostyle.CitationGroup;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.OOFormattedText;

import com.sun.star.beans.IllegalTypeException;
import com.sun.star.beans.NotRemoveableException;
import com.sun.star.beans.PropertyExistException;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.util.InvalidStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditMerge {

    private static final Logger LOGGER = LoggerFactory.getLogger(EditMerge.class);

    public static void mergeCitationGroups(XTextDocument doc,
                                           OOFrontend fr,
                                           List<BibDatabase> databases,
                                           OOBibStyle style)
        throws
        CreationException,
        IllegalArgumentException,
        IllegalTypeException,
        InvalidStateException,
        JabRefException,
        NoDocumentException,
        NoSuchElementException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        UnknownPropertyException,
        WrappedTargetException {

        final boolean useLockControllers = true;
        boolean madeModifications = false;

        List<CitationGroupID> referenceMarkNames =
            fr.getCitationGroupIDsSortedWithinPartitions(doc,
                                                         false /* mapFootnotesToFootnoteMarks */);

        final int nRefMarks = referenceMarkNames.size();

        try {

            if (useLockControllers) {
                UnoScreenRefresh.lockControllers(doc);
            }

            /*
             * joinableGroups collects lists of CitationGroup values
             * that we think are joinable.
             *
             * joinableGroupsCursors provides the range for each group
             */
            List<List<CitationGroup>> joinableGroups = new ArrayList<>();
            List<XTextCursor> joinableGroupsCursors = new ArrayList<>();

            // Since we only join groups with identical citationTypes, we
            // can get citationType from the first element of each
            // joinableGroup.

            if (referenceMarkNames.size() > 0) {
                // current group of CitationGroup values
                List<CitationGroup> currentGroup = new ArrayList<>();
                XTextCursor currentGroupCursor = null;
                XTextCursor cursorBetween = null;
                CitationGroup prev = null;
                XTextRange prevRange = null;

                for (CitationGroupID cgid : referenceMarkNames) {
                    CitationGroup cg = fr.citationGroups.getCitationGroupOrThrow(cgid);

                    XTextRange currentRange = (fr
                                               .getMarkRange(doc, cgid)
                                               .orElseThrow(RuntimeException::new));

                    boolean addToGroup = true;
                    /*
                     * Decide if we add cg to the group
                     */

                    // Only combine (Author 2000) type citations
                    if (cg.citationType != InTextCitationType.AUTHORYEAR_PAR) {
                        addToGroup = false;
                    }

                    // Even if we combine AUTHORYEAR_INTEXT citations, we
                    // would not mix them with AUTHORYEAR_PAR
                    if (addToGroup && (prev != null)) {
                        if (cg.citationType != prev.citationType) {
                            addToGroup = false;
                        }
                    }

                    if (addToGroup && prev != null) {
                        Objects.requireNonNull(prevRange);
                        Objects.requireNonNull(currentRange);
                        if (!UnoTextRange.comparables(prevRange, currentRange)) {
                            addToGroup = false;
                        } else {
                            int textOrder = UnoTextRange.compareStarts(prevRange, currentRange);
                            if (textOrder != (-1)) {
                                String msg =
                                    String.format("MergeCitationGroups:"
                                                  + " \"%s\" supposed to be followed by \"%s\","
                                                  + " but %s",
                                                  prevRange.getString(),
                                                  currentRange.getString(),
                                                  ((textOrder == 0)
                                                   ? "they start at the same position"
                                                   : ("the start of the latter precedes"
                                                      + " the start of the first")));
                                LOGGER.warn(msg);
                                addToGroup = false;
                            }
                        }
                    }

                    if (addToGroup && (cursorBetween != null)) {
                        Objects.requireNonNull(currentGroupCursor);
                        // assume: currentGroupCursor.getEnd() == cursorBetween.getEnd()
                        if (UnoTextRange.compareEnds(cursorBetween, currentGroupCursor) != 0) {
                            String msg = ("MergeCitationGroups:"
                                          + " cursorBetween.end != currentGroupCursor.end");
                            throw new RuntimeException(msg);
                        }

                        XTextRange rangeStart = currentRange.getStart();

                        boolean couldExpand = true;

                        XTextCursor thisCharCursor =
                            (currentRange.getText()
                             .createTextCursorByRange(cursorBetween.getEnd()));

                        while (couldExpand &&
                               (UnoTextRange.compareEnds(cursorBetween, rangeStart) < 0)) {
                            couldExpand = cursorBetween.goRight((short) 1, true);
                            currentGroupCursor.goRight((short) 1, true);
                            //
                            thisCharCursor.goRight((short) 1, true);
                            String thisChar = thisCharCursor.getString();
                            thisCharCursor.collapseToEnd();
                            if (thisChar.isEmpty()
                                || thisChar.equals("\n")
                                || !thisChar.trim().isEmpty()) {
                                couldExpand = false;
                            }
                            if (UnoTextRange.compareEnds(cursorBetween, currentGroupCursor) != 0) {
                                String msg = ("MergeCitationGroups:"
                                              + " cursorBetween.end != currentGroupCursor.end"
                                              + " (during expand)");
                                throw new RuntimeException(msg);
                            }
                        } // while

                        if (!couldExpand) {
                            addToGroup = false;
                        }
                    }

                    /*
                     * Even if we do not add it to an existing group,
                     * we might use it to start a new group.
                     *
                     * Can it start a new group?
                     */
                    boolean canStartGroup = (cg.citationType == InTextCitationType.AUTHORYEAR_PAR);

                    if (!addToGroup) {
                        // close currentGroup
                        if (currentGroup.size() > 1) {
                            joinableGroups.add(currentGroup);
                            joinableGroupsCursors.add(currentGroupCursor);
                        }
                        // Start a new, empty group
                        currentGroup = new ArrayList<>();
                        currentGroupCursor = null;
                        cursorBetween = null;
                        prev = null;
                        prevRange = null;
                    }

                    if (addToGroup || canStartGroup) {
                        // Add the current entry to a group.
                        currentGroup.add(cg);
                        // ... and start new cursorBetween
                        // Set up cursorBetween
                        //
                        XTextRange rangeEnd = currentRange.getEnd();
                        cursorBetween =
                            currentRange.getText().createTextCursorByRange(rangeEnd);
                        // If new group, create currentGroupCursor
                        if (currentGroupCursor == null) {
                            currentGroupCursor = (currentRange.getText()
                                                  .createTextCursorByRange(currentRange.getStart()));
                        }
                        // include self in currentGroupCursor
                        currentGroupCursor.goRight((short) (currentRange.getString().length()), true);

                        if (UnoTextRange.compareEnds(cursorBetween, currentGroupCursor) != 0) {
                            /*
                             * A problem discovered using this check:
                             * when viewing the document in
                             * two-pages-side-by-side mode, our visual
                             * firstAppearanceOrder follows the visual
                             * ordering on the screen. The problem
                             * this caused: it sees a citation on the
                             * 2nd line of the 1st page as appearing
                             * after one at the 1st line of 2nd
                             * page. Since we create cursorBetween at
                             * the end of range1Full (on 1st page), it
                             * is now BEFORE currentGroupCursor (on
                             * 2nd page).
                             */
                            String msg =
                                "MergeCitationGroups: "
                                + "cursorBetween.end != currentGroupCursor.end"
                                + String.format(" (after %s)",
                                                addToGroup ? "addToGroup" : "startGroup")
                                + (addToGroup
                                   ? String.format(" comparisonResult: %d\n"
                                                   + "cursorBetween: '%s'\n"
                                                   + "currentRange: '%s'\n"
                                                   + "currentGroupCursor: '%s'\n",
                                                   UnoTextRange.compareEnds(cursorBetween,
                                                                            currentGroupCursor),
                                                   cursorBetween.getString(),
                                                   currentRange.getString(),
                                                   currentGroupCursor.getString())
                                   : "");
                            throw new RuntimeException(msg);
                        }
                        prev = cg;
                        prevRange = currentRange;
                    }
                } // for cgid

                // close currentGroup
                if (currentGroup.size() > 1) {
                    joinableGroups.add(currentGroup);
                    joinableGroupsCursors.add(currentGroupCursor);
                }
            } // if (referenceMarkNames.size() > 0)

            /*
             * Now we can process the joinable groups
             */
            for (int gi = 0; gi < joinableGroups.size(); gi++) {

                List<CitationGroup> joinableGroup = joinableGroups.get(gi);
                /*
                 * Join those in joinableGroups.get(gi)
                 */

                //
                // Note: we are taking ownership of the citations (by
                //       adding to newGroupCitations, then removing
                //       the original CitationGroup values)

                List<Citation> newGroupCitations = new ArrayList<>();
                for (CitationGroup rk : joinableGroup) {
                    newGroupCitations.addAll(rk.citationsInStorageOrder);
                }

                InTextCitationType citationType = joinableGroup.get(0).citationType;

                // cgPageInfos belong to the CitationGroup (DataModel JabRef52),
                // but it is not clear how should we handle them here.
                // We delegate the problem to the backend.
                List<OOFormattedText> pageInfosForCitations =
                    fr.backend.combinePageInfos(joinableGroup);

                // Remove the old citation groups from the document.
                for (int gj = 0; gj < joinableGroup.size(); gj++) {
                    fr.removeCitationGroups(joinableGroup, doc);
                }

                XTextCursor textCursor = joinableGroupsCursors.get(gi);
                // Also remove the spaces between.
                textCursor.setString("");

                List<String> citationKeys = (newGroupCitations.stream()
                                             .map(cit -> cit.citationKey)
                                             .collect(Collectors.toList()));

                // Insert reference mark:

                /* insertSpaceAfter: no, it is already there (or could
                 * be)
                 */
                boolean insertSpaceAfter = false;
                UpdateCitationMarkers.createAndFillCitationGroup(fr,
                                                                 doc,
                                                                 citationKeys,
                                                                 pageInfosForCitations,
                                                                 citationType,
                                                                 OOFormattedText.fromString("tmp"),
                                                                 textCursor,
                                                                 style,
                                                                 insertSpaceAfter);
            } // for gi

            madeModifications = (joinableGroups.size() > 0);

        } finally {
            if (useLockControllers) {
                UnoScreenRefresh.unlockControllers(doc);
            }
        }

        if (madeModifications) {
            UnoCrossRef.refresh(doc);
            OOFrontend fr2 = new OOFrontend(doc);
            Update.updateDocument(doc,
                                  fr2,
                                  databases,
                                  style,
                                  false, /* doUpdateBibliography */
                                  false);
        }
    }
}
