package org.jabref.preferences;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.jabref.model.database.BibDatabaseMode;

import com.google.common.base.Strings;

public class GeneralPreferences {
    private final ObjectProperty<Charset> defaultEncoding;
    private final ObjectProperty<BibDatabaseMode> defaultBibDatabaseMode;
    private final BooleanProperty warnAboutDuplicatesInInspection;
    private final BooleanProperty confirmDelete;

    private final BooleanProperty memoryStickMode;
    private final BooleanProperty showAdvancedHints;

    public GeneralPreferences(Charset defaultEncoding,
                              BibDatabaseMode defaultBibDatabaseMode,
                              boolean warnAboutDuplicatesInInspection,
                              boolean confirmDelete,
                              boolean memoryStickMode,
                              boolean showAdvancedHints) {
        this.defaultEncoding = new SimpleObjectProperty<>(defaultEncoding);
        this.defaultBibDatabaseMode = new SimpleObjectProperty<>(defaultBibDatabaseMode);
        this.warnAboutDuplicatesInInspection = new SimpleBooleanProperty(warnAboutDuplicatesInInspection);
        this.confirmDelete = new SimpleBooleanProperty(confirmDelete);

        this.memoryStickMode = new SimpleBooleanProperty(memoryStickMode);
        this.showAdvancedHints = new SimpleBooleanProperty(showAdvancedHints);
    }

    /**
     * Creates Object with default values
     */
    public GeneralPreferences() {
        this(StandardCharsets.UTF_8,
                BibDatabaseMode.BIBTEX,
                true,
                true,
                false,
                true
        );
    }

    public void setDefaults() {
        GeneralPreferences defaults = new GeneralPreferences();
        this.defaultBibDatabaseMode.setValue(defaults.getDefaultBibDatabaseMode());
        this.defaultEncoding.setValue(defaults.getDefaultEncoding());
        this.warnAboutDuplicatesInInspection.setValue(defaults.shouldWarnAboutDuplicatesInInspection());
        this.confirmDelete.setValue(shouldConfirmDelete());
        this.memoryStickMode.setValue(defaults.isMemoryStickMode());
        this.showAdvancedHints.setValue(defaults.shouldShowAdvancedHints());
    }

    public Charset getDefaultEncoding() {
        return defaultEncoding.get();
    }

    public ObjectProperty<Charset> defaultEncodingProperty() {
        return defaultEncoding;
    }

    public void setDefaultEncoding(Charset defaultEncoding) {
        this.defaultEncoding.set(defaultEncoding);
    }

    public BibDatabaseMode getDefaultBibDatabaseMode() {
        return defaultBibDatabaseMode.get();
    }

    public ObjectProperty<BibDatabaseMode> defaultBibDatabaseModeProperty() {
        return defaultBibDatabaseMode;
    }

    public void setDefaultBibDatabaseMode(BibDatabaseMode defaultBibDatabaseMode) {
        this.defaultBibDatabaseMode.set(defaultBibDatabaseMode);
    }

    public boolean shouldWarnAboutDuplicatesInInspection() {
        return warnAboutDuplicatesInInspection.get();
    }

    public BooleanProperty warnAboutDuplicatesInInspectionProperty() {
        return warnAboutDuplicatesInInspection;
    }

    public void setWarnAboutDuplicatesInInspection(boolean warnAboutDuplicatesInInspection) {
        this.warnAboutDuplicatesInInspection.set(warnAboutDuplicatesInInspection);
    }

    public boolean shouldConfirmDelete() {
        return confirmDelete.get();
    }

    public BooleanProperty confirmDeleteProperty() {
        return confirmDelete;
    }

    public void setConfirmDelete(boolean confirmDelete) {
        this.confirmDelete.set(confirmDelete);
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

    public boolean shouldShowAdvancedHints() {
        return showAdvancedHints.get();
    }

    public BooleanProperty showAdvancedHintsProperty() {
        return showAdvancedHints;
    }

    public void setShowAdvancedHints(boolean showAdvancedHints) {
        this.showAdvancedHints.set(showAdvancedHints);
    }

    public static class Builder {
        private final GeneralPreferences toBuild;

        public Builder() {
            toBuild = new GeneralPreferences();
        }

        public Builder withDefaultEncoding(Charset defaultEncoding) {
            toBuild.setDefaultEncoding(defaultEncoding);
            return this;
        }

        public Builder withDefaultEncoding(String defaultEncoding) {
            if (!Strings.isNullOrEmpty(defaultEncoding)) {
                toBuild.setDefaultEncoding(Charset.forName(defaultEncoding));
            }
            return this;
        }

        public Builder withDefaultBibDatabaseMode(BibDatabaseMode defaultBibDatabaseMode) {
            toBuild.setDefaultBibDatabaseMode(defaultBibDatabaseMode);
            return this;
        }

        public Builder withWarnAboutDuplicatesInInspection(boolean warnAboutDuplicatesInInspection) {
            toBuild.setWarnAboutDuplicatesInInspection(warnAboutDuplicatesInInspection);
            return this;
        }

        public Builder withConfirmDelete(boolean confirmDelete) {
            toBuild.setConfirmDelete(confirmDelete);
            return this;
        }

        public Builder withMemoryStickMode(boolean memoryStickMode) {
            toBuild.setMemoryStickMode(memoryStickMode);
            return this;
        }

        public Builder withShowAdvancedHints(boolean showAdvancedHints) {
            toBuild.setShowAdvancedHints(showAdvancedHints);
            return this;
        }

        public GeneralPreferences build() {
            return toBuild;
        }
    }
}
