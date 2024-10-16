package org.jabref.logic;

import org.jabref.logic.importer.ParserResult;

import java.util.List;

public sealed interface UiCommand {
    record BlankWorkspace() implements UiCommand {}

    record JumpToEntryKey(String citationKey) implements UiCommand {}

    record OpenDatabases(List<ParserResult> parserResults) implements UiCommand {}

    record AutoSetFileLinks(List<ParserResult> parserResults) implements UiCommand {}
}
