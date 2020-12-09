package org.jabref.logic.importer.fetcher;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.jabref.logic.help.HelpFile;
import org.jabref.logic.importer.*;
import org.jabref.logic.importer.fileformat.ACMPortalParser;
import org.jabref.logic.importer.fileformat.BibtexParser;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.strings.StringUtil;
import org.jabref.model.util.DummyFileUpdateMonitor;

import org.apache.http.client.utils.URIBuilder;

public class ACMPortalFetcher implements SearchBasedParserFetcher {

    public static final String SEARCH_URL = "https://dl.acm.org/action/doSearch";

    public final ImportFormatPreferences preferences;

    public ACMPortalFetcher(ImportFormatPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @Override
    public String getName() {
        return "ACM Portal";
    }

    @Override
    public Optional<HelpFile> getHelpPage() {
        return Optional.of(HelpFile.FETCHER_ACM);
    }

    /*
     * This method changes the given query from the search bar in this form. >"words" "are" "split"<.
     * For the Url we need >words+are+split<
     * So we delete the spaces and change the quotation marks to plus where needed.
     */
    public static String createQueryString(String query) {
        // deletes all spaces
        query = query.trim().replaceAll("\\s+", "");

        // changes the quotation marks between search results to a + (used with complex search)
        String newQuery = "";
        char lastChar = '\"';
        for (int i = 0; i < query.length(); i++) {
            // check for quotations
            if (query.charAt(i) == '\"') {
                // check if the char before was also a quotation mark (because we only need one + between words)
                // check if you are at the last char of the query (so there is no plus needed)
                if (lastChar != '\"' && i + 1 != query.length()) {
                    newQuery += "+";
                }
            } else {
                newQuery += query.charAt(i);
            }
            lastChar = query.charAt(i);
        }
        return newQuery;
    }

    /*
     * Constructing the url for the searchpage.
     * The query is the input from the search bar.
     *
     * Good to know:
     * This method is used at SearchBasedParserFetcher line 49.
     *
     */
    public URL getURLForQuery(String query) throws URISyntaxException, MalformedURLException, FetcherException {
        URIBuilder uriBuilder = new URIBuilder(SEARCH_URL);
        uriBuilder.addParameter("AllField", createQueryString(query));
        return uriBuilder.build().toURL();
    }

    /*
     * Gives back an instance of our ACMPortalParser.
     *
     * What happens next in the program:
     * At line 77 in the SearchBasedParserFetcher an InputStream is created by the given URL
     * (see getURLForQuery). Then in line 78 this method is used as followed:
     * List<BibEntry> fetchedEntries = getParser().parseEntries(stream);
     */
    public Parser getParser() {
        return new ACMPortalParser();
    }
}
