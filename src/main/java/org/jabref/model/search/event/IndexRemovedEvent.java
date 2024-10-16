package org.jabref.model.search.event;

import org.jabref.model.entry.BibEntry;

import java.util.List;

public record IndexRemovedEvent(List<BibEntry> entries) {}
