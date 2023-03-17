package org.jabref.logic.shared.restserver.core.serialization;

import java.util.Arrays;
import java.util.Optional;

import org.jabref.logic.shared.restserver.rest.model.BibEntryDTO;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.EntryType;
import org.jabref.model.entry.types.IEEETranEntryType;
import org.jabref.model.entry.types.StandardEntryType;
import org.jabref.model.entry.types.UnknownEntryType;

public class BibEntryMapper {

    /**
     * Maps the relevant standard fields of the complete entry into a DTO
     * Note: This step is lossy. The BibEntryDTO contains way less information than the BibEntry.
     */
    public static BibEntryDTO map(BibEntry entry) {
        BibEntryDTO mappedEntry = new BibEntryDTO();
        mappedEntry.entryType = entry.getType().getName();
        entry.getCitationKey().ifPresent(s -> mappedEntry.citationKey = s);
        entry.getField(StandardField.ADDRESS).ifPresent(s -> mappedEntry.address = s);
        entry.getField(StandardField.AUTHOR).ifPresent(s -> mappedEntry.author = s);
        entry.getField(StandardField.BOOKTITLE).ifPresent(s -> mappedEntry.booktitle = s);
        entry.getField(StandardField.CHAPTER).ifPresent(s -> mappedEntry.chapter = s);
        entry.getField(StandardField.EDITION).ifPresent(s -> mappedEntry.edition = s);
        entry.getField(StandardField.EDITOR).ifPresent(s -> mappedEntry.editor = s);
        entry.getField(StandardField.HOWPUBLISHED).ifPresent(s -> mappedEntry.howpublished = s);
        entry.getField(StandardField.INSTITUTION).ifPresent(s -> mappedEntry.institution = s);
        entry.getField(StandardField.JOURNAL).ifPresent(s -> mappedEntry.journal = s);
        entry.getField(StandardField.MONTH).ifPresent(s -> mappedEntry.month = s);
        entry.getField(StandardField.NOTE).ifPresent(s -> mappedEntry.note = s);
        entry.getField(StandardField.NUMBER).ifPresent(s -> mappedEntry.number = s);
        entry.getField(StandardField.ORGANIZATION).ifPresent(s -> mappedEntry.organization = s);
        entry.getField(StandardField.PAGES).ifPresent(s -> mappedEntry.pages = s);
        entry.getField(StandardField.PUBLISHER).ifPresent(s -> mappedEntry.publisher = s);
        entry.getField(StandardField.SCHOOL).ifPresent(s -> mappedEntry.school = s);
        entry.getField(StandardField.SERIES).ifPresent(s -> mappedEntry.series = s);
        entry.getField(StandardField.TITLE).ifPresent(s -> mappedEntry.title = s);
        entry.getField(StandardField.VOLUME).ifPresent(s -> mappedEntry.volume = s);
        entry.getField(StandardField.YEAR).ifPresent(s -> mappedEntry.year = s);
        entry.getField(StandardField.DATE).ifPresent(s -> mappedEntry.date = s);
        return mappedEntry;
    }

    /**
     * Maps the BibEntryDTO into a BibEntry with the fields provided by the DTO mapped into the BibEntry
     * Note that the Information provided by the DTO cannot be used to reconstruct
     * a BibEntry that was previously mapped using the map method above.
     */
    public static BibEntry map(BibEntryDTO entry) {
        if ((entry.entryType == null) || entry.entryType.isBlank()) {
            throw new IllegalArgumentException("Entry has to have an entry type");
        }
        if ((entry.citationKey == null) || entry.citationKey.isBlank()) {
            throw new IllegalArgumentException("Entry has to have a citation key");
        }
        BibEntry mappedEntry = new BibEntry(getEntryType(entry.entryType));
        mappedEntry.withCitationKey(entry.citationKey);
        if ((entry.address != null) && !entry.address.isBlank()) {
            mappedEntry.withField(StandardField.ADDRESS, entry.address);
        }
        if ((entry.author != null) && !entry.author.isBlank()) {
            mappedEntry.withField(StandardField.AUTHOR, entry.author);
        }
        if ((entry.booktitle != null) && !entry.booktitle.isBlank()) {
            mappedEntry.withField(StandardField.BOOKTITLE, entry.booktitle);
        }
        if ((entry.chapter != null) && !entry.chapter.isBlank()) {
            mappedEntry.withField(StandardField.CHAPTER, entry.chapter);
        }
        if ((entry.edition != null) && !entry.edition.isBlank()) {
            mappedEntry.withField(StandardField.EDITION, entry.edition);
        }
        if ((entry.editor != null) && !entry.editor.isBlank()) {
            mappedEntry.withField(StandardField.EDITOR, entry.editor);
        }
        if ((entry.howpublished != null) && !entry.howpublished.isBlank()) {
            mappedEntry.withField(StandardField.HOWPUBLISHED, entry.howpublished);
        }
        if ((entry.institution != null) && !entry.institution.isBlank()) {
            mappedEntry.withField(StandardField.INSTITUTION, entry.institution);
        }
        if ((entry.journal != null) && !entry.journal.isBlank()) {
            mappedEntry.withField(StandardField.JOURNAL, entry.journal);
        }
        if ((entry.month != null) && !entry.month.isBlank()) {
            mappedEntry.withField(StandardField.MONTH, entry.month);
        }
        if ((entry.note != null) && !entry.note.isBlank()) {
            mappedEntry.withField(StandardField.NOTE, entry.note);
        }
        if ((entry.number != null) && !entry.number.isBlank()) {
            mappedEntry.withField(StandardField.NUMBER, entry.number);
        }
        if ((entry.organization != null) && !entry.organization.isBlank()) {
            mappedEntry .withField(StandardField.ORGANIZATION, entry.organization);
        }
        if ((entry.pages != null) && !entry.pages.isBlank()) {
            mappedEntry.withField(StandardField.PAGES, entry.pages);
        }
        if ((entry.publisher != null) && !entry.publisher.isBlank()) {
            mappedEntry.withField(StandardField.PUBLISHER, entry.publisher);
        }
        if ((entry.school != null) && !entry.school.isBlank()) {
            mappedEntry.withField(StandardField.SCHOOL, entry.school);
        }
        if ((entry.series != null) && !entry.series.isBlank()) {
            mappedEntry.withField(StandardField.SERIES, entry.series);
        }
        if ((entry.title != null) && !entry.title.isBlank()) {
            mappedEntry.withField(StandardField.TITLE, entry.title);
        }
        if ((entry.volume != null) && !entry.volume.isBlank()) {
            mappedEntry.withField(StandardField.VOLUME, entry.volume);
        }
        if ((entry.year != null) && !entry.year.isBlank()) {
            mappedEntry.withField(StandardField.YEAR, entry.year);
        }
        if ((entry.date != null) && !entry.date.isBlank()) {
            mappedEntry.withField(StandardField.DATE, entry.date);
        }
        return mappedEntry;
    }

    private static EntryType getEntryType(String entryTypeAsString) {
        Optional<StandardEntryType> standardEntryType = Arrays.stream(StandardEntryType.values()).filter(entryType -> entryType.getName().equals(entryTypeAsString)).findFirst();
        if (standardEntryType.isPresent()) {
            return standardEntryType.get();
        }
        Optional<IEEETranEntryType> ieeeEntryType = Arrays.stream(IEEETranEntryType.values()).filter(entryType -> entryType.getName().equals(entryTypeAsString)).findFirst();
        if (ieeeEntryType.isPresent()) {
            return ieeeEntryType.get();
        }
        return new UnknownEntryType(entryTypeAsString);
    }
}
