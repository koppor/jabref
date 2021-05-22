package org.jabref.model.oostyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**   What is the data stored?   */
public enum OODataModel {
    /**
     * JabRef52:
     *    pageInfo belongs to CitationGroup, not Citation.
     *
     *    Note: pageInfo stored in [File]/[Properties]/[Custom Properties]
     *
     *          under the same name as the reference mark for the
     *          CitationGroup.
     *
     *          JabRef "Merge" leaves pageInfo values of the parts joined
     *          around. Separate, or a new citation may pick these up.
     *
     *          In-text citep format: "[ ... ; pageInfo]", injected just before the
     *                  closing parenthesis (here "]"), with "; " as a separator.
     *
     *          citet format: the same, (injected to parens around
     *                        year of last citation of the group)
     */
    JabRef52,

    /**
     * JabRef53:
     *    pageInfo belongs to Citation.
     *    Need: formatting citation needs to know about these, inject after each year part
     */
    JabRef53;

    /**
     * @param pageInfo Nullable.
     * @return JabRef53 style pageInfo list
     */
    public static List<Optional<OOText>> fakePageInfosForCitations(String pageInfo,
                                                                            int nCitations) {
        List<Optional<OOText>> pageInfosForCitations = new ArrayList<>(nCitations);
        for (int i = 0; i < nCitations; i++) {
            if (i == (nCitations - 1) && (pageInfo != null)) {
                pageInfosForCitations.add(Optional.of(OOText.fromString(pageInfo)));
            } else {
                pageInfosForCitations.add(Optional.empty());
            }
        }
        return pageInfosForCitations;
    }
}
