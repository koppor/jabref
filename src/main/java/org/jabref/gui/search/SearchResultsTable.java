package org.jabref.gui.search;

import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.maintable.*;
import org.jabref.gui.maintable.columns.LibraryColumn;
import org.jabref.gui.maintable.columns.MainTableColumn;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.preferences.PreferencesService;

import javax.swing.undo.UndoManager;
import java.util.List;

public class SearchResultsTable extends TableView<BibEntryTableViewModel> {

    public SearchResultsTable(
            SearchResultsTableDataModel model,
            BibDatabaseContext database,
            PreferencesService preferencesService,
            UndoManager undoManager,
            DialogService dialogService,
            StateManager stateManager,
            TaskExecutor taskExecutor) {
        super();

        MainTablePreferences mainTablePreferences = preferencesService.getMainTablePreferences();

        List<TableColumn<BibEntryTableViewModel, ?>> allCols = new MainTableColumnFactory(
                        database,
                        preferencesService,
                        preferencesService.getSearchDialogColumnPreferences(),
                        undoManager,
                        dialogService,
                        stateManager,
                        taskExecutor)
                .createColumns();

        if (allCols.stream().noneMatch(LibraryColumn.class::isInstance)) {
            allCols.add(0, new LibraryColumn());
        }
        this.getColumns().addAll(allCols);

        this.getSortOrder().clear();
        preferencesService
                .getSearchDialogColumnPreferences()
                .getColumnSortOrder()
                .forEach(columnModel -> this.getColumns().stream()
                        .map(column -> (MainTableColumn<?>) column)
                        .filter(column -> column.getModel().equals(columnModel))
                        .findFirst()
                        .ifPresent(column -> this.getSortOrder().add(column)));

        if (mainTablePreferences.getResizeColumnsToFit()) {
            this.setColumnResizePolicy(new SmartConstrainedResizePolicy());
        }

        this.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        this.setItems(model.getEntriesFilteredAndSorted());
        // Enable sorting
        model.getEntriesFilteredAndSorted().comparatorProperty().bind(this.comparatorProperty());

        this.getStylesheets().add(MainTable.class.getResource("MainTable.css").toExternalForm());

        // Store visual state
        new PersistenceVisualStateTable(this, preferencesService.getSearchDialogColumnPreferences()).addListeners();

        database.getDatabase().registerListener(this);
    }
}
