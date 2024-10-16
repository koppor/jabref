package org.jabref.gui.frame;

import org.jabref.logic.UiCommand;

import java.util.List;

/**
 * Specifies an interface that can process either cli or remote commands to the ui
 *
 * See {@link org.jabref.logic.remote.server.RemoteMessageHandler}
 */
public interface UiMessageHandler {

    void handleUiCommands(List<UiCommand> uiCommands);
}
