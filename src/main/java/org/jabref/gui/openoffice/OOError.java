package org.jabref.gui.openoffice;

import org.jabref.gui.DialogService;
import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.openoffice.ConnectionLostException;
import org.jabref.logic.openoffice.NoDocumentException;
import org.jabref.logic.openoffice.NoDocumentFoundException;

class OOError extends JabRefException {

    private String localizedTitle;

    public OOError(String title, String localizedMessage) {
        super(localizedMessage, localizedMessage);
        this.localizedTitle = title;
    }

    public OOError(String title, String localizedMessage, Throwable cause) {
        super(localizedMessage, localizedMessage, cause);
        this.localizedTitle = title;
    }

    public String getTitle() {
        return localizedTitle;
    }

    public OOError setTitle(String title) {
        localizedTitle = title;
        return this;
    }

    public void showErrorDialog(DialogService dialogService) {
        dialogService.showErrorDialogAndWait(getTitle(), getLocalizedMessage());
    }

    public static OOError fromMisc(Exception ex) {
        return new OOError(
            "Exception",
            ex.getMessage(),
            ex);
    }

    // For JabRefException
    public static OOError from(JabRefException ex) {
        return new OOError(
            Localization.lang("JabRefException"),
            ex.getLocalizedMessage(),
            ex);
    }

    public static OOError fromJabRefException(JabRefException ex) {
        return from(ex);
    }

    // For ConnectionLostException
    public static OOError from(ConnectionLostException ex) {
        return new OOError(
            Localization.lang("Connection lost"),
            Localization.lang("Connection to OpenOffice/LibreOffice has been lost."
                              + " Please make sure OpenOffice/LibreOffice is running,"
                              + " and try to reconnect."),
            ex);
    }

    public static void
    showConnectionLostErrorMessage(DialogService dialogService, ConnectionLostException ex) {
        OOError.from(ex).showErrorDialog(dialogService);
    }

    // For NoDocumentException
    public static OOError from(NoDocumentException ex) {
        return new OOError(
            Localization.lang("Not connected to document"),
            Localization.lang("Not connected to any Writer document."
                              + " Please make sure a document is open,"
                              + " and use the 'Select Writer document' button"
                              + " to connect to it."),
            ex);
    }

    // For NoDocumentFoundException
    public static OOError from(NoDocumentFoundException ex) {
        return new OOError(
            Localization.lang("No Writer documents found"),
            Localization.lang("Could not connect to any Writer document."
                              + " Please make sure a document is open"
                              + " before using the 'Select Writer document' button"
                              + " to connect to it."),
            ex);
    }

    // noDataBaseIsOpenForCiting
    public static OOError noDataBaseIsOpenForCiting() {
        return new OOError(
            Localization.lang("No database"),
            Localization.lang("No bibliography database is open for citation.")
            + "\n"
            + Localization.lang("Open one before citing."));
    }

    // noDataBaseIsOpenForExport
    public static OOError noDataBaseIsOpenForExport() {
        return new OOError(
            Localization.lang("No database is open"),
            Localization.lang("We need a database to export from. Open one."));
    }

    // noDataBaseIsOpenForExport
    public static OOError noDataBaseIsOpen() {
        return new OOError(
            Localization.lang("No database is open"),
            Localization.lang("This operation requires a bibliography database."));
    }

    // noValidStyleSelected
    public static OOError noValidStyleSelected() {
        return new OOError(Localization.lang("No valid style file defined"),
                           Localization.lang("No bibliography style is selected for citation.")
                           + "\n"
                           + Localization.lang("Select one before citing.")
                           + "\n"
                           + Localization.lang("You must select either a valid style file,"
                                               + " or use one of the default styles."));
    }

    // noEntriesSelectedForCitation
    public static OOError noEntriesSelectedForCitation() {
        return new OOError(Localization.lang("No entries selected for citation"),
                           Localization.lang("No bibliography entries are selected for citation.")
                           + "\n"
                           + Localization.lang("Select some before citing."));
    }
}
