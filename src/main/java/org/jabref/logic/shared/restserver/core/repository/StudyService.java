package org.jabref.logic.shared.restserver.core.repository;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jabref.gui.Globals;
import org.jabref.logic.crawler.Crawler;
import org.jabref.logic.crawler.StudyYamlParser;
import org.jabref.logic.git.GitHandler;
import org.jabref.logic.git.SlrGitHandler;
import org.jabref.logic.importer.ParseException;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.study.Study;
import org.jabref.model.util.DummyFileUpdateMonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudyService {
    private static final Map<Path, StudyService> INSTANCES = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyService.class);
    // Contains all running tasks,
    private final Map<String, CrawlTask> runningCrawls = new HashMap<>();
    private final Path studiesDirectory;

    /**
     * Returns a study service instance for the selected working directory
     *
     * @param workingDirectory the path under which the studies can be found in the studies directory
     */
    private StudyService(Path workingDirectory) {
        this.studiesDirectory = workingDirectory.resolve("studies");
        if (Files.notExists(studiesDirectory)) {
            try {
                LOGGER.info(studiesDirectory.toString());
                Files.createDirectories(studiesDirectory);
                Map<String, String> env = new HashMap<>();
                env.put("create", "true");
                try {
                    FileSystems.newFileSystem(GitHandler.class.getResource("git.gitignore").toURI(), env);
                } catch (IOException | URISyntaxException e) {
                    LOGGER.error("Setting up filesystem failed", e);
                } catch (FileSystemAlreadyExistsException e) {
                    LOGGER.info("Filesystem already exists");
                }
            } catch (IOException e) {
                LOGGER.error("Could not create working directory.", e);
                System.exit(1);
            }
        }
    }

    public static synchronized StudyService getInstance(Path workingDirectory) {
        return INSTANCES.computeIfAbsent(workingDirectory, StudyService::new);
    }

    /**
     * Each study is managed as a directory within the study directory
     *
     * @return A list of the currently existing studies
     */
    public List<String> getStudyNames() throws IOException {
        return Files.list(studiesDirectory)                // Alternatively walk(Path start, int depth) for recursive aggregation
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
    }

    public synchronized void createStudy(Study study) throws IOException {
        Files.createDirectories(studiesDirectory.resolve(Paths.get(study.getTitle())));
        StudyYamlParser parser = new StudyYamlParser();
        parser.writeStudyYamlFile(study, studiesDirectory.resolve(Paths.get(study.getTitle(), "study.yml")));
    }

    public boolean deleteStudy(String studyName) throws IOException {
        Path study = studiesDirectory.resolve(Paths.get(studyName));
        if (Files.notExists(study)) {
            return false;
        }

        Files.walk(study)
          .sorted(Comparator.reverseOrder())
          .forEach(t -> {
            try {
                Files.delete(t);
            } catch (IOException e) {
             LOGGER.error("Error deleting dir", e);
            }
        });
        return true;
    }

    public boolean studyExists(String studyName) {
        return Files.exists(getStudyPath(studyName));
    }

    /**
     * Starts a crawl for a specific study
     *
     * @throws ParseException Occurs if the study definition file is malformed
     */
    public synchronized void startCrawl(String studyName) throws IOException, ParseException {
        if (runningCrawls.containsKey(studyName)) {
            return;
        }
        Path studyDirectory = studiesDirectory.resolve(Paths.get(studyName));
        CrawlTask crawl = new CrawlTask(new Crawler(studyDirectory,
                                                    new SlrGitHandler(studyDirectory),
                                                    Globals.prefs.getGeneralPreferences(),
                                                    Globals.prefs.getImportFormatPreferences(),
                                                    Globals.prefs.getImporterPreferences(),
                                                    Globals.prefs.getSavePreferences(),
                                                    new BibEntryTypesManager(),
                                                    new DummyFileUpdateMonitor()));
        runningCrawls.put(studyName, crawl);
        new Thread(crawl).start();
    }

    /**
     * Checks whether there is a crawl running for the specified study.
     * Removes crawls that are finished or failed.
     */
    public Boolean isCrawlRunning(String studyName) {
        if (!runningCrawls.containsKey(studyName)) {
            return false;
        }
        if (runningCrawls.get(studyName).getStatus() == TaskStatus.RUNNING) {
            return true;
        }
        runningCrawls.remove(studyName);
        return false;
    }

    public Path getStudyPath(String studyName) {
        return studiesDirectory.resolve(Paths.get(studyName));
    }

    public Study getStudyDefinition(String studyName) throws IOException {
        StudyYamlParser parser = new StudyYamlParser();
        return parser.parseStudyYamlFile(studiesDirectory.resolve(Paths.get(studyName, "study.yml")));
    }
}
