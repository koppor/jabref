package org.jabref.logic.importer.fetcher;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import org.jabref.logic.importer.FulltextFetcher;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.identifier.DOI;

import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FulltextFetcher implementation that attempts to find a PDF URL at ACS.
 */
public class ACS implements FulltextFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ACS.class);

    private static final String SOURCE = "https://pubs.acs.org/doi/abs/%s";

    /**
     * Tries to find a fulltext URL for a given BibTeX entry.
     * Requires the entry to have a DOI field.
     * In case no DOI is present, an empty Optional is returned.
     */
    @Override
    public Optional<URL> findFullText(BibEntry entry) throws IOException {
        Objects.requireNonNull(entry);
        Optional<DOI> doi = entry.getField(StandardField.DOI).flatMap(DOI::parse);
        if (!doi.isPresent()) {
            return Optional.empty();
        }

        String source = SOURCE.formatted(doi.get().getDOI());

        try (final WebClient webClient = new WebClient(BrowserVersion.CHROME)) {
            webClient.getOptions().setSSLClientProtocols("TLSv1.3", "TLSv1.2");
            // inspired by https://www.innoq.com/en/blog/2016/01/webscraping/
            webClient.getCookieManager().setCookiesEnabled(true);
            webClient.getOptions().setJavaScriptEnabled(true);
            webClient.getOptions().setTimeout(10_000);
            webClient.waitForBackgroundJavaScript(5000);
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setPrintContentOnFailingStatusCode(true);

            HtmlPage page = webClient.getPage(source);
            boolean pdfButtonExists = page.querySelectorAll("a[title=\"PDF\"].article__btn__secondary").isEmpty();
            if (pdfButtonExists) {
                LOGGER.info("Fulltext PDF found at ACS.");
                // We "guess" the URL instead of parsing the HTML for the actual link
                return Optional.of(new URL(source.replaceFirst("/abs/", "/pdf/")));
            }
        }
        return Optional.empty();
    }

    @Override
    public TrustLevel getTrustLevel() {
        return TrustLevel.PUBLISHER;
    }
}
