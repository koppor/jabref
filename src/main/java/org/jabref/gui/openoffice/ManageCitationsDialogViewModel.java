package org.jabref.gui.openoffice;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;

import org.jabref.gui.DialogService;
import org.jabref.model.openoffice.CitationEntry;

public class ManageCitationsDialogViewModel {

    SimpleObjectProperty<Optional<List<CitationEntry>>> citationEntries;

    private final ListProperty<CitationEntryViewModel> citations = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final OOBibBase ooBase;
    private final DialogService dialogService;

    public ManageCitationsDialogViewModel(OOBibBase ooBase, DialogService dialogService) {
        this.ooBase = ooBase;
        this.dialogService = dialogService;

        this.citationEntries = new SimpleObjectProperty<>();
        this.citationEntries.setValue(ooBase.guiActionGetCitationEntries());
        if (citationEntries.getValue().isEmpty()) {
            return;
        }

        for (CitationEntry entry : citationEntries.getValue().get()) {
            CitationEntryViewModel itemViewModelEntry = new CitationEntryViewModel(entry);
            citations.add(itemViewModelEntry);
        }
    }

    public void storeSettings() {
        List<CitationEntry> ciationEntries = citations.stream().map(CitationEntryViewModel::toCitationEntry).collect(Collectors.toList());
        ooBase.guiActionApplyCitationEntries(ciationEntries);
    }

    public ListProperty<CitationEntryViewModel> citationsProperty() {
        return citations;
    }
}

