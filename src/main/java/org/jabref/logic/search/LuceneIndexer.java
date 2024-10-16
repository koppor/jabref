package org.jabref.logic.search;

import org.apache.lucene.search.SearcherManager;
import org.jabref.logic.util.BackgroundTask;
import org.jabref.model.entry.BibEntry;

import java.util.Collection;

public interface LuceneIndexer {
    void updateOnStart(BackgroundTask<?> task);

    void addToIndex(Collection<BibEntry> entries, BackgroundTask<?> task);

    void removeFromIndex(Collection<BibEntry> entries, BackgroundTask<?> task);

    void updateEntry(BibEntry entry, String oldValue, String newValue, BackgroundTask<?> task);

    void removeAllFromIndex();

    void rebuildIndex(BackgroundTask<?> task);

    SearcherManager getSearcherManager();

    void close();

    void closeAndWait();
}
