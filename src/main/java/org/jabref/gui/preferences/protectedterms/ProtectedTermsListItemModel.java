package org.jabref.gui.preferences.protectedterms;

import javafx.beans.property.*;
import org.jabref.logic.protectedterms.ProtectedTermsList;

public class ProtectedTermsListItemModel {

    private final ProtectedTermsList termsList;
    private final BooleanProperty enabledProperty = new SimpleBooleanProperty();

    public ProtectedTermsListItemModel(ProtectedTermsList termsList) {
        this.termsList = termsList;
        this.enabledProperty.setValue(termsList.isEnabled());
    }

    public ProtectedTermsList getTermsList() {
        termsList.setEnabled(enabledProperty.getValue());
        return termsList;
    }

    public ReadOnlyStringProperty descriptionProperty() {
        return new ReadOnlyStringWrapper(termsList.getDescription());
    }

    public ReadOnlyStringProperty locationProperty() {
        return new ReadOnlyStringWrapper(termsList.getLocation());
    }

    public ReadOnlyBooleanProperty internalProperty() {
        return new ReadOnlyBooleanWrapper(termsList.isInternalList());
    }

    public BooleanProperty enabledProperty() {
        return enabledProperty;
    }
}
