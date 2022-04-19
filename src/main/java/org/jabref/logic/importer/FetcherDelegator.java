package org.jabref.logic.importer;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jabref.logic.importer.fetcher.transformers.AbstractQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.ArXivQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.CollectionOfComputerScienceBibliographiesQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.DBLPQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.DefaultQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.GVKQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.IEEEQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.JstorQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.ScholarQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.SpringerQueryTransformer;
import org.jabref.logic.importer.fetcher.transformers.ZbMathQueryTransformer;
import org.jabref.model.entry.BibEntry;

import org.apache.lucene.queryparser.flexible.core.QueryNodeParseException;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.core.parser.SyntaxParser;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;

import static org.jabref.logic.importer.fetcher.transformers.AbstractQueryTransformer.NO_EXPLICIT_FIELD;

/**
 * Manages how a query is executed (either raw or transformed)
 */
public class FetcherDelegator {

    public FetcherDelegator() {
    }

    /**
     * Looks for hits which are matched by the given free-text query.
     *
     * @param searchQuery query string that can be parsed into a lucene query
     * @return a list of {@link BibEntry}, which are matched by the query (may be empty)
     */
    List<BibEntry> performSearch(String searchQuery, SearchBasedFetcher fetcher) throws FetcherException {
        if (searchQuery.isBlank()) {
            return Collections.emptyList();
        }
        // TODO: The delegator furthermore should detect whether the proposed query is a specialized query (e.g. doi and use the appropriate fetcher, instead of the provided one

        SyntaxParser parser = new StandardSyntaxParser();
        QueryNode queryNode;
        try {
            queryNode = parser.parse(searchQuery, NO_EXPLICIT_FIELD);
        } catch (QueryNodeParseException e) {
            throw new FetcherException("An error occurred when parsing the query");
        }

        AbstractQueryTransformer transformer = getTransformer(fetcher);
        Optional<String> transformedQuery = transformer.transformLuceneQuery(queryNode);
        return postFilterResult(fetcher.performSearch(transformedQuery.orElse(searchQuery)), transformer);
    }

    private AbstractQueryTransformer getTransformer(SearchBasedFetcher fetcher) {
        String lowerCaseFetcherName = fetcher.getName().toLowerCase();
        return switch (lowerCaseFetcherName) {
            case "arxiv" -> new ArXivQueryTransformer();
            case "ieeexplore" -> new IEEEQueryTransformer();
            case "gvk" -> new GVKQueryTransformer();
            case "springer" -> new SpringerQueryTransformer();
            case "google scholar" -> new ScholarQueryTransformer();
            case "dblp" -> new DBLPQueryTransformer();
            case "collection of computer science bibliographies" -> new CollectionOfComputerScienceBibliographiesQueryTransformer();
            case "zbmath" -> new ZbMathQueryTransformer();
            case "jstor" -> new JstorQueryTransformer();
            default -> new DefaultQueryTransformer();
        };
    }

    /**
     * Depending on the type of query transformer the result has to be filtered
     *
     * @param result      the result
     * @param transformer transformer used for the query, depending on the type, post filtering is required
     * @return a filtered result list
     */
    private List<BibEntry> postFilterResult(List<BibEntry> result, AbstractQueryTransformer transformer) {
        // TODO
        return result;
    }

    /**
     * Looks for hits which are matched by the given free-text query.
     *
     * @param searchQuery query string
     * @return a list of {@link BibEntry}, which are matched by the query (may be empty)
     */
    List<BibEntry> performSearchRaw(String searchQuery, SearchBasedFetcher fetcher) throws FetcherException {
        return fetcher.performSearch(searchQuery);
    }
}
