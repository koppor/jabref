package org.jabref.gui.specialfields;

import de.saxsys.mvvmfx.utils.commands.Command;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.jabref.gui.DialogService;
import org.jabref.gui.JabRefFrame;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.ActionFactory;
import org.jabref.model.entry.field.SpecialField;
import org.jabref.model.entry.field.SpecialFieldValue;
import org.jabref.preferences.PreferencesService;

import javax.swing.undo.UndoManager;
import java.util.function.Function;

public class SpecialFieldMenuItemFactory {
    public static MenuItem getSpecialFieldSingleItem(
            SpecialField field,
            ActionFactory factory,
            JabRefFrame frame,
            DialogService dialogService,
            PreferencesService preferencesService,
            UndoManager undoManager,
            StateManager stateManager) {
        SpecialFieldValueViewModel specialField =
                new SpecialFieldValueViewModel(field.getValues().get(0));
        MenuItem menuItem = factory.createMenuItem(
                specialField.getAction(),
                new SpecialFieldViewModel(field, preferencesService, undoManager)
                        .getSpecialFieldAction(field.getValues().get(0), frame, dialogService, stateManager));
        menuItem.visibleProperty()
                .bind(preferencesService.getSpecialFieldsPreferences().specialFieldsEnabledProperty());
        return menuItem;
    }

    public static Menu createSpecialFieldMenu(
            SpecialField field,
            ActionFactory factory,
            JabRefFrame frame,
            DialogService dialogService,
            PreferencesService preferencesService,
            UndoManager undoManager,
            StateManager stateManager) {

        return createSpecialFieldMenu(
                field, factory, preferencesService, undoManager, specialField -> new SpecialFieldViewModel(
                                field, preferencesService, undoManager)
                        .getSpecialFieldAction(specialField.getValue(), frame, dialogService, stateManager));
    }

    public static Menu createSpecialFieldMenu(
            SpecialField field,
            ActionFactory factory,
            PreferencesService preferencesService,
            UndoManager undoManager,
            Function<SpecialFieldValueViewModel, Command> commandFactory) {
        SpecialFieldViewModel viewModel = new SpecialFieldViewModel(field, preferencesService, undoManager);
        Menu menu = factory.createMenu(viewModel.getAction());

        for (SpecialFieldValue Value : field.getValues()) {
            SpecialFieldValueViewModel valueViewModel = new SpecialFieldValueViewModel(Value);
            menu.getItems()
                    .add(factory.createMenuItem(valueViewModel.getAction(), commandFactory.apply(valueViewModel)));
        }

        menu.visibleProperty()
                .bind(preferencesService.getSpecialFieldsPreferences().specialFieldsEnabledProperty());
        return menu;
    }
}
