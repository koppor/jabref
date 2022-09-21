package org.jabref.logic.shared.restserver.rest.base;

import java.io.IOException;
import java.util.List;

import org.jabref.logic.shared.restserver.core.properties.ServerPropertyService;
import org.jabref.logic.shared.restserver.core.repository.LibraryService;
import org.jabref.logic.shared.restserver.rest.model.NewLibraryDTO;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("libraries")
public class Libraries {
    private final LibraryService libraryService;
    private final Logger LOGGER = LoggerFactory.getLogger(Libraries.class);

    public Libraries() {
        libraryService = LibraryService.getInstance(ServerPropertyService.getInstance().getWorkingDirectory());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getLibraryNames() throws IOException {
        try {
            return libraryService.getLibraryNames();
        } catch (IOException e) {
            LOGGER.error("Error retrieving library names.", e);
            throw e;
        }
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    public Response createNewLibrary(NewLibraryDTO newLibraryConfiguration) throws IOException {
        if (libraryService.libraryExists(newLibraryConfiguration.getLibraryName())) {
            return Response.status(Response.Status.CONFLICT)
                           .entity("The given library name is taken.")
                           .build();
        }
        libraryService.createLibrary(newLibraryConfiguration);
        return Response.ok()
                       .build();
    }

    @Path("{libraryName}")
    public Library getLibraryResource(@PathParam("libraryName") String libraryName) {
        return new Library(libraryName);
    }
}
