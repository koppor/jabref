package org.jabref.logic.importer;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.paging.Page;

import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;

public interface PagedSearchBasedParserFetcher extends SearchBasedParserFetcher, PagedSearchBasedFetcher {

    @Override
    default Page<BibEntry> performSearchPaged(String query, int pageNumber) throws FetcherException {
        // ADR-0014
        URL urlForQuery;
        try {
            urlForQuery = getURLForQuery(query, pageNumber);
        } catch (URISyntaxException | MalformedURLException e) {
            throw new FetcherException("Search URI crafted from complex search query is malformed", e);
        }
        return new Page<>(query, pageNumber, getBibEntries(urlForQuery));
    }

    private List<BibEntry> getBibEntries(URL urlForQuery) throws FetcherException {
        try (InputStream stream = getUrlDownload(urlForQuery).asInputStream()) {
            List<BibEntry> fetchedEntries = getParser().parseEntries(stream);
            fetchedEntries.forEach(this::doPostCleanup);
            return fetchedEntries;
        } catch (IOException e) {
            throw new FetcherException("A network error occurred while fetching from " + urlForQuery, e);
        } catch (ParseException e) {
            throw new FetcherException("An internal parser error occurred while fetching from " + urlForQuery, e);
        }
    }

    /**
     * Constructs a URL based on the query, size and page number.
     *  @param query the query string used to identify relevant documents
     * @param pageNumber the number of the page indexed from 0
     */
    URL getURLForQuery(String query, int pageNumber) throws URISyntaxException, MalformedURLException, FetcherException;

    @Override
    default URL getURLForQuery(String query) throws URISyntaxException, MalformedURLException, FetcherException {
        getURLForQuery(query, 0);
    }

    @Override
    default List<BibEntry> performSearch(String query) throws FetcherException {
        return SearchBasedParserFetcher.super.performSearch(query);
    }
}
