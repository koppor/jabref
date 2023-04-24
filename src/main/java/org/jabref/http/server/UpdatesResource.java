package org.jabref.http.server;

import java.lang.reflect.Type;
import java.util.List;

import org.jabref.http.dto.BibEntryDTO;
import org.jabref.http.sync.state.ChangesAndServerView;
import org.jabref.http.sync.state.SyncState;
import org.jabref.preferences.PreferencesService;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("libraries/{id}/updates")
public class UpdatesResource {
    @Inject
    Gson gson;

    @Inject
    PreferencesService preferences;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String get(@PathParam("id") String id, @QueryParam("since") int since) {
        SyncState syncState = new SyncState(LibraryResource.getParserResult(preferences, id).getDatabaseContext());
        ChangesAndServerView changes = syncState.changesAndServerView(since);
        return gson.toJson(changes);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public void acceptChanges(String changes) {
        Type listType = new TypeToken<List<BibEntryDTO>>() {
        }.getType();
        List<BibEntryDTO> result = gson.fromJson(changes, listType);
        System.out.println(result);
    }
}
