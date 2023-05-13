package org.jabref.logic.formatter.bibtexfields;

import java.text.Normalizer;
import java.util.Objects;

import org.jabref.logic.cleanup.Formatter;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.layout.LayoutFormatter;
import org.jabref.logic.util.strings.HTMLUnicodeConversionMaps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The inverse operation is {@link LatexToUnicodeFormatter}.
 */
public class UnicodeToLatexFormatter extends Formatter implements LayoutFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnicodeToLatexFormatter.class);

    @Override
    public String format(String text) {
        if (Objects.requireNonNull(text).isEmpty()) {
            return text;
        }

        // normalize the unicode characters to cover more cases
        String result = Normalizer.normalize(text, Normalizer.Form.NFC);

        // Convert single Unicode characters to LaTeX commands
        boolean changed = false;
        StringBuilder stringBuilder = new StringBuilder();
        for (char c : text.toCharArray()) {
            String lookup = HTMLUnicodeConversionMaps.UNICODE_LATEX_CONVERSION_MAP.get(c);
            if (lookup == null) {
                stringBuilder.append(c);
            } else {
                stringBuilder.append(lookup);
                changed = true;
            }
        }
        if (changed) {
            result = stringBuilder.toString();
        }

        // Combining accents
        StringBuilder sb = new StringBuilder();
        boolean consumed = false;
        for (int i = 0; i <= (result.length() - 2); i++) {
            if (!consumed && (i < (result.length() - 1))) {
                int cpCurrent = result.codePointAt(i);
                Integer cpNext = result.codePointAt(i + 1);
                String code = HTMLUnicodeConversionMaps.ESCAPED_ACCENTS.get(cpNext);
                if (code == null) {
                    // skip next index to avoid reading surrogate as a separate char
                    if (!Character.isBmpCodePoint(cpCurrent)) {
                        i++;
                    }
                    sb.appendCodePoint(cpCurrent);
                } else {
                    sb.append("{\\").append(code).append('{').append((char) cpCurrent).append("}}");
                    consumed = true;
                }
            } else {
                consumed = false;
            }
        }
        if (!consumed) {
            sb.append((char) result.codePointAt(result.length() - 1));
        }
        result = sb.toString();

        // Check if any symbols is not converted
        for (int i = 0; i <= (result.length() - 1); i++) {
            int cp = result.codePointAt(i);
            if (cp >= 129) {
                LOGGER.warn("Unicode character not converted: {}", cp);
            }
        }
        return result;
    }

    @Override
    public String getDescription() {
        return Localization.lang("Converts Unicode characters to LaTeX encoding.");
    }

    @Override
    public String getExampleInput() {
        return "Mönch";
    }

    @Override
    public String getName() {
        return Localization.lang("Unicode to LaTeX");
    }

    @Override
    public String getKey() {
        return "unicode_to_latex";
    }
}
