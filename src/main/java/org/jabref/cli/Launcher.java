package org.jabref.cli;

import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import org.jabref.gui.Globals;
import org.jabref.gui.MainApplication;
import org.jabref.logic.journals.JournalAbbreviationLoader;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.net.ProxyAuthenticator;
import org.jabref.logic.net.ProxyPreferences;
import org.jabref.logic.net.ProxyRegisterer;
import org.jabref.logic.net.ssl.SSLPreferences;
import org.jabref.logic.net.ssl.TrustStoreManager;
import org.jabref.logic.protectedterms.ProtectedTermsLoader;
import org.jabref.logic.remote.RemotePreferences;
import org.jabref.logic.remote.client.RemoteClient;
import org.jabref.logic.shared.restserver.rest.Root;
import org.jabref.logic.util.BuildInfo;
import org.jabref.migrations.PreferencesMigrations;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.preferences.JabRefPreferences;
import org.jabref.preferences.PreferencesService;

import jakarta.ws.rs.SeBootstrap;
import net.harawata.appdirs.AppDirsFactory;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinylog.configuration.Configuration;

/**
 * The main entry point for the JabRef application.
 * <p>
 * It has two main functions:
 * - Handle the command line arguments
 * - Start the JavaFX application (if not in cli mode)
 */
public class Launcher {
    // initialized after reading the preferences (which configure log directory, ...)
    private static Logger LOGGER;

    public static void main(String[] args) {
        addLogToDisk();
        try {
            // we need a copy of the original arguments
            String[] arguments = args;

            // Init preferences
            final JabRefPreferences preferences = JabRefPreferences.getInstance();
            Globals.prefs = preferences;
            PreferencesMigrations.runMigrations(preferences);

            // Early exit in case another instance is already running
            if (!handleMultipleAppInstances(args, preferences)) {
                return;
            }

            // Init rest of preferences
            configureProxy(preferences.getProxyPreferences());
            configureSSL(preferences.getSSLPreferences());
            applyPreferences(preferences);
            clearOldSearchIndices();

            try {
                // Process arguments
                ArgumentProcessor argumentProcessor = new ArgumentProcessor(args, ArgumentProcessor.Mode.INITIAL_START,
                        preferences);
                if (argumentProcessor.shouldShutDown()) {
                    LOGGER.debug("JabRef shut down after processing command line arguments");
                    return;
                }
                startServer();

                MainApplication.main(argumentProcessor.getParserResults(), argumentProcessor.isBlank(), preferences, arguments);
            } catch (ParseException e) {
                LOGGER.error("Problem parsing arguments", e);
                JabRefCLI.printUsage(preferences);
            }
        } catch (Exception ex) {
            LOGGER.error("Unexpected exception", ex);
        }

    }

    static void startServer() {
        SeBootstrap.start(Root.class).thenAccept(instance -> {
            instance.stopOnShutdown(stopResult ->
                    System.out.printf("JabRef REST server stop result: %s [Native stop result: %s].%n", stopResult, stopResult.unwrap(Object.class)));
            URI uri = instance.configuration().baseUri();
            System.out.printf("JabRef REST server %s running at %s [Native handle: %s].%n", instance, uri, instance.unwrap(Object.class));
        });

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            LOGGER.error("could not join on current thread", e);
        }
    }

    /**
     * This needs to be called as early as possible. After the first log write, it
     * is not possible to alter
     * the log configuration programmatically anymore.
     */
    private static void addLogToDisk() {
        Path directory = Path.of(AppDirsFactory.getInstance().getUserLogDir(
                "jabref",
                new BuildInfo().version.toString(),
                "org.jabref"));
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            initializeLogger();
            LOGGER.error("Could not create log directory {}", directory, e);
            return;
        }
        // The "Shared File Writer" is explained at
        // https://tinylog.org/v2/configuration/#shared-file-writer
        Map<String, String> configuration = Map.of(
                "writerFile", "shared file",
                "writerFile.level", "info",
                "writerFile.file", directory.resolve("log.txt").toString(),
                "writerFile.charset", "UTF-8");

        configuration.entrySet().forEach(config -> Configuration.set(config.getKey(), config.getValue()));
        initializeLogger();
    }

    private static void initializeLogger() {
        LOGGER = LoggerFactory.getLogger(MainApplication.class);
    }

    private static boolean handleMultipleAppInstances(String[] args, PreferencesService preferences) {
        RemotePreferences remotePreferences = preferences.getRemotePreferences();
        if (remotePreferences.useRemoteServer()) {
            // Try to contact already running JabRef
            RemoteClient remoteClient = new RemoteClient(remotePreferences.getPort());
            if (remoteClient.ping()) {
                // We are not alone, there is already a server out there, send command line
                // arguments to other instance
                if (remoteClient.sendCommandLineArguments(args)) {
                    // So we assume it's all taken care of, and quit.
                    LOGGER.info(Localization.lang("Arguments passed on to running JabRef instance. Shutting down."));
                    return false;
                } else {
                    LOGGER.warn("Could not communicate with other running JabRef instance.");
                }
            }
        }
        return true;
    }

    private static void applyPreferences(PreferencesService preferences) {
        // Read list(s) of journal names and abbreviations
        Globals.journalAbbreviationRepository = JournalAbbreviationLoader
                .loadRepository(preferences.getJournalAbbreviationPreferences());

        // Build list of Import and Export formats
        Globals.IMPORT_FORMAT_READER.resetImportFormats(
                preferences.getImporterPreferences(),
                preferences.getImportFormatPreferences(),
                Globals.getFileUpdateMonitor());
        Globals.entryTypesManager.addCustomOrModifiedTypes(
                preferences.getBibEntryTypes(BibDatabaseMode.BIBTEX),
                preferences.getBibEntryTypes(BibDatabaseMode.BIBLATEX));

        // Initialize protected terms loader
        Globals.protectedTermsLoader = new ProtectedTermsLoader(preferences.getProtectedTermsPreferences());
    }

    private static void configureProxy(ProxyPreferences proxyPreferences) {
        ProxyRegisterer.register(proxyPreferences);
        if (proxyPreferences.shouldUseProxy() && proxyPreferences.shouldUseAuthentication()) {
            Authenticator.setDefault(new ProxyAuthenticator());
        }
    }

    private static void configureSSL(SSLPreferences sslPreferences) {
        TrustStoreManager.createTruststoreFileIfNotExist(Path.of(sslPreferences.getTruststorePath()));
        System.setProperty("javax.net.ssl.trustStore", sslPreferences.getTruststorePath());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

    private static void clearOldSearchIndices() {
        Path currentIndexPath = BibDatabaseContext.getFulltextIndexBasePath();
        Path appData = currentIndexPath.getParent();

        try {
            Files.createDirectories(currentIndexPath);
        } catch (IOException e) {
            LOGGER.error("Could not create index directory {}", appData, e);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(appData)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && !path.toString().endsWith("ssl") && path.toString().contains("lucene")
                        && !path.equals(currentIndexPath)) {
                    LOGGER.info("Deleting out-of-date fulltext search index at {}.", path);
                    Files.walk(path)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not access app-directory at {}", appData, e);
        }
    }
}
