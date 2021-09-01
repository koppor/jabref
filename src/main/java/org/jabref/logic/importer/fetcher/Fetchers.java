package org.jabref.logic.importer.fetcher;

import dagger.Component;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.util.BuildInfo;

@Component(modules = {BuildInfo.class, ImportFormatPreferences.class})
public interface Fetchers {
    IEEE IEEE();
}
