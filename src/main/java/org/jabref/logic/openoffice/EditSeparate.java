package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jabref.logic.JabRefException;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.oostyle.Citation;
import org.jabref.model.oostyle.CitationGroup;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.OOFormattedText;
import org.jabref.model.openoffice.CreationException;
import org.jabref.model.openoffice.NoDocumentException;

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

public class EditSeparate {

    public static void separateCitations(XTextDocument doc,
                                         OOFrontend fr,
                                         List<BibDatabase> databases,
                                         OOBibStyle style,
                                         FunctionalTextViewCursor fcursor)
        throws
        CreationException,
        IllegalTypeException,
        InvalidStateException,
        JabRefException,
        NoDocumentException,
        NoSuchElementException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        UnknownPropertyException,
        WrappedTargetException,
        com.sun.star.lang.IllegalArgumentException {

        boolean madeModifications = false;

        // {@code names} does not need to be sorted.
        List<CitationGroupID> names =
            new ArrayList<>(fr.citationGroups.getCitationGroupIDsUnordered());

        final boolean useLockControllers = true;
        try {
            if (useLockControllers) {
                UnoScreenRefresh.lockControllers(doc);
            }

            int pivot = 0;

            while (pivot < (names.size())) {
                CitationGroupID cgid = names.get(pivot);
                CitationGroup cg = fr.citationGroups.getCitationGroupOrThrow(cgid);
                XTextRange range1 = (fr
                                     .getMarkRange(doc, cgid)
                                     .orElseThrow(RuntimeException::new));
                XTextCursor textCursor = range1.getText().createTextCursorByRange(range1);

                // Note: JabRef52 returns cg.pageInfo for the last citation.
                List<Optional<OOFormattedText>> pageInfosForCitations =
                    cg.getPageInfosForCitationsInStorageOrder();

                List<Citation> cits = cg.citationsInStorageOrder;
                if (cits.size() <= 1) {
                    pivot++;
                    continue;
                }

                List<String> keys =
                    cits.stream().map(cit -> cit.citationKey).collect(Collectors.toList());

                fr.removeCitationGroup(cg, doc);

                // Now we own the content of cits

                // Insert mark for each key
                final int last = keys.size() - 1;
                for (int i = 0; i < keys.size(); i++) {
                    boolean insertSpaceAfter = (i != last);
                    List<String> citationKeys1 = keys.subList(i, i + 1);
                    List<Optional<OOFormattedText>> pageInfos1 = pageInfosForCitations.subList(i, i + 1);
                    // String tmpLabel = "tmp";
                    String tmpLabel = keys.get(i);
                    OOFormattedText citationText1 = OOFormattedText.fromString(tmpLabel);
                    UpdateCitationMarkers.createAndFillCitationGroup(fr,
                                                                     doc,
                                                                     citationKeys1,
                                                                     pageInfos1,
                                                                     cg.citationType,
                                                                     citationText1,
                                                                     textCursor,
                                                                     style,
                                                                     insertSpaceAfter);
                    textCursor.collapseToEnd();
                }

                madeModifications = true;
                pivot++;
            }
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
                                  fcursor,
                                  false, /* doUpdateBibliography */
                                  false /* alwaysAddCitedOnPages */);
        }
    }
}
