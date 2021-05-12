package org.jabref.gui.openoffice;

import java.util.List;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.Text;

import org.jabref.gui.DialogService;
import org.jabref.gui.util.BaseDialog;
import org.jabref.gui.util.ValueTableCellFactory;
import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.openoffice.CitationEntry;
import org.jabref.model.strings.StringUtil;

import com.airhacks.afterburner.views.ViewLoader;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;

public class ManageCitationsDialogView extends BaseDialog<Void> {

    private static final String HTML_BOLD_END_TAG = "</b>";
    private static final String HTML_BOLD_START_TAG = "<b>";

    private final OOBibBase ooBase;
    private final List<CitationEntry> citations;

    @FXML private TableView<CitationEntryViewModel> citationsTableView;
    @FXML private TableColumn<CitationEntryViewModel, String> citation;
    @FXML private TableColumn<CitationEntryViewModel, String> extraInfo;

    @Inject private DialogService dialogService;

    private ManageCitationsDialogViewModel viewModel;

    public ManageCitationsDialogView(OOBibBase ooBase, List<CitationEntry> citations) {
        this.ooBase = ooBase;
        this.citations = citations;

        ViewLoader.view(this)
                  .load()
                  .setAsDialogPane(this);

        setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                viewModel.storeSettings();
            }
            return null;
        });

        setTitle(Localization.lang("Manage citations"));
    }

    @FXML
    private void initialize() throws NoSuchElementException, WrappedTargetException, UnknownPropertyException, JabRefException {

        viewModel = new ManageCitationsDialogViewModel(ooBase, citations, dialogService);

        citation.setCellValueFactory(cellData -> cellData.getValue().citationProperty());
        new ValueTableCellFactory<CitationEntryViewModel, String>().withGraphic(this::getText).install(citation);

        extraInfo.setCellValueFactory(cellData -> cellData.getValue().extraInformationProperty());
        extraInfo.setEditable(true);

        citationsTableView.setEditable(true);

        citationsTableView.itemsProperty().bindBidirectional(viewModel.citationsProperty());

        extraInfo.setOnEditCommit((CellEditEvent<CitationEntryViewModel, String> cell) -> {
            cell.getRowValue().setExtraInfo(cell.getNewValue());
        });
        extraInfo.setCellFactory(TextFieldTableCell.forTableColumn());
    }

    private Node getText(String citationContext) {

        String inBetween = StringUtil.substringBetween(citationContext, HTML_BOLD_START_TAG, HTML_BOLD_END_TAG);
        String start = citationContext.substring(0, citationContext.indexOf(HTML_BOLD_START_TAG));
        String end = citationContext.substring(citationContext.lastIndexOf(HTML_BOLD_END_TAG) + HTML_BOLD_END_TAG.length());

        Text startText = new Text(start);
        Text inBetweenText = new Text(inBetween);
        inBetweenText.setStyle("-fx-font-weight: bold");
        Text endText = new Text(end);

        FlowPane flow = new FlowPane(startText, inBetweenText, endText);
        return flow;
    }
}
