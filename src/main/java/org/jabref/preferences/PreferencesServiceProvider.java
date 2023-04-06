package org.jabref.preferences;

import jakarta.inject.Provider;

public class PreferencesServiceProvider implements Provider<PreferencesService> {
    @Override
    public PreferencesService get() {
        return JabRefPreferences.getInstance();
    }
}
