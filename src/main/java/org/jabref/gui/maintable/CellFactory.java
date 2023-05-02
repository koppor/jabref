package org.jabref.gui.maintable;

import java.util.HashMap;
import java.util.Map;

import javax.swing.undo.UndoManager;

import javafx.scene.Node;

import org.jabref.gui.externalfiletype.ExternalFileType;
import org.jabref.gui.icon.IconTheme;
import org.jabref.gui.icon.JabRefIcon;
import org.jabref.gui.specialfields.SpecialFieldViewModel;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.SpecialField;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.field.UnknownField;
import org.jabref.preferences.PreferencesService;

public class CellFactory {

    private final Map<Field, JabRefIcon> tableIcons = new HashMap<>();

    public CellFactory(PreferencesService preferencesService, UndoManager undoManager) {
        JabRefIcon icon;
        icon = IconTheme.JabRefIcons.PDF_FILE;
        // icon.setToo(Localization.lang("Open") + " PDF");
        tableIcons.put(StandardField.PDF, icon);

        icon = IconTheme.JabRefIcons.WWW;
        // icon.setToolTipText(Localization.lang("Open") + " URL");
        tableIcons.put(StandardField.URL, icon);

        icon = IconTheme.JabRefIcons.WWW;
        // icon.setToolTipText(Localization.lang("Open") + " CiteSeer URL");
        tableIcons.put(new UnknownField("citeseerurl"), icon);

        icon = IconTheme.JabRefIcons.WWW;
        // icon.setToolTipText(Localization.lang("Open") + " ArXivFetcher URL");
        tableIcons.put(StandardField.EPRINT, icon);

        icon = IconTheme.JabRefIcons.DOI;
        // icon.setToolTipText(Localization.lang("Open") + " DOI " + Localization.lang("web link"));
        tableIcons.put(StandardField.DOI, icon);

        icon = IconTheme.JabRefIcons.FILE;
        // icon.setToolTipText(Localization.lang("Open") + " PS");
        tableIcons.put(StandardField.PS, icon);

        icon = IconTheme.JabRefIcons.FOLDER;
        // icon.setToolTipText(Localization.lang("Open folder"));
        tableIcons.put(StandardField.FOLDER, icon);

        icon = IconTheme.JabRefIcons.FILE;
        // icon.setToolTipText(Localization.lang("Open file"));
        tableIcons.put(StandardField.FILE, icon);

        for (ExternalFileType fileType : preferencesService.getFilePreferences().getExternalFileTypes()) {
            icon = fileType.getIcon();
            // icon.setToolTipText(Localization.lang("Open %0 file", fileType.getName()));
            tableIcons.put(fileType.getField(), icon);
        }

        SpecialFieldViewModel relevanceViewModel = new SpecialFieldViewModel(SpecialField.RELEVANCE, preferencesService, undoManager);
        icon = relevanceViewModel.getIcon();
        // icon.setToolTipText(relevanceViewModel.getLocalization());
        tableIcons.put(SpecialField.RELEVANCE, icon);

        SpecialFieldViewModel qualityViewModel = new SpecialFieldViewModel(SpecialField.QUALITY, preferencesService, undoManager);
        icon = qualityViewModel.getIcon();
        // icon.setToolTipText(qualityViewModel.getLocalization());
        tableIcons.put(SpecialField.QUALITY, icon);

        // Ranking item in the menu uses one star
        SpecialFieldViewModel rankViewModel = new SpecialFieldViewModel(SpecialField.RANKING, preferencesService, undoManager);
        icon = rankViewModel.getIcon();
        // icon.setToolTipText(rankViewModel.getLocalization());
        tableIcons.put(SpecialField.RANKING, icon);

        // Priority icon used for the menu
        SpecialFieldViewModel priorityViewModel = new SpecialFieldViewModel(SpecialField.PRIORITY, preferencesService, undoManager);
        icon = priorityViewModel.getIcon();
        // icon.setToolTipText(priorityViewModel.getLocalization());
        tableIcons.put(SpecialField.PRIORITY, icon);

        // Read icon used for menu
        SpecialFieldViewModel readViewModel = new SpecialFieldViewModel(SpecialField.READ_STATUS, preferencesService, undoManager);
        icon = readViewModel.getIcon();
        // icon.setToolTipText(readViewModel.getLocalization());
        tableIcons.put(SpecialField.READ_STATUS, icon);

        // Print icon used for menu
        SpecialFieldViewModel printedViewModel = new SpecialFieldViewModel(SpecialField.PRINTED, preferencesService, undoManager);
        icon = printedViewModel.getIcon();
        // icon.setToolTipText(printedViewModel.getLocalization());
        tableIcons.put(SpecialField.PRINTED, icon);
    }

    public Node getTableIcon(Field field) {
        JabRefIcon icon = tableIcons.get(field);
        if (icon == null) {
            // LOGGER.info("Error: no table icon defined for type '" + field + "'.");
            return null;
        } else {
            // node should be generated for each call, as nodes can be added to the scene graph only once
            return icon.getGraphicNode();
        }
    }
}
