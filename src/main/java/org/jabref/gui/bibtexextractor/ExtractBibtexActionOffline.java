package org.jabref.gui.bibtexextractor;

import static org.jabref.gui.actions.ActionHelper.needsDatabase;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;

public class ExtractBibtexActionOffline extends SimpleCommand {

    private final DialogService dialogService;

    public ExtractBibtexActionOffline(DialogService dialogService, StateManager stateManager) {
        this.dialogService = dialogService;
        this.executable.bind(needsDatabase(stateManager));
    }

    @Override
    public void execute() {
        dialogService.showCustomDialogAndWait(new ExtractBibtexDialog(false));
    }
}
