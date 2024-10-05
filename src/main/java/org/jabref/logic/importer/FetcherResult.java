package org.jabref.logic.importer;

import org.jabref.logic.importer.fetcher.TrustLevel;

import java.net.URL;

public final class FetcherResult {
    private final TrustLevel trust;
    private final URL source;

    public FetcherResult(TrustLevel trust, URL source) {
        this.trust = trust;
        this.source = source;
    }

    public TrustLevel getTrust() {
        return trust;
    }

    public URL getSource() {
        return source;
    }
}
