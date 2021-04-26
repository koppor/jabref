package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Compat {

    /**   What is the data stored?   */
    public enum DataModel {
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
        JabRef53
    }

    /**
     * Return the last pageInfo from the list, if there is one.
     */
    public static Optional<String> getJabRef52PageInfoFromList(List<String> pageInfosForCitations) {
        if (pageInfosForCitations == null) {
            return Optional.empty();
        }
        int n = pageInfosForCitations.size();
        if (n == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(pageInfosForCitations.get(n - 1));
    }

    /**
     * @param pageInfo Nullable.
     * @return JabRef53 style pageInfo list
     */
    public static List<String> fakePageInfosForCitations(String pageInfo,
                                                         int nCitations) {
        List<String> pageInfosForCitations = new ArrayList<>(nCitations);
        for (int i = 0; i < nCitations; i++) {
            if (i == nCitations - 1) {
                pageInfosForCitations.add(pageInfo);
            } else {
                pageInfosForCitations.add(null);
            }
        }
        return pageInfosForCitations;
    }

    /**
     * @return List of nullable pageInfo values, one for each citation.
     *         Result contains null for missing pageInfo values.
     *         The list itself is not null.
     *
     *         For Compat.DataModel.JabRef52 the last citation gets
     *         the CitationGroup.pageInfo
     *
     *         The result is passed to OOBibStyle.getCitationMarker or
     *          OOBibStyle.getNumCitationMarker
     *
     *          TODO: we may want class DataModel52, DataModel53 and split this.
     */
    public static List<String> getPageInfosForCitations(Compat.DataModel dataModel, CitationGroup cg) {
        switch (dataModel) {
        case JabRef52:
            // check conformance to dataModel
            for (int i = 0; i < cg.citations.size(); i++) {
                if (cg.citations.get(i).pageInfo.isPresent()) {
                    throw new RuntimeException("getPageInfosForCitations:"
                                               + " found Citation.pageInfo under JabRef52 dataModel");
                }
            }
            // A list of null values, except the last that comes from this.pageInfo
            return Compat.fakePageInfosForCitations(cg.pageInfo.orElse(null),
                                                    cg.citations.size());
        case JabRef53:
            // check conformance to dataModel
            if (cg.pageInfo.isPresent()) {
                throw new RuntimeException("getPageInfosForCitations:"
                                           + " found CitationGroup.pageInfo under JabRef53 dataModel");
            }
            // pageInfo values from citations, empty mapped to null.
            return (cg.citations.stream()
                    .map(cit -> cit.pageInfo.orElse(null))
                    .collect(Collectors.toList()));

        default:
            throw new RuntimeException("getPageInfosForCitations:"
                                       + "unhandled dataModel");
        }
    }

    /**
     * @return A list with one nullable pageInfo entry for each citation in
     *         joinableGroups.
     *
     *  TODO: JabRef52 combinePageInfos is not reversible. Should warn
     *        user to check the result. Or ask what to do.
     */
    public static List<String> combinePageInfos(Compat.DataModel dataModel,
                                                List<CitationGroup> joinableGroup) {
        switch (dataModel) {
        case JabRef52:
            // collect to cgPageInfos
            List<Optional<String>> cgPageInfos = (joinableGroup.stream()
                                                  .map(cg -> cg.pageInfo)
                                                  .collect(Collectors.toList()));

            // Try to do something of the cgPageInfos.
            String cgPageInfo = (cgPageInfos.stream()
                                 .filter(pi -> pi.isPresent())
                                 .map(pi -> pi.get())
                                 .distinct()
                                 .collect(Collectors.joining("; ")));

            int nCitations = (joinableGroup.stream()
                              .map(cg -> cg.citations.size())
                              .mapToInt(Integer::intValue).sum());

            return Compat.fakePageInfosForCitations(cgPageInfo, nCitations);

        case JabRef53:
            return (joinableGroup.stream()
                    .flatMap(cg -> (cg.citations.stream()
                                    .map(cit -> cit.pageInfo.orElse(null))))
                    .collect(Collectors.toList()));
        default:
            throw new RuntimeException("unhandled dataModel here");
        }
    }

}
