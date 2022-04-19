package org.jabref.logic.importer;

import java.util.Collections;
import java.util.List;

import org.jabref.model.entry.BibEntry;

import org.apache.lucene.queryparser.flexible.core.QueryNodeParseException;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.core.parser.SyntaxParser;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;

import static org.jabref.logic.importer.fetcher.transformers.AbstractQueryTransformer.NO_EXPLICIT_FIELD;

/**
 * Searches web resources for bibliographic information based on a free-text query.
 * May return multiple search hits.
 *
 * Note: All queries should go through the Search Delegator, as this allows the use of the query transformer/or explicit use of the raw query
 *       The delegator additionally performs post filtering of results where possible (for transformed queries)
 */
public interface SearchBasedFetcher extends WebFetcher {

    /**
     * Looks for hits which are matched by the given free-text query.
     *
     * @param searchQuery query the query string used to identify relevant documents
     * @return a list of {@link BibEntry}, which are matched by the query (may be empty)
     */
    List<BibEntry> performSearch(String searchQuery) throws FetcherException;

}
