package org.jabref.logic.importer;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jabref.logic.importer.fetcher.DoiFetcher;
import org.jabref.logic.net.URLDownload;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.identifier.DOI;

import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONException;
import kong.unirest.json.JSONObject;

import static org.jabref.gui.edit.automaticfiededitor.AbstractAutomaticFieldEditorTabViewModel.LOGGER;

public class AutoEntryAddition {
    private static final String CROSSREF_API = "https://api.crossref.org/works?query=";
    private static final String ARXIV_API = "http://export.arxiv.org/api/query?id_list=";

    private static final Pattern ARXIV_ID_PATTERN = Pattern.compile("(\\d{4}\\.\\d{4,5})(v\\d+)?");

    private ImportFormatPreferences preferences;
    // Function to determine DOI from a given title - just a draft function to test some functionality
    public Optional<DOI> findDOI(String title) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            URLDownload download = new URLDownload(CROSSREF_API + encodedTitle);
            String response = download.asString();
            JSONObject jsonResponse = new JSONObject(response);

            if (jsonResponse.getJSONObject("message").getInt("total-results") > 0) {
                JSONArray items = jsonResponse.getJSONObject("message").getJSONArray("items");
                String doi = items.getJSONObject(0).getString("DOI");
                return DOI.parse(doi);
            }
        } catch (
                IOException |
                JSONException e) {
            LOGGER.error("Error while fetching DOI from CrossRef", e);
        }
        return Optional.empty();
    }

    // Function to extract an arxiv ID from a given URL through pattern matching
    public static String extractArXivIDFromURL(String url) {
        Matcher matcher = ARXIV_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // draft function to fetch entries through arxiv ID
    public Optional<BibEntry> fetchEntryByArxivID(String arxivID) throws FetcherException {
        try {
            URLDownload download = new URLDownload(ARXIV_API + arxivID);
            String response = download.asString();
            JSONObject jsonResponse = new JSONObject(response);

            JSONArray entries = jsonResponse.getJSONArray("entry");
            if (entries.length() > 0) {
                JSONObject entry = entries.getJSONObject(0);
                BibEntry bibEntry = new BibEntry();

                return Optional.of(bibEntry);
            }
        } catch (IOException | JSONException e) {
            LOGGER.error("Error while fetching entry from ArXiv", e);
        }
        return Optional.empty();
    }

    public Optional<BibEntry> fetchEntryByTitle(String title) throws FetcherException {
        Optional<DOI> optionalDOI = findDOI(title);
        if (optionalDOI.isPresent()) {
            DoiFetcher fetcher = new DoiFetcher(preferences);
            return fetcher.performSearchById(optionalDOI.get().getDOI());
        }

        return Optional.empty();
    }
}


