package org.jabref.logic.shared.restserver.rest.base;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.jabref.logic.shared.restserver.core.properties.ServerPropertyService;
import org.jabref.logic.shared.restserver.core.repository.LibraryService;
import org.jabref.logic.shared.restserver.core.serialization.BibEntryMapper;
import org.jabref.logic.shared.restserver.rest.model.Library;
import org.jabref.model.entry.BibEntry;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("all")
public class Accumulation {
    private static final Logger LOGGER = LoggerFactory.getLogger(Accumulation.class);
    private final LibraryService libraryService;

    public Accumulation() {
        libraryService = LibraryService.getInstance(ServerPropertyService.getInstance().getWorkingDirectory());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Library getAllEntries() throws IOException {
        try {
            List<BibEntry> entries = libraryService.getAllEntries();
            return new Library(entries.parallelStream().map(BibEntryMapper::map).collect(Collectors.toList()));
        } catch (IOException e) {
            LOGGER.error("Error accumulating all entries.", e);
            throw e;
        }
    }
}
