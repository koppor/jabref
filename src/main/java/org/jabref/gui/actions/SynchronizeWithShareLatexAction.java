package org.jabref.gui.actions;

import org.jabref.gui.sharelatex.ShareLatexLoginDialogView;

public class SynchronizeWithShareLatexAction extends SimpleCommand {

    @Override
    public void execute() {
        new ShareLatexLoginDialogView().showAndWait();
    }
}
