package org.jabref.gui.backup;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;

import org.controlsfx.control.HyperlinkLabel;
import org.jabref.gui.FXDialog;
import org.jabref.gui.desktop.os.NativeDesktop;
import org.jabref.gui.frame.ExternalApplicationsPreferences;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.BackupFileType;
import org.jabref.logic.util.io.BackupFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class BackupResolverDialog extends FXDialog {
    public static final ButtonType RESTORE_FROM_BACKUP =
            new ButtonType(Localization.lang("Restore from backup"), ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType REVIEW_BACKUP =
            new ButtonType(Localization.lang("Review backup"), ButtonBar.ButtonData.LEFT);
    public static final ButtonType IGNORE_BACKUP =
            new ButtonType(Localization.lang("Ignore backup"), ButtonBar.ButtonData.CANCEL_CLOSE);

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupResolverDialog.class);

    public BackupResolverDialog(
            Path originalPath,
            Path backupDir,
            ExternalApplicationsPreferences externalApplicationsPreferences) {
        super(AlertType.CONFIRMATION, Localization.lang("Backup found"), true);
        setHeaderText(null);
        getDialogPane().setMinHeight(180);
        getDialogPane().getButtonTypes().setAll(RESTORE_FROM_BACKUP, REVIEW_BACKUP, IGNORE_BACKUP);

        Optional<Path> backupPathOpt =
                BackupFileUtil.getPathOfLatestExistingBackupFile(
                        originalPath, BackupFileType.BACKUP, backupDir);
        String backupFilename =
                backupPathOpt
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .orElse(Localization.lang("File not found"));
        String content =
                Localization.lang(
                                "A backup file for '%0' was found at [%1]",
                                originalPath.getFileName().toString(), backupFilename)
                        + "\n"
                        + Localization.lang(
                                "This could indicate that JabRef did not shut down cleanly last time the file was used.")
                        + "\n\n"
                        + Localization.lang(
                                "Do you want to recover the library from the backup file?");
        setContentText(content);

        HyperlinkLabel contentLabel = new HyperlinkLabel(content);
        contentLabel.setPrefWidth(360);
        contentLabel.setOnAction(
                e -> {
                    if (backupPathOpt.isPresent()) {
                        if (!(e.getSource() instanceof Hyperlink)) {
                            return;
                        }
                        String clickedLinkText = ((Hyperlink) (e.getSource())).getText();
                        if (backupFilename.equals(clickedLinkText)) {
                            try {
                                NativeDesktop.openFolderAndSelectFile(
                                        backupPathOpt.get(), externalApplicationsPreferences, null);
                            } catch (IOException ex) {
                                LOGGER.error("Could not open backup folder", ex);
                            }
                        }
                    }
                });
        getDialogPane().setContent(contentLabel);
    }
}
