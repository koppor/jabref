package org.jabref.logic.importer.fetcher;

import com.microsoft.applicationinsights.core.dependencies.http.client.utils.URIBuilder;
import org.jabref.logic.importer.FetcherException;
import org.jabref.logic.importer.ParseException;
import org.jabref.logic.importer.fileformat.ACMPortalParser;
import org.jabref.logic.net.URLDownload;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ACMPortalParserTest {
    ACMPortalParser parser;
    List<BibEntry> e;

    @BeforeEach
    void setUp() {
        parser = new ACMPortalParser();

        e = new ArrayList<>();
        BibEntry expected = new BibEntry(StandardEntryType.Article);

        expected.setField(StandardField.AUTHOR, "Arnowitz, Jonathan and Dykstra-Erickson, Elizabeth");
        expected.setField(StandardField.DAY, "1");
        expected.setField(StandardField.DOI, "10.1145/1041280.1041283");
        expected.setField(StandardField.MONTH, "1");
        expected.setField(StandardField.PUBLISHER, "Association for Computing Machinery");
        expected.setField(StandardField.PUBSTATE, "New York, NY, USA");
        expected.setField(StandardField.TITLE, "Hello!");
        expected.setField(StandardField.YEAR, "2005");
        e.add(expected);
        }

    @Test
    void testParseEntries() throws URISyntaxException, IOException {
        CookieHandler.setDefault(new CookieManager());
        URL r = new URIBuilder("https://dl.acm.org/action/doSearch?AllField=10.1145/1041280.1041283").build().toURL();
        try (InputStream stream = new URLDownload(r).asInputStream()) {
            parser.parseEntries(stream);
        } catch (ParseException parseException) {
            parseException.printStackTrace();
        }
        assertEquals(Collections.singletonList(e), Collections.singletonList(parser.list));
    }

    @Test
    void testParseSearchPage() throws URISyntaxException, MalformedURLException {
        List<String> testDoiList = new LinkedList<>();
        testDoiList.add("10.1145/1041280.1041283");
        CookieHandler.setDefault(new CookieManager());
        URL r = new URIBuilder("https://dl.acm.org/action/doSearch?AllField=10.1145/1041280.1041283").build().toURL();

        String htmlFile = "";
        try (InputStream stream = new URLDownload(r).asInputStream()) {
            BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            while(in.readLine()!= null){
                htmlFile = htmlFile + (char)10 + in.readLine();
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        parser.parseSearchPage(htmlFile);
        assertEquals(testDoiList, parser.parseSearchPage(htmlFile));
    }

    @Test
    void testGetBibEntriesFromDoilist() throws FetcherException {
        List<String> testDoiList = new LinkedList<>();
        testDoiList.add("10.1145/1041280.1041283");
        parser.getBibEntriesFromDoilist(testDoiList);
        assertEquals(Collections.singletonList(e), Collections.singletonList(parser.list));
    }

    @Test
    void testGetURLForDoi() throws FetcherException, MalformedURLException, URISyntaxException {
        String testQuery = "This+is+a+test+query";
        URL url = parser.getURLForDoi(testQuery);
        String expected = "https://dl.acm.org/action/exportCiteProcCitation?dois=This%2Bis%2Ba%2Btest%2Bquery&targetFile=custom-bibtex&format=bibTex";
        assertEquals(expected, url.toString());
    }

    @Test
    void testFetchEntry() throws URISyntaxException {
        CookieHandler.setDefault(new CookieManager());
        try (InputStream stream = new URLDownload(parser.getURLForDoi("10.1145/1041280.1041283")).asInputStream()) {
            parser.fetchEntry(stream);
        } catch (IOException | FetcherException parseException) {
            parseException.printStackTrace();
        }
        assertEquals(Collections.singletonList(e), Collections.singletonList(parser.list));
    }

    @Test
    void testGetOneInfo() {
        parser.xmlFile = "test\"DOI\":\"10.1145/1015706.1015800\"test";
        assertEquals("10.1145/1015706.1015800", parser.getOneInfo(parser.xmlFile));
    }

    @Test
    void testGetMoreThanOneInfo() {
        parser.xmlFile = "test\"date-parts\":[[2004,8,1]]\"test";
        assertEquals("[2004,8,1", parser.getMoreThanOneInfo(parser.xmlFile));
    }

    @Test
    void testSplitWithLastSymbol() {
        parser.xmlFile = "test\":\"hellotest\":\"test";
        assertEquals("hello", parser.splitWithLastSymbol(parser.xmlFile, 't'));
    }

    @Test
    void testNoEntryFound() throws URISyntaxException, MalformedURLException {
        CookieHandler.setDefault(new CookieManager());
        URL r = new URIBuilder("https://dl.acm.org/action/doSearch?AllField=10.1145/1041280.1041284").build().toURL();
        try (InputStream stream = new URLDownload(r).asInputStream()) {
            parser.parseEntries(stream);
        } catch (ParseException | IOException parseException) {
            parseException.printStackTrace();
        }
        assertEquals(new ArrayList<>(), parser.list);
    }
}
