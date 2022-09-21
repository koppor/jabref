package org.jabref.logic.shared.restserver.core.repository;

import java.io.IOException;

import org.jabref.logic.crawler.Crawler;
import org.jabref.logic.exporter.SaveException;

import org.eclipse.jgit.api.errors.GitAPIException;

public class CrawlTask implements Runnable {
    private final Crawler crawler;
    private TaskStatus status;

    public CrawlTask(Crawler crawler) {
        this.crawler = crawler;
    }

    public TaskStatus getStatus() {
        return status;
    }

    @Override
    public void run() {
        status = TaskStatus.RUNNING;
        try {
            crawler.performCrawl();
        } catch (IOException | GitAPIException | SaveException e) {
            status = TaskStatus.FAILED;
            throw new RuntimeException(e);
        }
        status = TaskStatus.DONE;
    }
}
