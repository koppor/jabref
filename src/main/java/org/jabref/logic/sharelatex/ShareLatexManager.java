package org.jabref.logic.sharelatex;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import org.jabref.gui.Globals;
import org.jabref.gui.JabRefExecutorService;
import org.jabref.logic.exporter.BibtexDatabaseWriter;
import org.jabref.logic.exporter.SavePreferences;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.sharelatex.ShareLatexProject;
import org.jabref.model.util.FileUpdateMonitor;
import org.jabref.preferences.GeneralPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShareLatexManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareLatexManager.class);

    private final SharelatexConnector connector = new SharelatexConnector();
    private final ShareLatexParser parser = new ShareLatexParser();
    private SharelatexConnectionProperties properties;

    public ShareLatexManager() {
    }

    public String login(String server, String username, String password) throws IOException {
        return connector.connectToServer(server, username, password);
    }

    public List<ShareLatexProject> getProjects() throws IOException {
        if (connector.getProjects().isPresent()) {
            return parser.getProjectFromJson(connector.getProjects().get());
        }
        return Collections.emptyList();
    }

    public void startWebSocketHandler(String projectID, BibDatabaseContext database, ImportFormatPreferences preferences, FileUpdateMonitor fileMonitor) {
        JabRefExecutorService.INSTANCE.executeAndWait(() -> {

            try {
                connector.startWebsocketListener(projectID, database, preferences, fileMonitor);
            } catch (URISyntaxException e) {
                LOGGER.error("Exception {}", e);
            }
            registerListener(ShareLatexManager.this);
        });
    }

    /**
     * pushes the database content to overleaf
     *
     * @param bibDatabaseContext the context of the database to send
     */
    public void sendNewDatabaseContent(BibDatabaseContext bibDatabaseContext) {
        try {
            GeneralPreferences generalPreferences = Globals.prefs.getGeneralPreferences();
            SavePreferences prefs = Globals.prefs.getSavePreferences();

            StringWriter outputWriter = new StringWriter();
            BibtexDatabaseWriter databaseWriter = new BibtexDatabaseWriter(outputWriter, "\n", generalPreferences, prefs, Globals.entryTypesManager);
            databaseWriter.savePartOfDatabase(bibDatabaseContext, bibDatabaseContext.getEntries());
            String content = outputWriter.toString();
            connector.sendNewDatabaseContent(content);
        } catch (InterruptedException e) {
            LOGGER.error("Could not prepare database for saving ", e);
        } catch (IOException e) {
            LOGGER.error("General I/O Exception", e);
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
