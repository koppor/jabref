package org.jabref.logic.importer.fetcher.transformers;

public class ScholarQueryTransformer extends YearAndYearRangeByFilteringQueryTransformer {

    @Override
    protected String getLogicalAndOperator() {
        return " AND ";
    }

    @Override
    protected String getLogicalOrOperator() {
        return " OR ";
    }

    @Override
    protected String getLogicalNotOperator() {
        return "-";
    }

    @Override
    protected String handleAuthor(String author) {
        return createKeyValuePair("author", author);
    }

    @Override
    protected String handleTitle(String title) {
        return createKeyValuePair("allintitle", title);
    }

    @Override
    protected String handleJournal(String journalTitle) {
        return createKeyValuePair("source", journalTitle);
    }
}
