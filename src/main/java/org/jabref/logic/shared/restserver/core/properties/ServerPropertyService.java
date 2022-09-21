package org.jabref.logic.shared.restserver.core.properties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerPropertyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerPropertyService.class);
    private static ServerPropertyService instance;
    private final Properties serverProperties;

    private ServerPropertyService() {
        serverProperties = loadProperties();
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
     * 2. Default User home with a new directory for the library
     */
    private Properties loadProperties() {
        Properties properties = new Properties();
        if (!((System.getenv("LIBRARY_WORKSPACE") == null) || System.getenv("LIBRARY_WORKSPACE").isBlank())) {
            LOGGER.info("Environment Variable found, using defined directory: {}", System.getenv("LIBRARY_WORKSPACE"));
            properties.setProperty("workingDirectory", System.getenv("LIBRARY_WORKSPACE"));
        } else {
            LOGGER.info("Working directory was not found in either the properties or the environment variables, falling back to default location: {}", System.getProperty("user.home") + "/planqk-library");
            properties.setProperty("workingDirectory", System.getProperty("user.home") + "/planqk-library");
        }
        return properties;
    }

    public Path getWorkingDirectory() {
        return Paths.get(serverProperties.getProperty("workingDirectory"));
    }
}
