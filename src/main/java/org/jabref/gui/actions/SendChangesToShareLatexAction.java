package org.jabref.gui.actions;

import org.jabref.gui.Globals;
import org.jabref.gui.StateManager;
import org.jabref.logic.sharelatex.ShareLatexManager;

public class SendChangesToShareLatexAction extends SimpleCommand {

    @Override
    public void execute() {
        ShareLatexManager manager = Globals.shareLatexManager;
        StateManager stateManager = Globals.stateManager;
        manager.sendNewDatabaseContent(stateManager.getActiveDatabase().get());
    }
}
