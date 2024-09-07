package org.jabref.logic.importer.fetcher;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import org.jabref.logic.importer.FulltextFetcher;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.identifier.DOI;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefStringVisitor;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FulltextFetcher implementation that attempts to find a PDF URL at <a href="https://pubs.acs.org/">ACS</a>.
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

        CefAppBuilder builder = new CefAppBuilder();
        builder.setAppHandler(new MavenCefAppHandlerAdapter(){});
        CefApp cefApp;
        try {
            cefApp = builder.build();
        } catch (Exception e) {
            LOGGER.error("Could not initialize CEF", e);
            throw new IOException(e);
        }

        CefClient client = cefApp.createClient();
        CefBrowser browser = client.createBrowser(source, false, false);

        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                System.out.println("lalala");
                if (frame.isMain()) {
                    frame.executeJavaScript(
                            "document.documentElement.outerHTML;",
                            frame.getURL(),
                            0
                    );
                }
            }
        });

        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
                // Capture the result of the JavaScript execution in the console message
                System.out.println("Page HTML content:\n" + message);
                return true;
            }
        });

        browser.loadURL(source);

        try {
            Thread.sleep(5000);
        } catch (
                InterruptedException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    @Override
    public TrustLevel getTrustLevel() {
        return TrustLevel.PUBLISHER;
    }
}
