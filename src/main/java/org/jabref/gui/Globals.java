package org.jabref.gui;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.gui.keyboard.KeyBindingRepository;
import org.jabref.gui.util.DefaultDirectoryMonitor;
import org.jabref.gui.util.DefaultFileUpdateMonitor;
import org.jabref.gui.util.DefaultTaskExecutor;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.logic.protectedterms.ProtectedTermsLoader;
import org.jabref.logic.remote.server.RemoteListenerServerManager;
import org.jabref.logic.util.BuildInfo;
import org.jabref.model.util.DirectoryMonitor;
import org.jabref.model.util.FileUpdateMonitor;
import org.jabref.preferences.PreferencesService;

/**
 * @deprecated try to use {@link StateManager} and {@link org.jabref.preferences.PreferencesService}
 */
@Deprecated
@AllowedToUseAwt("Requires AWT for headless check")
public class Globals {

    /**
     * JabRef version info
     */
    public static final BuildInfo BUILD_INFO = new BuildInfo();

    public static final RemoteListenerServerManager REMOTE_LISTENER = new RemoteListenerServerManager();

    /**
     * Manager for the state of the GUI.
     */
    public static StateManager stateManager = new StateManager();

    public static final TaskExecutor TASK_EXECUTOR = new DefaultTaskExecutor(stateManager);

    /**
     * Each test case initializes this field if required
     */
    public static PreferencesService prefs;

    /**
     * This field is initialized upon startup.
     * <p>
     * Only GUI code is allowed to access it, logic code should use dependency injection.
     */
    public static JournalAbbreviationRepository journalAbbreviationRepository;
    /**
     * This field is initialized upon startup.
     * <p>
     * Only GUI code is allowed to access it, logic code should use dependency injection.
     */
    public static ProtectedTermsLoader protectedTermsLoader;

    private static ClipBoardManager clipBoardManager = null;
    private static KeyBindingRepository keyBindingRepository;

    private static DefaultFileUpdateMonitor fileUpdateMonitor;
    private static DefaultDirectoryMonitor directoryMonitor;

    private Globals() {
    }

    // Key binding preferences
    public static synchronized KeyBindingRepository getKeyPrefs() {
        if (keyBindingRepository == null) {
            keyBindingRepository = prefs.getKeyBindingRepository();
        }
        return keyBindingRepository;
    }

    public static synchronized ClipBoardManager getClipboardManager() {
        if (clipBoardManager == null) {
            clipBoardManager = new ClipBoardManager(prefs);
        }
        return clipBoardManager;
    }

    public static synchronized FileUpdateMonitor getFileUpdateMonitor() {
        if (fileUpdateMonitor == null) {
            fileUpdateMonitor = new DefaultFileUpdateMonitor();
            JabRefExecutorService.INSTANCE.executeInterruptableTask(fileUpdateMonitor, "FileUpdateMonitor");
        }
        return fileUpdateMonitor;
    }

    public static DirectoryMonitor getDirectoryMonitor() {
        if (directoryMonitor == null) {
            directoryMonitor = new DefaultDirectoryMonitor();
        }
        return directoryMonitor;
    }

    public static void shutdownThreadPools() {
        TASK_EXECUTOR.shutdown();
        if (fileUpdateMonitor != null) {
            fileUpdateMonitor.shutdown();
        }

        if (directoryMonitor != null) {
            directoryMonitor.shutdown();
        }

        JabRefExecutorService.INSTANCE.shutdownEverything();
    }
}
