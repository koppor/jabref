package org.jabref.gui.entryeditor;

import static org.jabref.gui.actions.ActionHelper.needsDatabase;

import org.jabref.gui.LibraryTab;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;

import java.util.function.Supplier;

public class PreviewSwitchAction extends SimpleCommand {

    public enum Direction {
        PREVIOUS,
        NEXT
    }

    private final Supplier<LibraryTab> tabSupplier;
    private final Direction direction;

    public PreviewSwitchAction(
            Direction direction, Supplier<LibraryTab> tabSupplier, StateManager stateManager) {
        this.tabSupplier = tabSupplier;
        this.direction = direction;

        this.executable.bind(needsDatabase(stateManager));
    }

    @Override
    public void execute() {
        if (direction == Direction.NEXT) {
            tabSupplier.get().getEntryEditor().nextPreviewStyle();
        } else {
            tabSupplier.get().getEntryEditor().previousPreviewStyle();
        }
    }
}
