package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jabref.gui.Globals;
import org.jabref.gui.sharelatex.ShareLatexLoginDialogView;
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
    private Optional<List<ShareLatexProject>> projectList;

    public ShareLatexManager() {
    }

    public void loginWebEngineToServer(String server, String username, String password, ShareLatexLoginDialogView dialogView) {
        connector.connectWebEngineToServer(server, username, password, dialogView, this::setProjects);
    }

    public List<ShareLatexProject> getWebEngineProjects() {
        return projectList.orElse(Collections.emptyList());
    }

    public void setProjects(List<ShareLatexProject> projects) {
        projectList = Optional.ofNullable(projects);
    }

    public List<ShareLatexProject> getProjects() throws IOException {
        if (connector.getProjects().isPresent()) {
            return parser.getProjectFromJson(connector.getProjects().get());
        }
        return Collections.emptyList();
    }

    public void startWebSocketHandler(String projectID, BibDatabaseContext database, ImportFormatPreferences preferences, FileUpdateMonitor fileMonitor) {
        //JabRefExecutorService.INSTANCE.executeAndWait(() -> {

            //try {
                connector.openWebEngineProject(projectID, database, preferences, fileMonitor);
                // connector.startSocketIOListener(projectID, database, preferences, fileMonitor);
                // connector.startWebsocketListener(projectID, database, preferences, fileMonitor);
            //} catch (URISyntaxException e) {
            //    LOGGER.error(e);
            //}
            registerListener(ShareLatexManager.this);

        //});
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
}
