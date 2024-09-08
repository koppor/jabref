package org.jabref.logic.importer.fetcher;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import org.jabref.logic.importer.FulltextFetcher;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.identifier.DOI;

import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FulltextFetcher implementation that attempts to find a PDF URL at <a href="https://pubs.acs.org/">ACS</a>.
 *
 * Alternatives concidered: https://stackoverflow.com/a/53099311/873282
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

        // You can optionally pass a Settings object here,
        // constructed using Settings.Builder
        JBrowserDriver driver = new JBrowserDriver(Settings.builder().
                                                           timezone(Timezone.AMERICA_NEWYORK).build());

        driver.get(source);
        System.out.println(driver.getStatusCode());
        System.out.println(driver.getPageSource());
        driver.quit();

        return Optional.empty();
    }

    @Override
    public TrustLevel getTrustLevel() {
        return TrustLevel.PUBLISHER;
    }
}
