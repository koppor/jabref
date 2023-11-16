package org.jabref.gui.fieldeditors;

import javafx.util.StringConverter;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.jabref.gui.autocompleter.*;
import org.jabref.logic.integrity.FieldCheckers;
import org.jabref.model.entry.Author;
import org.jabref.model.entry.field.Field;

import javax.swing.undo.UndoManager;
import java.util.Collection;

public class PersonsEditorViewModel extends AbstractEditorViewModel {

    private final AutoCompletePreferences preferences;

    public PersonsEditorViewModel(
            Field field,
            SuggestionProvider<?> suggestionProvider,
            AutoCompletePreferences preferences,
            FieldCheckers fieldCheckers,
            UndoManager undoManager) {
        super(field, suggestionProvider, fieldCheckers, undoManager);
        this.preferences = preferences;
    }

    public StringConverter<Author> getAutoCompletionConverter() {
        return new PersonNameStringConverter(preferences);
    }

    @SuppressWarnings("unchecked")
    public Collection<Author> complete(AutoCompletionBinding.ISuggestionRequest request) {
        return (Collection<Author>) super.complete(request);
    }

    public AutoCompletionStrategy getAutoCompletionStrategy() {
        return new AppendPersonNamesStrategy();
    }
}
