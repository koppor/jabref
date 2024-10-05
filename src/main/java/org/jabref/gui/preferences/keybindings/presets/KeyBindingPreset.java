package org.jabref.gui.preferences.keybindings.presets;

import org.jabref.gui.keyboard.KeyBinding;

import java.util.Map;

public interface KeyBindingPreset {
    String getName();

    Map<KeyBinding, String> getKeyBindings();
}
