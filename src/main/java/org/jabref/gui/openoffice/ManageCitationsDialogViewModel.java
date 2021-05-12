package org.jabref.gui.openoffice;

import java.util.List;
import java.util.stream.Collectors;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

import org.jabref.gui.DialogService;
import org.jabref.model.openoffice.CitationEntry;

public class ManageCitationsDialogViewModel {

    private final ListProperty<CitationEntryViewModel> citations = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final OOBibBase ooBase;
    private final DialogService dialogService;

    public ManageCitationsDialogViewModel(OOBibBase ooBase,
                                          List<CitationEntry> citationEntries,
                                          DialogService dialogService) {
        this.ooBase = ooBase;
        this.dialogService = dialogService;

        for (CitationEntry entry : citationEntries) {
            CitationEntryViewModel itemViewModelEntry = new CitationEntryViewModel(entry);
            citations.add(itemViewModelEntry);
        }
    }

    public void storeSettings() {
        List<CitationEntry> ciationEntries = citations.stream().map(CitationEntryViewModel::toCitationEntry).collect(Collectors.toList());
        ooBase.applyCitationEntries(ciationEntries);
    }

    public ListProperty<CitationEntryViewModel> citationsProperty() {
        return citations;
    }
}

