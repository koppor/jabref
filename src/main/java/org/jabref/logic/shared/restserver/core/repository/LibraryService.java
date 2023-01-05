package org.jabref.logic.shared.restserver.core.repository;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jabref.gui.Globals;
import org.jabref.logic.database.DatabaseMerger;
import org.jabref.logic.exporter.AtomicFileWriter;
import org.jabref.logic.exporter.BibWriter;
import org.jabref.logic.exporter.BibtexDatabaseWriter;
import org.jabref.logic.exporter.SavePreferences;
import org.jabref.logic.importer.OpenDatabase;
import org.jabref.logic.shared.restserver.rest.model.NewLibraryDTO;
import org.jabref.logic.util.OS;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.util.DummyFileUpdateMonitor;
import org.jabref.model.util.FileUpdateMonitor;
import org.jabref.preferences.GeneralPreferences;
import org.jabref.preferences.JabRefPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibraryService {

    private static final Map<Path, LibraryService> instances = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryService.class);
    private final Path workingDirectory;

    private LibraryService(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
        if (Files.notExists(workingDirectory)) {
            try {
                Files.createDirectories(workingDirectory);
            } catch (IOException e) {
                LOGGER.error("Could not create working directory.", e);
                System.exit(1);
            }
        }
    }

    public static LibraryService getInstance(Path workingDirectory) {
        return instances.computeIfAbsent(workingDirectory, LibraryService::new);
    }

    public List<String> getLibraryNames() throws IOException {
        return Files.list(workingDirectory) // Alternatively walk(Path start, int depth) for recursive aggregation
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(file -> file.endsWith(".bib"))
                    .collect(Collectors.toList());
    }

    public void createLibrary(NewLibraryDTO newLibraryConfiguration) throws IOException {
        Files.createFile(getLibraryPath(newLibraryConfiguration.getLibraryName()));
    }

    public Boolean deleteLibrary(String libraryName) throws IOException {
        return Files.deleteIfExists(getLibraryPath(libraryName));
    }

    public boolean libraryExists(String libraryName) {
        return Files.exists(getLibraryPath(libraryName));
    }

    public List<BibEntry> getLibraryEntries(String libraryName) throws IOException {
        Path libraryPath = getLibraryPath(libraryName);
        if (!Files.exists(libraryPath)) {
            throw new FileNotFoundException();
        }
        // We do not need any update monitoring
        return new ArrayList<>(OpenDatabase.loadDatabase(libraryPath, Globals.prefs.getImportFormatPreferences(), Globals.getFileUpdateMonitor())
                                           .getDatabase()
                                           .getEntries());
    }

    public Optional<BibEntry> getLibraryEntryMatchingCiteKey(String libraryName, String citeKey) throws IOException {
        Path libraryPath = getLibraryPath(libraryName);
        if (!Files.exists(libraryPath)) {
            throw new FileNotFoundException();
        }

        // Note that this might lead to issues if multiple entries have the same cite key!
        return OpenDatabase.loadDatabase(libraryPath, Globals.prefs.getImportFormatPreferences(), Globals.getFileUpdateMonitor())
                           .getDatabase()
                           .getEntryByCitationKey(citeKey);
    }

    public synchronized void addEntryToLibrary(String libraryName, BibEntry newEntry) throws IOException {
        // Enforce that a citation key is provided and that is is not part of the library already.
        if (newEntry.getCitationKey().isEmpty()) {
            throw new IllegalArgumentException("Entry does not contain a citation key");
        }
        Path libraryPath = getLibraryPath(libraryName);
        BibDatabaseContext context;
        if (!Files.exists(libraryPath)) {
            throw new FileNotFoundException();
        } else {
            context = OpenDatabase.loadDatabase(libraryPath,  Globals.prefs.getImportFormatPreferences(), Globals.getFileUpdateMonitor())
                                  .getDatabaseContext();
        }
        // Required to get serialized
        newEntry.setChanged(true);
        if (this.citationKeyAlreadyExists(libraryName, newEntry.getCitationKey().get())) {
            throw new IllegalArgumentException("Library already contains an entry with that citation key.");
        }
        context.getDatabase().insertEntry(newEntry);
        GeneralPreferences generalPreferences = JabRefPreferences.getInstance().getGeneralPreferences();
        SavePreferences savePreferences = JabRefPreferences.getInstance().getSavePreferences();

        try (AtomicFileWriter fileWriter = new AtomicFileWriter(libraryPath, context.getMetaData().getEncoding().orElse(StandardCharsets.UTF_8), savePreferences.shouldMakeBackup())) {
            BibWriter writer = new BibWriter(fileWriter, OS.NEWLINE);
            BibtexDatabaseWriter databaseWriter = new BibtexDatabaseWriter(writer, JabRefPreferences.getInstance().getGeneralPreferences(), savePreferences, new BibEntryTypesManager());
            databaseWriter.saveDatabase(context);
        }
    }

    public synchronized void updateEntry(String libraryName, String citeKey, BibEntry updatedEntry) throws IOException {
        // Enforce that a citation key is provided and that is is not part of the library already.
        if (updatedEntry.getCitationKey().isEmpty()) {
            throw new IllegalArgumentException("Entry does not contain a citation key");
        }
        this.deleteEntryByCiteKey(libraryName, citeKey);
        updatedEntry.setChanged(true);
        this.addEntryToLibrary(libraryName, updatedEntry);
    }

    public synchronized boolean deleteEntryByCiteKey(String libraryName, String citeKey) throws IOException {
        Path libraryPath = getLibraryPath(libraryName);
        BibDatabaseContext context;
        if (!Files.exists(libraryPath)) {
            return false;
        } else {
            context = OpenDatabase.loadDatabase(libraryPath, Globals.prefs.getImportFormatPreferences(), Globals.getFileUpdateMonitor())
                                  .getDatabaseContext();
        }
        Optional<BibEntry> entry = context.getDatabase().getEntryByCitationKey(citeKey);
        if (entry.isEmpty()) {
            return false;
        }

        context.getDatabase().removeEntry(entry.get());
        GeneralPreferences generalPreferences = JabRefPreferences.getInstance().getGeneralPreferences();
        SavePreferences savePreferences = JabRefPreferences.getInstance().getSavePreferences();

        try (AtomicFileWriter fileWriter = new AtomicFileWriter(libraryPath, context.getMetaData().getEncoding().orElse(StandardCharsets.UTF_8), savePreferences.shouldMakeBackup())) {
            BibWriter writer = new BibWriter(fileWriter, OS.NEWLINE);
            BibtexDatabaseWriter databaseWriter = new BibtexDatabaseWriter(writer, generalPreferences, savePreferences, new BibEntryTypesManager());
            databaseWriter.saveDatabase(context);
            return true;
        }
    }

    public List<BibEntry> getAllEntries() throws IOException {
        List<String> libraryNames = getLibraryNames();
        BibDatabase result = new BibDatabase();
        DatabaseMerger merger = new DatabaseMerger(JabRefPreferences.getInstance().getBibEntryPreferences().getKeywordSeparator());
        FileUpdateMonitor dummy = new DummyFileUpdateMonitor();
        libraryNames.stream()
                    .map(this::getLibraryPath)
                    .map(path -> {
                        try {
                            return OpenDatabase.loadDatabase(path, Globals.prefs.getImportFormatPreferences(), Globals.getFileUpdateMonitor()).getDatabase();
                        } catch (IOException e) {
                            // Just return an empty database, a.k.a if opening fails, ignore it
                            return new BibDatabase();
                        }
                    })
                    .forEach(database -> merger.merge(result, database));
        return new ArrayList<>(result.getEntries());
    }

    private boolean citationKeyAlreadyExists(String libraryName, String citationKey) throws IOException {
        return this.getLibraryEntryMatchingCiteKey(libraryName, citationKey).isPresent();
    }

    private Path getLibraryPath(String libraryName) {
        libraryName = addBibExtensionIfMissing(libraryName);
        LOGGER.info("Resolved path: {}", workingDirectory.resolve(libraryName));
        // For now make assumption that the directory is flat:
        return workingDirectory.resolve(libraryName);
    }

    private String addBibExtensionIfMissing(String libraryName) {
        return libraryName.endsWith(".bib") ? libraryName : libraryName + ".bib";
    }
}
