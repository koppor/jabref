package org.jabref.gui.plaincitationparser;

import static org.jabref.gui.actions.ActionHelper.needsDatabase;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;

public class PlainCitationParserAction extends SimpleCommand {
    private final DialogService dialogService;

    public PlainCitationParserAction(DialogService dialogService, StateManager stateManager) {
        this.dialogService = dialogService;
        this.executable.bind(needsDatabase(stateManager));
    }

    @Override
    public void execute() {
        dialogService.showCustomDialogAndWait(new PlainCitationParserDialog());
    }
}
