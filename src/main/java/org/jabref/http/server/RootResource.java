package org.jabref.http.server;

import java.lang.reflect.Type;
import java.util.List;

import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.types.EntryType;
import org.jabref.http.dto.BibEntryDTO;
import org.jabref.http.dto.typeadapters.EntryTypeAdapter;
import org.jabref.http.dto.typeadapters.FieldAdapter;
import org.jabref.http.dto.typeadapters.MapFieldStringAdapter;
import org.jabref.http.sync.state.SyncState;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.integration.OpenApiContextLocator;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class RootResource {

    @GET
    @Path("openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public String getOpenApiJson() {
        OpenAPI openAPI = OpenApiContextLocator.getInstance().getOpenApiContext("org.jabref.sync").read();
        return Json.pretty(openAPI);
    }

    @GET
    @Path("updates")
    @Produces(MediaType.APPLICATION_JSON)
    public String get(@QueryParam("since") int since) {
        List<BibEntryDTO> changes = SyncState.INSTANCE.changes(since);
        return new GsonBuilder().setPrettyPrinting().create().toJson(changes);
    }

    @POST
    @Path("updates")
    @Produces(MediaType.APPLICATION_JSON)
    public void acceptChanges(String changes) {
        Type listType = new TypeToken<List<BibEntryDTO>>() {
        }.getType();
        List<BibEntryDTO> result = new GsonBuilder()
                .registerTypeAdapter(EntryType.class, new EntryTypeAdapter())
                .registerTypeAdapter(Field.class, new FieldAdapter())
                .registerTypeAdapter(MapFieldStringAdapter.TYPE, new MapFieldStringAdapter())
                .create().fromJson(changes, listType);
        System.out.println(result);
    }
}
