package org.jabref.http.server;

import org.glassfish.hk2.api.Factory;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.preferences.JabRefCliPreferences;

public class PreferencesFactory implements Factory<CliPreferences> {
    @Override
    public CliPreferences provide() {
        return JabRefCliPreferences.getInstance();
    }

    @Override
    public void dispose(CliPreferences instance) {}
}
