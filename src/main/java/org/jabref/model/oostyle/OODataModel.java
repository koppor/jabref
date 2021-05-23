package org.jabref.model.oostyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**   What is the data stored?   */
public enum OODataModel {

    /**
     * JabRef52:
     *    pageInfo belongs to CitationGroup, not Citation.
     */
    JabRef52,

    /**
     * JabRef53:
     *    pageInfo belongs to Citation.
     */
    JabRef53;

    /**
     * @param pageInfo Nullable.
     * @return JabRef53 style pageInfo list with pageInfo in the last slot.
     */
    public static List<Optional<OOText>> fakePageInfos(String pageInfo, int nCitations) {
        List<Optional<OOText>> pageInfos = new ArrayList<>(nCitations);
        for (int i = 0; i < nCitations; i++) {
            pageInfos.add(Optional.empty());
        }
        if (pageInfo != null) {
            pageInfos.set(last, Optional.ofNullable(OOText.fromString(pageInfo)));
        }
        return pageInfos;
    }
}
