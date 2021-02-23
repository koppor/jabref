package org.jabref.logic.sharelatex;

import java.util.List;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;

import org.jabref.gui.Globals;
import org.jabref.logic.exporter.SavePreferences;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.sharelatex.ShareLatexProject;
import org.jabref.model.util.FileUpdateMonitor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ShareLatexManager {

    private static final Log LOGGER = LogFactory.getLog(ShareLatexManager.class);

    private final SharelatexConnector connector = new SharelatexConnector();
    private final ShareLatexParser parser = new ShareLatexParser();
    private SharelatexConnectionProperties properties;
    private final ListProperty<ShareLatexProject> projectList = new SimpleListProperty<>();

    public ShareLatexManager() {
        // Todo, probably shouldn't be bi-directional
        projectList.bind(connector.projectListProperty());
    }

    public void loginWebEngineToServer(String server, String username, String password) {
        connector.connectWebEngineToServer(server, username, password);
    }

    public List<ShareLatexProject> getWebEngineProjects() {
        return projectList.get();
    }

    public void startWebSocketHandler(String projectID, BibDatabaseContext database, ImportFormatPreferences preferences, FileUpdateMonitor fileMonitor) {
        connector.openWebEngineProject(projectID, database, preferences, fileMonitor);
        registerListener(ShareLatexManager.this); // Todo, figure out what should happen here
    }

    /**
     * pushes the database content to overleaf
     *
     * @param bibDatabaseContext the context of the database to send
     */
    public void sendNewDatabaseContent(BibDatabaseContext bibDatabaseContext) {
        try {
            SavePreferences prefs = Globals.prefs.getSavePreferences();
            // TODO FIXME important
            /*
            AtomicFileWriter fileWriter = new AtomicFileWriter(Paths.get(""), savePreferences.getEncoding());

            StringWriter strWriter = new StringWriter();
            BibtexDatabaseWriter stringdbWriter = new BibtexDatabaseWriter(strWriter, savePreferences, Globals.entryTypesManager)

                fileWriter.saveDatabase, savePreferences);

              stringdbWriter.saveDatabase(database);
            String updatedcontent = saveSession.getStringValue().replace("\r\n", "\n");
            */
            connector.sendNewDatabaseContent("");
        } catch (InterruptedException e) {
            LOGGER.error("Could not prepare database for saving ", e);
        }
    }

    public void registerListener(Object listener) {
        connector.registerListener(listener);
    }

    public void unregisterListener(Object listener) {
        connector.unregisterListener(listener);
    }

    public void disconnectAndCloseConnection() {
        connector.disconnectAndCloseConn();
    }

    public void setConnectionProperties(SharelatexConnectionProperties props) {
        this.properties = props;
    }

    public SharelatexConnectionProperties getConnectionProperties() {
        return this.properties;
    }

    public ListProperty<ShareLatexProject> projectListProperty() {
        return projectList;
    }
}
