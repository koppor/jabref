package org.jabref.logic.shared.restserver.core.representation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jabref.logic.citationstyle.CitationStyle;
import org.jabref.logic.citationstyle.CitationStyleGenerator;
import org.jabref.logic.citationstyle.CitationStyleOutputFormat;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CSLStyleAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CSLStyleAdapter.class);
    private static final String STYLES_ROOT = "/csl-styles";
    private static CSLStyleAdapter instance;

    private final Map<String, CitationStyle> namesToStyles = new HashMap<>();

    static {
        // Setup file system
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        try {
            FileSystems.newFileSystem(CitationStyle.class.getResource(STYLES_ROOT).toURI(), env);
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("Setting up filesystem failed", e);
        } catch (FileSystemAlreadyExistsException e) {
            LOGGER.info("Filesystem already exists");
        }
    }

    private CSLStyleAdapter() throws URISyntaxException, IOException {
        registerAvailableCitationStyles();
    }

    public static CSLStyleAdapter getInstance() throws URISyntaxException, IOException {
        if (instance == null) {
            instance = new CSLStyleAdapter();
        }
        return instance;
    }

    protected static void resetStyles() {
        instance = null;
    }

    /**
     * @param entry Entry to be returned in the expected style
     * @param style Expects a style from the list of styles returned by getRegisteredStyles()
     * @return A styled Entry in HTML, if the style is not available use default style
     */
    public String generateCitation(BibEntry entry, String style) {
        return CitationStyleGenerator.generateCitation(entry, namesToStyles.getOrDefault(style, CitationStyle.getDefault()).getSource(), CitationStyleOutputFormat.HTML, new BibDatabaseContext(), new BibEntryTypesManager());
    }

    /**
     * @param entry Entry to be returned in the expected style
     * @param style Expects a style from the list of styles returned by getRegisteredStyles()
     * @return A styled Entry in HTML, if the style is not available use default style
     */
    public String generatePlainCitation(BibEntry entry, String style) {
        return CitationStyleGenerator.generateCitation(entry, namesToStyles.getOrDefault(style, CitationStyle.getDefault()).getSource(), CitationStyleOutputFormat.TEXT, new BibDatabaseContext(), new BibEntryTypesManager());
    }

    public List<String> getRegisteredStyles() {
        return new ArrayList<>(namesToStyles.keySet());
    }

    private void registerAvailableCitationStyles() {
        List<CitationStyle> styles = CitationStyle.discoverCitationStyles();
        namesToStyles.putAll(styles.stream().collect(Collectors.toMap(CitationStyle::getTitle, citationStyle -> citationStyle)));
    }

    public CitationStyle registerCitationStyleFromFile(String citationStyleFile) throws IOException {
        CitationStyle style = CitationStyle.createCitationStyleFromFile(citationStyleFile).orElseThrow(FileNotFoundException::new);
        namesToStyles.putIfAbsent(style.getTitle(), style);
        return style;
    }
}
