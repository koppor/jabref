package org.jabref.logic.formatter.minifier;

import java.util.Objects;

import org.jabref.logic.cleanup.Formatter;
import org.jabref.logic.l10n.Localization;

public class TruncateFormatter extends Formatter {
    private final int truncateAfter;
    private final String key;

    /**
     * The TruncateFormatter truncates a string after the given index and removes trailing whitespaces.
     *
     * @param truncateIndex truncate a string after this index.
     */
    public TruncateFormatter(final int truncateIndex) {
        truncateAfter = (truncateIndex >= 0) ? truncateIndex : Integer.MAX_VALUE;
        key = "truncate" + truncateAfter;
    }

    @Override
    public String getName() {
        return Localization.lang("Truncate");
    }

    @Override
    public String getKey() {
        return key;
    }

    /**
     * Truncates a string after the given index.
     */
    @Override
    public String format(final String input) {
        Objects.requireNonNull(input);
        final int index = Math.min(truncateAfter, input.length());
        return input.substring(0, index).stripTrailing();
    }

    @Override
    public String getDescription() {
        return Localization.lang("Truncates a string after a given index.");
    }

    @Override
    public String getExampleInput() {
        return "Truncate this sentence.";
    }
}
