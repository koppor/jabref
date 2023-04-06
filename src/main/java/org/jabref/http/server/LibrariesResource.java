package org.jabref.http.server;

import java.net.URI;
import java.util.List;

import org.jabref.logic.util.io.BackupFileUtil;
import org.jabref.preferences.PreferencesService;

import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.RedirectionException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("libraries")
public class LibrariesResource {
    @Inject
    PreferencesService preferences;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String get(@QueryParam("tofirst") String toFirst) {
        List<String> fileNamesWithUniqueSuffix = preferences.getGuiPreferences().getLastFilesOpened().stream()
                                                            .map(java.nio.file.Path::of)
                                                            .map(p -> p.getFileName() + "-" + BackupFileUtil.getUniqueFilePrefix(p))
                                                            .toList();
        if (toFirst != null) {
            if (fileNamesWithUniqueSuffix.isEmpty()) {
                throw new NotFoundException();
            }
            throw new RedirectionException(Response.Status.SEE_OTHER, URI.create("libraries/" + fileNamesWithUniqueSuffix.get(0)));
        }
        return new Gson().toJson(fileNamesWithUniqueSuffix);
    }
}
