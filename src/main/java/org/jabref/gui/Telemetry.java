package org.jabref.gui;

import org.jabref.logic.util.BuildInfo;
import org.jabref.preferences.TelemetryPreferences;

import java.util.Optional;

public class Telemetry {
    private Telemetry() {}

    public static Optional<TelemetryClient> getTelemetryClient() {
        return Optional.empty();
    }

    private static void start(TelemetryPreferences telemetryPreferences, BuildInfo buildInfo) {}

    public static void shutdown() {
        getTelemetryClient().ifPresent(client -> {});
    }
}
