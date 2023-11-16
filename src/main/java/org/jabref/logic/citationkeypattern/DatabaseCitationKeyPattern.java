package org.jabref.logic.citationkeypattern;

import org.jabref.model.entry.types.EntryType;

import java.util.List;

public class DatabaseCitationKeyPattern extends AbstractCitationKeyPattern {

    private final GlobalCitationKeyPattern globalCitationKeyPattern;

    public DatabaseCitationKeyPattern(GlobalCitationKeyPattern globalCitationKeyPattern) {
        this.globalCitationKeyPattern = globalCitationKeyPattern;
    }

    @Override
    public List<String> getLastLevelCitationKeyPattern(EntryType entryType) {
        return globalCitationKeyPattern.getValue(entryType);
    }
}
