package org.jabref.gui.importer;

import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.VBox;

import org.jabref.gui.util.BaseDialog;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntryType;

import com.airhacks.afterburner.views.ViewLoader;
import jakarta.inject.Inject;
import org.controlsfx.control.CheckListView;

public class ImportCustomEntryTypesDialog extends BaseDialog<Void> {

    private final List<BibEntryType> customEntryTypes;

    @Inject private CliPreferences preferences;
    @FXML private VBox boxDifferentCustomization;

    @FXML private CheckListView<BibEntryType> unknownEntryTypesCheckList;
    @FXML private CheckListView<BibEntryTypePrefsAndFileViewModel> differentCustomizationCheckList;

    private final BibDatabaseMode mode;
    private ImportCustomEntryTypesDialogViewModel viewModel;

    public ImportCustomEntryTypesDialog(BibDatabaseMode mode, List<BibEntryType> customEntryTypes) {
        this.mode = mode;
        this.customEntryTypes = customEntryTypes;

        ViewLoader.view(this)
                  .load()
                  .setAsDialogPane(this);

        setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                viewModel.importBibEntryTypes(
                        unknownEntryTypesCheckList.getCheckModel().getCheckedItems(),
                        differentCustomizationCheckList.getCheckModel().getCheckedItems().stream()
                                .map(BibEntryTypePrefsAndFileViewModel::customTypeFromPreferences)
                                .toList());
            }
            return null;
        });

        setTitle(Localization.lang("Custom entry types"));
    }

    @FXML
    public void initialize() {
        viewModel = new ImportCustomEntryTypesDialogViewModel(mode, customEntryTypes, preferences);
        boxDifferentCustomization.visibleProperty().bind(Bindings.isNotEmpty(viewModel.differentCustomizations()));
        boxDifferentCustomization.managedProperty().bind(Bindings.isNotEmpty(viewModel.differentCustomizations()));
        unknownEntryTypesCheckList.setItems(viewModel.newTypes());
        unknownEntryTypesCheckList.setCellFactory(listView -> new CheckBoxListCell<>(unknownEntryTypesCheckList::getItemBooleanProperty) {
            @Override
            public void updateItem(BibEntryType bibEntryType, boolean empty) {
                super.updateItem(bibEntryType, empty);
                setText(bibEntryType == null ? "" : bibEntryType.getType().getDisplayName());
            }
        });
        differentCustomizationCheckList.setItems(viewModel.differentCustomizations());
    }
}
