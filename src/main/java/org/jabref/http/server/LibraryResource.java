package org.jabref.http.server;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.jabref.gui.Globals;
import org.jabref.http.dto.BibEntryDTO;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.importer.fileformat.BibtexImporter;
import org.jabref.logic.util.io.BackupFileUtil;
import org.jabref.model.util.DummyFileUpdateMonitor;
import org.jabref.preferences.PreferencesService;

import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("libraries/{id}")
public class LibraryResource {
    public static final Logger LOGGER = LoggerFactory.getLogger(LibraryResource.class);

    @Inject
    PreferencesService preferences;

    @Inject
    Gson gson;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String get(@PathParam("id") String id) {
        java.nio.file.Path library = preferences.getGuiPreferences().getLastFilesOpened()
                                                .stream()
                                                .map(java.nio.file.Path::of)
                                                .filter(p -> (p.getFileName() + "-" + BackupFileUtil.getUniqueFilePrefix(p)).equals(id))
                                                .findAny()
                                                .orElseThrow(() -> new NotFoundException());
        ParserResult parserResult;
        try {
            parserResult = new BibtexImporter(preferences.getImportFormatPreferences(), new DummyFileUpdateMonitor()).importDatabase(library);
        } catch (IOException e) {
            LOGGER.warn("Could not find open library file {}", library, e);
            throw new InternalServerErrorException("Could not parse library", e);
        }
        List<BibEntryDTO> list = parserResult.getDatabase().getEntries().stream()
                                             .map(bibEntry -> {
                                                 bibEntry.getSharedBibEntryData().setSharedID(Objects.hash(bibEntry));
                                                 return bibEntry;
                                             })
                                             .map(entry -> new BibEntryDTO(entry, parserResult.getDatabaseContext().getMode(), preferences.getFieldWriterPreferences(), Globals.entryTypesManager))
                                             .toList();
        return gson.toJson(list);
    }
}
