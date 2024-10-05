package org.jabref.gui.actions;

import org.jabref.gui.icon.JabRefIcon;
import org.jabref.gui.keyboard.KeyBinding;

import java.util.Optional;

public interface Action {
    default Optional<JabRefIcon> getIcon() {
        return Optional.empty();
    }

    default Optional<KeyBinding> getKeyBinding() {
        return Optional.empty();
    }

    String getText();

    default String getDescription() {
        return "";
    }
}
