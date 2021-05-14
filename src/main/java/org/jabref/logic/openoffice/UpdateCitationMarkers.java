package org.jabref.logic.openoffice;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jabref.logic.JabRefException;
import org.jabref.logic.oostyle.CitationGroup;
import org.jabref.logic.oostyle.CitationGroups;
import org.jabref.logic.oostyle.OOBibStyle;
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

/*
 * Update document: citation marks and bibliography
 */
public class UpdateCitationMarkers {
    /**
     * Visit each reference mark in referenceMarkNames, overwrite its
     * text content.
     *
     * After each fillCitationMarkInCursor call check if we lost the
     * BIB_SECTION_NAME bookmark and recreate it if we did.
     *
     * @param fr
     *
     * @param citMarkers Corresponding text for each reference mark,
     *                   that replaces the old text.
     *
     * @param style Bibliography style to use.
     *
     */
    static void applyNewCitationMarkers(XTextDocument doc,
                                        OOFrontend fr,
                                        Map<CitationGroupID, OOFormattedText> citMarkers,
                                        OOBibStyle style)
        throws
        NoDocumentException,
        UnknownPropertyException,
        CreationException,
        WrappedTargetException,
        PropertyVetoException,
        NoSuchElementException,
        JabRefException {

        // checkStylesExistInTheDocument(style, doc);

        CitationGroups cgs = fr.citationGroups;

        for (Map.Entry<CitationGroupID, OOFormattedText> kv : citMarkers.entrySet()) {

            CitationGroupID cgid = kv.getKey();
            Objects.requireNonNull(cgid);

            OOFormattedText citationText = kv.getValue();
            Objects.requireNonNull(citationText);

            CitationGroup cg = cgs.getCitationGroupOrThrow(cgid);

            boolean withText = (cg.citationType != InTextCitationType.INVISIBLE_CIT);

            if (withText) {

                XTextCursor cursor = fr.getFillCursorForCitationGroup(doc, cgid);

                fillCitationMarkInCursor(doc, cursor, citationText, withText, style);

                fr.cleanFillCursorForCitationGroup(doc, cgid);
            }

        }
    }

    public static void fillCitationMarkInCursor(XTextDocument doc,
                                                XTextCursor cursor,
                                                OOFormattedText citationText,
                                                boolean withText,
                                                OOBibStyle style)
    throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        NoSuchElementException,
        CreationException,
        IllegalArgumentException {

        Objects.requireNonNull(cursor);
        Objects.requireNonNull(citationText);
        Objects.requireNonNull(style);

        if (withText) {
            OOFormattedText citationText2 = style.decorateCitationMarker(citationText);
            // inject a ZERO_WIDTH_SPACE to hold the initial character format
            final String ZERO_WIDTH_SPACE = "\u200b";
            citationText2 = OOFormattedText.fromString(ZERO_WIDTH_SPACE + citationText2.asString());
            OOFormattedTextIntoOO.write(doc, cursor, citationText2);
        } else {
            cursor.setString("");
        }
    }

    /**
     *  Inserts a citation group in the document: creates and fills it.
     *
     * @param citationKeys BibTeX keys of
     * @param pageInfosForCitations
     * @param citationType
     *
     * @param citationText Text for the citation. A citation mark or
     *             placeholder if not yet available.
     *
     * @param position Location to insert at.
     * @param withText If false, citationText is not shown.
     * @param style
     * @param insertSpaceAfter A space inserted after the reference
     *             mark makes it easier to separate from the text
     *             coming after. But is not wanted when we recreate a
     *             reference mark.
     */
    public static void createAndFillCitationGroup(OOFrontend fr,
                                                  XTextDocument doc,
                                                  List<String> citationKeys,
                                                  List<OOFormattedText> pageInfosForCitations,
                                                  InTextCitationType citationType,
                                                  OOFormattedText citationText,
                                                  XTextCursor position,
                                                  boolean withText,
                                                  OOBibStyle style,
                                                  boolean insertSpaceAfter)
        throws
        UnknownPropertyException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        WrappedTargetException,
        PropertyVetoException,
        IllegalArgumentException,
        CreationException,
        NoDocumentException,
        IllegalTypeException,
        NoSuchElementException {

        Objects.requireNonNull(pageInfosForCitations);
        if (pageInfosForCitations.size() != citationKeys.size()) {
            throw new RuntimeException("pageInfosForCitations.size != citationKeys.size");
        }
        CitationGroupID cgid = fr.createCitationGroup(doc,
                                                      citationKeys,
                                                      pageInfosForCitations,
                                                      citationType,
                                                      position,
                                                      insertSpaceAfter,
                                                      !withText /* withoutBrackets */);

        if (withText) {
            XTextCursor c2 = fr.getFillCursorForCitationGroup(doc, cgid);

            UpdateCitationMarkers.fillCitationMarkInCursor(doc, c2, citationText, withText, style);

            fr.cleanFillCursorForCitationGroup(doc, cgid);
        }
        position.collapseToEnd();
    }

}
