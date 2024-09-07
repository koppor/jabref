package org.jabref.logic.importer.fetcher;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.jabref.logic.importer.FulltextFetcher;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.identifier.DOI;

import me.friwi.jcefmaven.CefAppBuilder;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;
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
        if (doi.isEmpty()) {
            return Optional.empty();
        }

        System.setProperty("jcef.logSeverity", "VERBOSE");
        System.setProperty("jcef.logFile", "jcef.log");

        String source = SOURCE.formatted(doi.get().getDOI());

        CefAppBuilder builder = new CefAppBuilder();
        CefApp.addAppHandler(new CefAppHandlerAdapter(null) {
            @Override
            public void stateHasChanged(org.cef.CefApp.CefAppState state) {
                // Shutdown the app if the native CEF part is terminated
                if (state == CefApp.CefAppState.TERMINATED) {
                     // calling System.exit(0) appears to be causing assert errors,
                     // as its firing before all of the CEF objects shutdown.
                     //System.exit(0);
                }
            }
        });

        builder.getCefSettings().windowless_rendering_enabled = false;

        CefApp cefApp;
        try {
            cefApp = builder.build();
        } catch (Exception e) {
            LOGGER.error("Could not initialize CEF", e);
            throw new IOException(e);
        }

        CefClient client = cefApp.createClient();
        CefBrowser browser = client.createBrowser("about:blank", false, false);

        CompletableFuture<Void> result = new CompletableFuture<>();
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                super.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
                System.out.println("Loading state changed is loading " + isLoading);
            }

            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
                super.onLoadStart(browser, frame, transitionType);
                System.out.println("Load start");
            }

            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
                super.onLoadError(browser, frame, errorCode, errorText, failedUrl);
                System.out.println("Load error");
            }

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
                result.complete(null);
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

        // browser.loadURL(source);
        browser.loadURL("https://www.jabref.org");

        cefApp.doMessageLoopWork(1000);

        try {
            result.get();
        } catch (
                InterruptedException e) {
            throw new RuntimeException(e);
        } catch (
                ExecutionException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    @Override
    public TrustLevel getTrustLevel() {
        return TrustLevel.PUBLISHER;
    }
}
