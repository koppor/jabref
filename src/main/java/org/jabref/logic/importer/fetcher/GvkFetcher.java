package org.jabref.logic.importer.fetcher;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import org.jabref.logic.help.HelpFile;
import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.Parser;
import org.jabref.logic.importer.SearchBasedParserFetcher;
import org.jabref.logic.importer.fetcher.transformers.GVKQueryTransformer;
import org.jabref.logic.importer.fileformat.PicaXmlParser;

import org.apache.http.client.utils.URIBuilder;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;

public class GvkFetcher implements SearchBasedParserFetcher {

    private static final String URL_PATTERN = "http://sru.gbv.de/gvk?";

    @Override
    public String getName() {
        return "GVK";
    }

    @Override
    public Optional<HelpFile> getHelpPage() {
        return Optional.of(HelpFile.FETCHER_GVK);
    }

    @Override
    public URL getURLForQuery(QueryNode luceneQuery) throws URISyntaxException, MalformedURLException, FetcherException {
        URIBuilder uriBuilder = new URIBuilder(URL_PATTERN);
        uriBuilder.addParameter("version", "1.1");
        uriBuilder.addParameter("operation", "searchRetrieve");
        uriBuilder.addParameter("query", new GVKQueryTransformer().transformLuceneQuery(luceneQuery).orElse(""));
        uriBuilder.addParameter("maximumRecords", "50");
        uriBuilder.addParameter("recordSchema", "picaxml");
        uriBuilder.addParameter("sortKeys", "Year,,1");
        return uriBuilder.build().toURL();
    }

    @Override
    public Parser getParser() {
        return new PicaXmlParser();
    }
}
