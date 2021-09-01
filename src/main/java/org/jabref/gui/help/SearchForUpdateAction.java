package org.jabref.gui.help;

import org.jabref.gui.DialogService;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.util.BuildInfo;
import org.jabref.preferences.VersionPreferences;

import javax.inject.Inject;

public class SearchForUpdateAction extends SimpleCommand {

    private final VersionPreferences versionPreferences;
    private final DialogService dialogService;
    private final TaskExecutor taskExecutor;

    @Inject
    BuildInfo buildInfo;

    public SearchForUpdateAction(VersionPreferences versionPreferences, DialogService dialogService, TaskExecutor taskExecutor) {
        this.versionPreferences = versionPreferences;
        this.dialogService = dialogService;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void execute() {
        new VersionWorker(buildInfo.version, versionPreferences.getIgnoredVersion(), dialogService, taskExecutor)
                .checkForNewVersionAsync();
    }
}
