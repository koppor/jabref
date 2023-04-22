package org.jabref.http.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.jabref.model.database.BibDatabaseContext;
import org.jabref.http.dto.BibEntryDTO;

public class SyncClient {

    private final BibDatabaseContext bibDatabaseContext;
    private Long lastSynchronizedGlobalRevision = -1L;

    private HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Initializes a client for the given context
     */
    public SyncClient(BibDatabaseContext bibDatabaseContext) throws IllegalArgumentException {
        if (bibDatabaseContext.getDatabasePath().isEmpty()) {
            throw new IllegalArgumentException("Unsaved libraries not yet supported.");
        }
        this.bibDatabaseContext = bibDatabaseContext;
    }

    /**
     * Client needs to store the state of Id and entry locally to be able to handle external changes.
     * This is done using the "dirty" flag.
     */
    private void synchronizeWithLocalView() {
    }

    public List<BibEntryDTO> getChanges() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create("http://localhost:8080/updates?lastUpdate=0"))
                                         .GET()
                                         .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return null;
    }

    /**
     * Synchronizes the given library with the server.
     * <p>
     * Pre-condition: Connection with server works
     */
    public void synchronize(BibDatabaseContext bibDatabaseContext) {
    }
}
