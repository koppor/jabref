package org.jabref.logic.protectedterms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jabref.logic.l10n.Localization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtectedTermsLoader {

    private static final Map<String, Supplier<String>> INTERNAL_LISTS = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtectedTermsLoader.class);

    private final List<ProtectedTermsList> mainList = new ArrayList<>();

    static {
        INTERNAL_LISTS.put("/protectedterms/months_weekdays.terms", () -> Localization.lang("Months and weekdays in English"));
        INTERNAL_LISTS.put("/protectedterms/countries_territories.terms", () -> Localization.lang("Countries and territories in English"));
        INTERNAL_LISTS.put("/protectedterms/electrical_engineering.terms", () -> Localization.lang("Electrical engineering terms"));
        INTERNAL_LISTS.put("/protectedterms/computer_science.terms", () -> Localization.lang("Computer science"));
    }

    public ProtectedTermsLoader(ProtectedTermsPreferences preferences) {
        update(preferences);
    }

    public static List<String> getInternalLists() {
        return new ArrayList<>(INTERNAL_LISTS.keySet());
    }

    public void update(ProtectedTermsPreferences preferences) {
        mainList.clear();

        // Read internal lists
        for (String filename : preferences.getEnabledInternalTermLists()) {
            if (INTERNAL_LISTS.containsKey(filename)) {
                mainList.add(readProtectedTermsListFromResource(filename, INTERNAL_LISTS.get(filename).get(), true));
            } else {
                LOGGER.warn("Protected terms resource '{}' is no longer available.", filename);
            }
        }
        for (String filename : preferences.getDisabledInternalTermLists()) {
            if (INTERNAL_LISTS.containsKey(filename)) {
                if (!preferences.getEnabledInternalTermLists().contains(filename)) {
                    mainList.add(readProtectedTermsListFromResource(filename, INTERNAL_LISTS.get(filename).get(), false));
                }
            } else {
                LOGGER.warn("Protected terms resource '{}' is no longer available.", filename);
            }
        }

        // Check if any new internal lists have emerged
        for (String filename : INTERNAL_LISTS.keySet()) {
            if (!preferences.getEnabledInternalTermLists().contains(filename)
                    && !preferences.getDisabledInternalTermLists().contains(filename)) {
                // New internal list, add it
                mainList.add(readProtectedTermsListFromResource(filename, INTERNAL_LISTS.get(filename).get(), true));
                LOGGER.warn("New protected terms resource '{}' is available and enabled by default.", filename);
            }
        }

        // Read external lists
        for (String filename : preferences.getEnabledExternalTermLists()) {
            Path filePath = Path.of(filename);
            if (Files.exists(filePath)) {
                mainList.add(readProtectedTermsListFromFile(filePath, true));
            } else {
                LOGGER.warn("Cannot find protected terms file {} ", filename);
            }
        }

        for (String filename : preferences.getDisabledExternalTermLists()) {
            if (!preferences.getEnabledExternalTermLists().contains(filename)) {
                mainList.add(readProtectedTermsListFromFile(Path.of(filename), false));
            }
        }
    }

    public void reloadProtectedTermsList(ProtectedTermsList list) {
        ProtectedTermsList newList = readProtectedTermsListFromFile(Path.of(list.getLocation()), list.isEnabled());
        int index = mainList.indexOf(list);
        if (index >= 0) {
            mainList.set(index, newList);
        } else {
            LOGGER.warn("Problem reloading protected terms file");
        }
    }

    public List<ProtectedTermsList> getProtectedTermsLists() {
        return mainList;
    }

    public List<String> getProtectedTerms() {
        Set<String> result = new HashSet<>();
        for (ProtectedTermsList list : mainList) {
            if (list.isEnabled()) {
                result.addAll(list.getTermList());
            }
        }

        return new ArrayList<>(result);
    }

    public void addProtectedTermsListFromFile(Path path, boolean enabled) {
        mainList.add(readProtectedTermsListFromFile(path, enabled));
    }

    public static ProtectedTermsList readProtectedTermsListFromResource(String resource, String description, boolean enabled) {
        ProtectedTermsParser parser = new ProtectedTermsParser();
        parser.readTermsFromResource(Objects.requireNonNull(resource), Objects.requireNonNull(description));
        return parser.getProtectTermsList(enabled, true);
    }

    public static ProtectedTermsList readProtectedTermsListFromFile(Path path, boolean enabled) {
        LOGGER.debug("Reading term list from file {}", path);
        ProtectedTermsParser parser = new ProtectedTermsParser();
        parser.readTermsFromFile(Objects.requireNonNull(path));
        return parser.getProtectTermsList(enabled, false);
    }

    public boolean removeProtectedTermsList(ProtectedTermsList termList) {
        termList.setEnabled(false);
        return mainList.remove(termList);
    }

    public ProtectedTermsList addNewProtectedTermsList(String newDescription, String newLocation, boolean enabled) {
        Objects.requireNonNull(newDescription);
        Objects.requireNonNull(newLocation);
        ProtectedTermsList resultingList = new ProtectedTermsList(newDescription, new ArrayList<>(), newLocation);
        resultingList.setEnabled(enabled);
        resultingList.createAndWriteHeading(newDescription);
        mainList.add(resultingList);
        return resultingList;
    }

    public ProtectedTermsList addNewProtectedTermsList(String newDescription, String newLocation) {
        return addNewProtectedTermsList(newDescription, newLocation, true);
    }
}
