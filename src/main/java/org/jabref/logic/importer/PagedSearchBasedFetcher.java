package org.jabref.logic.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.paging.Page;

public interface PagedSearchBasedFetcher extends SearchBasedFetcher {

    /**
     * @param searchQuery query string that can be parsed into a lucene query
     * @param pageNumber  requested site number indexed from 0
     * @return Page with search results
     */
    Page<BibEntry> performSearchPaged(String searchQuery, int pageNumber) throws FetcherException;

    /**
     * @return default pageSize
     */
    default int getPageSize() {
        return 20;
    }

    /**
     * This method is used to send complex queries using fielded search.
     *
     * @param query the query string used to identify relevant documents
     * @return a list of {@link BibEntry}, which are matched by the query (may be empty)
     */
    default List<BibEntry> performSearch(String query) throws FetcherException {
        return new ArrayList<>(performSearchPaged(query, 0).getContent());
    }

}
