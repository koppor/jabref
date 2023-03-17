package org.jabref.logic.shared.restserver.core.properties;

import java.nio.file.Path;

import org.jabref.gui.desktop.JabRefDesktop;
import org.jabref.gui.desktop.os.NativeDesktop;
import org.jabref.model.strings.StringUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerPropertyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerPropertyService.class);
    private static ServerPropertyService instance;
    private Path workingDirectory;

    private ServerPropertyService() {
        workingDirectory = determineWorkingDirectory();
    }

    public static ServerPropertyService getInstance() {
        if (instance == null) {
            instance = new ServerPropertyService();
        }
        return instance;
    }

    /**
     * Tries to determine the working directory of the library.
     * Uses the first path it finds when resolving in this order:
     * 1. Environment variable LIBRARY_WORKSPACE
     * 2. Default User home (via {@link NativeDesktop#getDefaultFileChooserDirectory()}) with a new directory for the library
     */
    private Path determineWorkingDirectory() {
        String libraryWorkspaceEnvironmentVariable = System.getenv("LIBRARY_WORKSPACE");
        if (!StringUtil.isNullOrEmpty(libraryWorkspaceEnvironmentVariable)) {
            LOGGER.info("Environment Variable found, using defined directory: {}", libraryWorkspaceEnvironmentVariable);
            return Path.of(libraryWorkspaceEnvironmentVariable);
        } else {
            Path fallbackDirectory = JabRefDesktop.getNativeDesktop().getDefaultFileChooserDirectory().resolve("planqk-library");
            LOGGER.info("Working directory was not found in either the properties or the environment variables, falling back to default location: {}", fallbackDirectory);
            return fallbackDirectory;
        }
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }
}
