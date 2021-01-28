package org.jabref.gui.sharelatex;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import org.jabref.gui.DialogService;
import org.jabref.gui.FXDialog;
import org.jabref.gui.Globals;
import org.jabref.gui.util.BaseDialog;
import org.jabref.gui.util.ControlHelper;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.sharelatex.ShareLatexManager;
import org.jabref.logic.sharelatex.SharelatexConnectionProperties;

import com.airhacks.afterburner.views.ViewLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShareLatexLoginDialogView extends BaseDialog<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareLatexLoginDialogView.class);

    @FXML private TextField tbAddress;
    @FXML private TextField tbUsername;
    @FXML private PasswordField tbPassword;
    @FXML private ButtonType connectButton;

    private final Button btnLogin;
    @Inject private ShareLatexManager manager;
    @Inject private DialogService dialogService;

    private SharelatexConnectionProperties props;

    public ShareLatexLoginDialogView() {
        ViewLoader.view(this)
                  .load()
                  .setAsDialogPane(this);

        ControlHelper.setAction(connectButton, this.getDialogPane(), event -> signIn());
        btnLogin = (Button) this.getDialogPane().lookupButton(connectButton);

    }

    @FXML
    private void initialize() {
    }

    @FXML
    private void signIn() {
        btnLogin.setText(Localization.lang("Logging in..."));
        try {
            String result = manager.login(tbAddress.getText(), tbUsername.getText(), tbPassword.getText());
            if (result.contains("incorrect")) {
                FXDialog dlg = new FXDialog(AlertType.ERROR);
                dlg.setContentText("Your email or password is incorrect. Please try again");
                dlg.showAndWait();
            } else {
                // TODO: Replace with PreferencesService later

                props = new SharelatexConnectionProperties(Globals.prefs.getShareLatexPreferences());

                props.setUrl(tbAddress.getText());
                props.setUser(tbUsername.getText());
                props.setPassword(tbPassword.getText());

                manager.setConnectionProperties(props);

                ShareLatexProjectDialogView dlgprojects = new ShareLatexProjectDialogView();
                dlgprojects.show();
                this.close();

            }
        } catch (Exception e) {
            LOGGER.error("Problems connectiong", e);
            dialogService.showErrorDialogAndWait(e);
        }

    }

}
