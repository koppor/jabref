package org.jabref.gui.openoffice;

import java.util.List;
import java.util.stream.Collectors;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

import org.jabref.gui.DialogService;
import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.openoffice.CreationException;
import org.jabref.logic.openoffice.NoDocumentException;
import org.jabref.model.openoffice.CitationEntry;

import com.sun.star.beans.IllegalTypeException;
import com.sun.star.beans.NotRemoveableException;
import com.sun.star.beans.PropertyExistException;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.WrappedTargetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManageCitationsDialogViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManageCitationsDialogViewModel.class);

    private final ListProperty<CitationEntryViewModel> citations = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final OOBibBase ooBase;
    private final DialogService dialogService;

    public ManageCitationsDialogViewModel(OOBibBase ooBase, DialogService dialogService) {
        this.ooBase = ooBase;
        this.dialogService = dialogService;

        try {
            List<CitationEntry> cts = ooBase.getCitationEntries();
            for (CitationEntry entry : cts) {
                CitationEntryViewModel itemViewModelEntry = new CitationEntryViewModel(entry);
                citations.add(itemViewModelEntry);
            }
        } catch (UnknownPropertyException
                 | WrappedTargetException
                 | NoDocumentException
                 | PropertyVetoException
                 | JabRefException
                 | CreationException ex) {
            LOGGER.warn("Problem collecting citations", ex);
            dialogService.showErrorDialogAndWait(Localization.lang("Problem collecting citations"), ex);
        }
    }

    public void storeSettings() {
        List<CitationEntry> ciationEntries = citations.stream().map(CitationEntryViewModel::toCitationEntry).collect(Collectors.toList());
        try {
            ooBase.applyCitationEntries(ciationEntries);
        } catch (UnknownPropertyException | NotRemoveableException | PropertyExistException | IllegalTypeException |
                 NoDocumentException | WrappedTargetException |
                IllegalArgumentException ex) {
            LOGGER.warn("Problem modifying citation", ex);
            dialogService.showErrorDialogAndWait(Localization.lang("Problem modifying citation"), ex);
        }
    }

    public ListProperty<CitationEntryViewModel> citationsProperty() {
        return citations;
    }
}

