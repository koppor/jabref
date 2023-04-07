package org.jabref.http.server;

import java.io.IOException;
import java.nio.file.Files;

import org.jabref.logic.util.io.BackupFileUtil;
import org.jabref.preferences.PreferencesService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("libraries/{id}")
public class LibraryResource {
    public static final Logger LOGGER = LoggerFactory.getLogger(LibraryResource.class);

    @Inject
    PreferencesService preferences;

    @GET
    @Produces(org.jabref.http.MediaType.BIBTEX)
    public Response getBibtex(@PathParam("id") String id) {
        java.nio.file.Path library = getLibraryPath(id);
        String libraryAsString;
        try {
            libraryAsString = Files.readString(library);
        } catch (IOException e) {
            LOGGER.error("Could not read library {}", library, e);
            throw new InternalServerErrorException("Could not read library " + library, e);
        }
        return Response.ok()
                .entity(libraryAsString)
                .build();
    }

    private java.nio.file.Path getLibraryPath(String id) {
        java.nio.file.Path library = preferences.getGuiPreferences().getLastFilesOpened()
                                                .stream()
                                                .map(java.nio.file.Path::of)
                                                .filter(p -> (p.getFileName() + "-" + BackupFileUtil.getUniqueFilePrefix(p)).equals(id))
                                                .findAny()
                                                .orElseThrow(() -> new NotFoundException());
        return library;
    }
}
