package org.jabref.logic.importer.util;

import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.ParseException;
import org.jabref.logic.importer.fileformat.BibtexParser;
import org.jabref.model.entry.BibEntry;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implements an API to a GROBID server, as described at
 * https://grobid.readthedocs.io/en/latest/Grobid-service/#grobid-web-services
 * <p>
 * Note: Currently a custom GROBID server is used...
 * https://github.com/NikodemKch/grobid
 * <p>
 * The methods are structured to match the GROBID server api.
 * Each method corresponds to a GROBID service request. Only the ones already used are already implemented.
 */
public class GrobidService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrobidService.class);

    public enum ConsolidateCitations {
        NO(0),
        WITH_METADATA(1),
        WITH_DOI_ONLY(2);
        private final int code;

        ConsolidateCitations(int code) {
            this.code = code;
        }

        public int getCode() {
            return this.code;
        }
    }

    private final GrobidPreferences grobidPreferences;

    public GrobidService(GrobidPreferences grobidPreferences) {
        this.grobidPreferences = grobidPreferences;
        if (!grobidPreferences.isGrobidEnabled()) {
            throw new UnsupportedOperationException("Grobid was used but not enabled.");
        }
    }

    /**
     * Calls the Grobid server for converting the citation into a BibEntry
     *
     * @return A BibEntry for the String
     * @throws IOException if an I/O exception during the call occurred or no BibTeX entry could be determined
     */
    public Optional<BibEntry> processCitation(
            String rawCitation,
            ImportFormatPreferences importFormatPreferences,
            ConsolidateCitations consolidateCitations)
            throws IOException, ParseException {
        Connection.Response response =
                Jsoup.connect(grobidPreferences.getGrobidURL() + "/api/processCitation")
                        .header("Accept", MediaTypes.APPLICATION_BIBTEX)
                        .data("citations", rawCitation)
                        .data(
                                "consolidateCitations",
                                String.valueOf(consolidateCitations.getCode()))
                        .method(Connection.Method.POST)
                        .ignoreContentType(true)
                        .timeout(100_000)
                        .execute();
        String httpResponse = response.body();
        LOGGER.debug("raw citation -> response: {}, {}", rawCitation, httpResponse);

        if (httpResponse == null
                || "@misc{-1,\n  author = {}\n}\n".equals(httpResponse)
                || httpResponse.equals(
                        "@misc{-1,\n  author = {"
                                + rawCitation
                                + "}\n}\n")) { // This filters empty BibTeX entries
            throw new IOException("The GROBID server response does not contain anything.");
        }

        return BibtexParser.singleFromString(httpResponse, importFormatPreferences);
    }

    public List<BibEntry> processPDF(Path filePath, ImportFormatPreferences importFormatPreferences)
            throws IOException, ParseException {
        Connection.Response response =
                Jsoup.connect(grobidPreferences.getGrobidURL() + "/api/processHeaderDocument")
                        .header("Accept", MediaTypes.APPLICATION_BIBTEX)
                        .data("input", filePath.toString(), Files.newInputStream(filePath))
                        .method(Connection.Method.POST)
                        .ignoreContentType(true)
                        .timeout(20000)
                        .execute();

        String httpResponse = response.body();

        return getBibEntries(importFormatPreferences, httpResponse);
    }

    public List<BibEntry> processReferences(
            List<Path> pathList, ImportFormatPreferences importFormatPreferences)
            throws IOException, ParseException {
        List<BibEntry> entries = new ArrayList<>();
        for (Path filePath : pathList) {
            entries.addAll(processReferences(filePath, importFormatPreferences));
        }

        return entries;
    }

    public List<BibEntry> processReferences(
            Path filePath, ImportFormatPreferences importFormatPreferences)
            throws IOException, ParseException {
        Connection.Response response =
                Jsoup.connect(grobidPreferences.getGrobidURL() + "/api/processReferences")
                        .header("Accept", MediaTypes.APPLICATION_BIBTEX)
                        .data("input", filePath.toString(), Files.newInputStream(filePath))
                        .data(
                                "consolidateCitations",
                                String.valueOf(ConsolidateCitations.WITH_METADATA))
                        .method(Connection.Method.POST)
                        .ignoreContentType(true)
                        .timeout(20000)
                        .execute();

        String httpResponse = response.body();

        return getBibEntries(importFormatPreferences, httpResponse);
    }

    private static List<BibEntry> getBibEntries(
            ImportFormatPreferences importFormatPreferences, String httpResponse)
            throws IOException, ParseException {
        if (httpResponse == null
                || "@misc{-1,\n  author = {}\n}\n"
                        .equals(httpResponse)) { // This filters empty BibTeX entries
            throw new IOException("The GROBID server response does not contain anything.");
        }

        BibtexParser parser = new BibtexParser(importFormatPreferences);
        List<BibEntry> result = parser.parseEntries(httpResponse);
        result.forEach(entry -> entry.setCitationKey(""));
        return result;
    }
}
