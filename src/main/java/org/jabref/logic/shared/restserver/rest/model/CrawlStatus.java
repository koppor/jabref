package org.jabref.logic.shared.restserver.rest.model;

public class CrawlStatus {
    public boolean currentlyCrawling;

    public CrawlStatus(boolean currentlyCrawling) {
        this.currentlyCrawling = currentlyCrawling;
    }
}
