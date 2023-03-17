package org.jabref.cli;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LauncherTest {

    @Test
    @Disabled
    void startServer() {
        Launcher.LOGGER = LoggerFactory.getLogger(Launcher.class);
        Launcher.startServer();
    }
}
