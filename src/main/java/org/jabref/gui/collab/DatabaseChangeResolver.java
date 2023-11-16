package org.jabref.gui.collab;

import org.jabref.gui.DialogService;
import org.jabref.gui.collab.entrychange.EntryChangeResolver;

import java.util.Optional;

public abstract sealed class DatabaseChangeResolver permits EntryChangeResolver {
    protected final DialogService dialogService;

    protected DatabaseChangeResolver(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    public abstract Optional<DatabaseChange> askUserToResolveChange();
}
