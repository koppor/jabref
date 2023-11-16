package org.jabref.gui.preferences.customentrytypes;

import org.jabref.model.entry.BibEntryType;
import org.jabref.model.entry.field.Field;

import java.util.function.Predicate;

public class CustomEntryTypeViewModel extends EntryTypeViewModel {

    public CustomEntryTypeViewModel(BibEntryType entryType, Predicate<Field> isMultiline) {
        super(entryType, isMultiline);
    }
}
