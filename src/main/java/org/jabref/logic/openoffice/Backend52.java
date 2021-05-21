package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jabref.model.oostyle.Citation;
import org.jabref.model.oostyle.CitationGroup;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.CitationGroups;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.OOFormattedText;
import org.jabref.model.oostyle.OOStyleDataModelVersion;
import org.jabref.model.openoffice.CitationEntry;
import org.jabref.model.openoffice.CreationException;
import org.jabref.model.openoffice.NoDocumentException;
import org.jabref.model.openoffice.StorageBase;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Backend52 {
    private static final Logger LOGGER = LoggerFactory.getLogger(Backend52.class);
    public final OOStyleDataModelVersion dataModel;
    public final StorageBase.NamedRangeManager citationStorageManager;

    // uses: Codec52
    public Backend52() {
        this.dataModel = OOStyleDataModelVersion.JabRef52;
        this.citationStorageManager = new StorageBaseRefMark.Manager();
    }

    /**
     * Get reference mark names from the document matching the pattern
     * used for JabRef reference mark names.
     *
     * Note: the names returned are in arbitrary order.
     *
     */
    public List<String> getJabRefReferenceMarkNames(XTextDocument doc)
        throws
        NoDocumentException {
        List<String> allNames = this.citationStorageManager.getUsedNames(doc);
        return Codec52.filterIsJabRefReferenceMarkName(allNames);
    }

    /**
     * Names of custom properties belonging to us, but without a
     * corresponding reference mark.  These can be deleted.
     *
     * @param citationGroupNames These are the names that are used.
     *
     */
    private List<String> findUnusedJabrefPropertyNames(XTextDocument doc,
                                                       List<String> citationGroupNames) {

        // Collect unused jabrefPropertyNames
        Set<String> citationGroupNamesSet = citationGroupNames.stream().collect(Collectors.toSet());

        List<String> pageInfoThrash = new ArrayList<>();
        List<String> jabrefPropertyNames =
            UnoUserDefinedProperty.getListOfNames(doc)
            .stream()
            .filter(Codec52::isJabRefReferenceMarkName)
            .collect(Collectors.toList());
        for (String pn : jabrefPropertyNames) {
            if (!citationGroupNamesSet.contains(pn)) {
                pageInfoThrash.add(pn);
            }
        }
        return pageInfoThrash;
    }

    /**
     *  @return Optional.empty if all is OK, message text otherwise.
     */
    public Optional<String> healthReport(XTextDocument doc)
        throws
        NoDocumentException {
        List<String> pageInfoThrash =
            this.findUnusedJabrefPropertyNames(doc, this.getJabRefReferenceMarkNames(doc));
        if (pageInfoThrash.isEmpty()) {
            return Optional.empty(); // "Backend52: found no unused pageInfo data";
        }
        String msg =
            "Backend52: found unused pageInfo data, with names listed below.\n"
            + "In LibreOffice you may remove these in [File]/[Properties]/[Custom Properties]\n";
        msg += "" + String.join("\n", pageInfoThrash) + "";
        return Optional.of(msg);
    }

    private static void setPageInfoInDataInitial(List<Citation> citations,
                                                 Optional<OOFormattedText> pageInfo) {
        // attribute to last citation (initially localOrder == storageOrder)
        if (citations.size() > 0) {
            citations.get(citations.size() - 1).setPageInfo(pageInfo);
        }
    }

    private static void setPageInfoInData(CitationGroup cg,
                                          Optional<OOFormattedText> pageInfo) {
        List<Citation> citations = cg.getCitationsInLocalOrder();
        if (citations.size() > 0) {
            citations.get(citations.size() - 1).setPageInfo(pageInfo);
        }
    }

    private static Optional<OOFormattedText> getPageInfoFromData(CitationGroup cg) {
        List<Citation> citations = cg.getCitationsInLocalOrder();
        if (citations.size() > 0) {
            return citations.get(citations.size() - 1).getPageInfo();
        } else {
            return Optional.empty();
        }
    }

    /**
     *  We have circular dependency here: backend uses
     *  class from ...
     */
    public CitationGroup readCitationGroupFromDocumentOrThrow(XTextDocument doc, String refMarkName)
        throws
        WrappedTargetException,
        NoDocumentException {

        Optional<Codec52.ParsedMarkName> op = Codec52.parseMarkName(refMarkName);
        if (op.isEmpty()) {
            throw new IllegalArgumentException("readCitationGroupFromDocumentOrThrow:"
                                               + " found unparsable referenceMarkName");
        }
        Codec52.ParsedMarkName ov = op.get();
        CitationGroupID id = new CitationGroupID(refMarkName);
        List<Citation> citations = (ov.citationKeys.stream()
                                    .map(Citation::new)
                                    .collect(Collectors.toList()));

        Optional<OOFormattedText> pageInfo =
            (UnoUserDefinedProperty.getStringValue(doc, refMarkName)
             .map(OOFormattedText::fromString));
        pageInfo = Citation.normalizePageInfo(pageInfo);

        setPageInfoInDataInitial(citations, pageInfo);

        Optional<StorageBase.NamedRange> storedRange = (citationStorageManager
                                                        .getFromDocument(doc, refMarkName));

        if (storedRange.isEmpty()) {
            throw new IllegalArgumentException("readCitationGroupFromDocumentOrThrow:"
                                               + " referenceMarkName is not in the document");
        }

        CitationGroup cg = new CitationGroup(OOStyleDataModelVersion.JabRef52,
                                             id,
                                             storedRange.get(),
                                             ov.citationType,
                                             citations,
                                             Optional.of(refMarkName));
        return cg;
    }

    /**
     *  Create a reference mark with the given name, at the
     *  end of position.
     *
     *  On return {@code position} is collapsed, and is after the
     *  inserted space, or at the end of the reference mark.
     *
     *  @param position Collapsed to its end.
     *  @param insertSpaceAfter We insert a space after the mark, that
     *                          carries on format of characters from
     *                          the original position.
     */
    public CitationGroup createCitationGroup(XTextDocument doc,
                                             List<String> citationKeys,
                                             List<Optional<OOFormattedText>> pageInfosForCitations,
                                             InTextCitationType citationType,
                                             XTextCursor position,
                                             boolean insertSpaceAfter)
        throws
        CreationException,
        NoDocumentException,
        WrappedTargetException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        IllegalTypeException {

        Objects.requireNonNull(pageInfosForCitations);
        if (pageInfosForCitations.size() != citationKeys.size()) {
            throw new RuntimeException("pageInfosForCitations.size != citationKeys.size");
        }

        // Get a new refMarkName
        Set<String> usedNames = new HashSet<>(this.citationStorageManager.getUsedNames(doc));
        String xkey = (citationKeys.stream().collect(Collectors.joining(",")));
        String refMarkName = Codec52.getUniqueMarkName(usedNames, xkey, citationType);

        CitationGroupID cgid = new CitationGroupID(refMarkName);

        final int nCitations = citationKeys.size();
        final int last = nCitations - 1;

        // Build citations, add pageInfo to each citation
        List<Citation> citations = new ArrayList<>(nCitations);
        for (int i = 0; i < nCitations; i++) {
            Citation cit = new Citation(citationKeys.get(i));
            citations.add(cit);

            Optional<OOFormattedText> pageInfo = Citation.normalizePageInfo(pageInfosForCitations.get(i));
            switch (dataModel) {
            case JabRef52:
                if (i == last) {
                    cit.setPageInfo(pageInfo);
                } else {
                    if (pageInfo.isPresent()) {
                        LOGGER.warn("dataModel JabRef52"
                                    + " only supports pageInfo for the last citation of a group");
                    }
                }
                break;
            case JabRef53:
                cit.setPageInfo(pageInfo);
                break;
            }
        }

        /*
         * Apply to document
         */
        boolean withoutBrackets = (citationType == InTextCitationType.INVISIBLE_CIT);
        StorageBase.NamedRange storedRange =
            this.citationStorageManager.create(doc, refMarkName, position, insertSpaceAfter,
                                               withoutBrackets);

        switch (dataModel) {
        case JabRef52:
            Optional<OOFormattedText> pageInfo =
                Citation.normalizePageInfo(pageInfosForCitations.get(last));

            if (pageInfo.isPresent()) {
                String pageInfoString = OOFormattedText.toString(pageInfo.get());
                UnoUserDefinedProperty.createStringProperty(doc, refMarkName, pageInfoString);
            } else {
                // do not inherit from trash
                UnoUserDefinedProperty.removeIfExists(doc, refMarkName);
            }
            CitationGroup cg = new CitationGroup(OOStyleDataModelVersion.JabRef52,
                                                 cgid, storedRange, citationType, citations,
                                                 Optional.of(refMarkName));
            return cg;
        default:
            throw new RuntimeException("Backend52 requires JabRef52 dataModel");
        }
    }

    /**
     * @return A list with a nullable pageInfo entry for each citation in
     *         joinableGroups.
     *
     *  TODO: JabRef52 combinePageInfos is not reversible. Should warn
     *        user to check the result. Or ask what to do.
     */
    public static List<Optional<OOFormattedText>>
    combinePageInfosCommon(OOStyleDataModelVersion dataModel,
                           List<CitationGroup> joinableGroup) {
        switch (dataModel) {
        case JabRef52:
            // collect to cgPageInfos
            List<Optional<OOFormattedText>> cgPageInfos =
                (joinableGroup.stream()
                 .map(cg -> getPageInfoFromData(cg))
                 .collect(Collectors.toList()));

            // Try to do something of the cgPageInfos.
            String cgPageInfo = (cgPageInfos.stream()
                                 .filter(pi -> pi.isPresent())
                                 .map(pi -> OOFormattedText.toString(pi.get()))
                                 .distinct()
                                 .collect(Collectors.joining("; ")));

            int nCitations = (joinableGroup.stream()
                              .map(cg -> cg.numberOfCitations())
                              .mapToInt(Integer::intValue).sum());
            if ("".equals(cgPageInfo)) {
                cgPageInfo = null;
            }
            return OOStyleDataModelVersion.fakePageInfosForCitations(cgPageInfo, nCitations);

        case JabRef53:
            return (joinableGroup.stream()
                    .flatMap(cg -> (cg.citationsInStorageOrder.stream()
                                    .map(cit -> cit.getPageInfo())))
                    .collect(Collectors.toList()));
        default:
            throw new RuntimeException("unhandled dataModel here");
        }
    }

    /**
     *
     */
    public List<Optional<OOFormattedText>> combinePageInfos(List<CitationGroup> joinableGroup) {
        return combinePageInfosCommon(this.dataModel, joinableGroup);
    }

    public void removeCitationGroup(CitationGroup cg, XTextDocument doc)
        throws
        WrappedTargetException,
        NoDocumentException,
        NoSuchElementException,
        NotRemoveableException,
        IllegalTypeException,
        PropertyExistException {

        String refMarkName = cg.cgRangeStorage.getName();
        cg.cgRangeStorage.removeFromDocument(doc);
        UnoUserDefinedProperty.removeIfExists(doc, refMarkName);
    }

    /**
     *
     * @return Optional.empty if the reference mark is missing.
     *
     */
    public Optional<XTextRange> getMarkRange(CitationGroup cg, XTextDocument doc)
        throws
        NoDocumentException,
        WrappedTargetException {

        return cg.cgRangeStorage.getMarkRange(doc);
    }

    /**
     * Cursor for the reference marks as is, not prepared for filling,
     * but does not need cleanFillCursorForCitationGroup either.
     */
    public Optional<XTextCursor> getRawCursorForCitationGroup(CitationGroup cg, XTextDocument doc)
        throws
        NoDocumentException,
        WrappedTargetException {
        return cg.cgRangeStorage.getRawCursor(doc);
    }

    /**
     * Must be followed by call to cleanFillCursorForCitationGroup
     */
    public XTextCursor getFillCursorForCitationGroup(CitationGroup cg, XTextDocument doc)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {

        return cg.cgRangeStorage.getFillCursor(doc);
    }

    /** To be called after getFillCursorForCitationGroup */
    public void cleanFillCursorForCitationGroup(CitationGroup cg, XTextDocument doc)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {
        cg.cgRangeStorage.cleanFillCursor(doc);
    }

    public List<CitationEntry> getCitationEntries(XTextDocument doc, CitationGroups cgs)
        throws
        UnknownPropertyException,
        WrappedTargetException,
        NoDocumentException {

        switch (dataModel) {
        case JabRef52:
            // One context per CitationGroup: Backend52 (DataModel.JabRef52)
            // For DataModel.JabRef53 (Backend53) we need one context per Citation
            int n = cgs.numberOfCitationGroups();
            List<CitationEntry> citations = new ArrayList<>(n);
            for (CitationGroup cg : cgs.getCitationGroupsUnordered()) {
                String name = cg.cgid.asString();
                XTextCursor cursor = (this
                                      .getRawCursorForCitationGroup(cg, doc)
                                      .orElseThrow(RuntimeException::new));
                String context = OOUtil.getCursorStringWithContext(cursor, 30, 30, true);
                Optional<String> pageInfo = (cg.numberOfCitations() > 0
                                             ? (getPageInfoFromData(cg)
                                                .map(e -> OOFormattedText.toString(e)))
                                             : Optional.empty());
                CitationEntry entry = new CitationEntry(name, context, pageInfo);
                citations.add(entry);
            }
            return citations;
        case JabRef53:
            //xx
            throw new RuntimeException("getCitationEntries for JabRef53 is not implemented yet");
        default:
            throw new RuntimeException("getCitationEntries: unhandled dataModel ");
        }
    }

    /*
     * Only applies to storage. Citation markers are not changed.
     */
    public void applyCitationEntries(XTextDocument doc, List<CitationEntry> citationEntries)
        throws
        UnknownPropertyException,
        NotRemoveableException,
        PropertyExistException,
        PropertyVetoException,
        IllegalTypeException,
        IllegalArgumentException,
        NoDocumentException,
        WrappedTargetException {

        switch (dataModel) {
        case JabRef52:
            for (CitationEntry entry : citationEntries) {
                Optional<OOFormattedText> pageInfo = entry.getPageInfo().map(OOFormattedText::fromString);
                pageInfo = Citation.normalizePageInfo(pageInfo);
                if (pageInfo.isPresent()) {
                    String name = entry.getRefMarkName();
                    UnoUserDefinedProperty.createStringProperty(doc, name, pageInfo.get().asString());
                }
            }
            break;
        case JabRef53:
            //xx
            throw new RuntimeException("applyCitationEntries for JabRef53 is not implemented yet");
        default:
            throw new RuntimeException("applyCitationEntries: unhandled dataModel ");
        }
    }

} // end Backend52

