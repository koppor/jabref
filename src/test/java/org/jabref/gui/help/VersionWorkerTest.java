package org.jabref.gui.help;

import java.io.IOException;
import java.util.List;

import org.jabref.gui.DialogService;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.util.Version;
import org.jabref.preferences.InternalPreferences;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;

class VersionWorkerTest {
    @Test
    void ignoreNewVersionTest() throws IOException {
        InternalPreferences internalPreferences = mock(InternalPreferences.class);
        List<Version> availVersions = List.of(Version.parse("1.0.0"), Version.parse("2.0.0"));
        VersionWorker versionWorker = new VersionWorker(Version.parse("1.0.0"),
                mock(DialogService.class, Answers.RETURNS_DEEP_STUBS),
                mock(TaskExecutor.class),
                internalPreferences);

        try (MockedStatic<Version> version = Mockito.mockStatic(Version.class)) {
            version.when(Version::getAllAvailableVersions)
                   .thenReturn(availVersions);

            System.out.println(versionWorker.getNewVersion().get());
        }


    }
}
