package org.jabref.preferences;

import javafx.beans.property.*;
import org.jabref.logic.util.Version;

import java.nio.file.Path;

public class InternalPreferences {

    private final ObjectProperty<Version> ignoredVersion;
    private final BooleanProperty versionCheckEnabled;
    private final ObjectProperty<Path> lastPreferencesExportPath;
    private final StringProperty userAndHost;
    private final BooleanProperty memoryStickMode;

    public InternalPreferences(
            Version ignoredVersion,
            boolean versionCheck,
            Path exportPath,
            String userAndHost,
            boolean memoryStickMode) {
        this.ignoredVersion = new SimpleObjectProperty<>(ignoredVersion);
        this.versionCheckEnabled = new SimpleBooleanProperty(versionCheck);
        this.lastPreferencesExportPath = new SimpleObjectProperty<>(exportPath);
        this.userAndHost = new SimpleStringProperty(userAndHost);
        this.memoryStickMode = new SimpleBooleanProperty(memoryStickMode);
    }

    public Version getIgnoredVersion() {
        return ignoredVersion.getValue();
    }

    public ObjectProperty<Version> ignoredVersionProperty() {
        return ignoredVersion;
    }

    public void setIgnoredVersion(Version ignoredVersion) {
        this.ignoredVersion.set(ignoredVersion);
    }

    public boolean isVersionCheckEnabled() {
        return versionCheckEnabled.get();
    }

    public BooleanProperty versionCheckEnabledProperty() {
        return versionCheckEnabled;
    }

    public void setVersionCheckEnabled(boolean versionCheckEnabled) {
        this.versionCheckEnabled.set(versionCheckEnabled);
    }

    public Path getLastPreferencesExportPath() {
        return lastPreferencesExportPath.get();
    }

    public ObjectProperty<Path> lastPreferencesExportPathProperty() {
        return lastPreferencesExportPath;
    }

    public void setLastPreferencesExportPath(Path lastPreferencesExportPath) {
        this.lastPreferencesExportPath.set(lastPreferencesExportPath);
    }

    public String getUserAndHost() {
        return userAndHost.get();
    }

    public boolean isMemoryStickMode() {
        return memoryStickMode.get();
    }

    public BooleanProperty memoryStickModeProperty() {
        return memoryStickMode;
    }

    public void setMemoryStickMode(boolean memoryStickMode) {
        this.memoryStickMode.set(memoryStickMode);
    }
}
