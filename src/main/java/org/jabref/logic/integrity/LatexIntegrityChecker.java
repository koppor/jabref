package org.jabref.logic.integrity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jabref.logic.l10n.Localization;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;

import uk.ac.ed.ph.snuggletex.ErrorCode;
import uk.ac.ed.ph.snuggletex.InputError;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;
import uk.ac.ed.ph.snuggletex.definitions.CoreErrorCode;
import uk.ac.ed.ph.snuggletex.definitions.CoreErrorGroup;

public class LatexIntegrityChecker implements EntryChecker {

    private static Set<ErrorCode> excludedErrors = new HashSet<>(
            Arrays.asList(
                    CoreErrorCode.TTEG04  // # only allowed inside and command/environment definitions
            )
    );

    @Override
    public List<IntegrityMessage> check(BibEntry entry) {
        List<IntegrityMessage> results = new ArrayList<>();

        SnuggleEngine engine = new SnuggleEngine();
        SnuggleSession session = engine.createSession();
        session.getConfiguration().setFailingFast(true);

        // example of how we could add commands that are not yet present
        // engine.getPackages().get(0).addComplexCommandOneArg("text", false, ALL_MODES,LR, StyleDeclarationInterpretation.NORMALSIZE, null, TextFlowContext.ALLOW_INLINE);

        for (Map.Entry<Field, String> field : entry.getFieldMap().entrySet()) {
            SnuggleInput input = new SnuggleInput(field.getValue());
            try {
                session.parseInput(input);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (!session.getErrors().isEmpty()) {
                InputError error = session.getErrors().get(0);
                ErrorCode errorCode = error.getErrorCode();
                // exclude all Dom building errors as this functionality is not used, and we do not have the translations
                // further exclude individual errors
                if (!errorCode.getErrorGroup().equals(CoreErrorGroup.TDE) && !excludedErrors.contains(errorCode)) {
                    String message = Localization.lang(errorCode.getName(), error.getArguments());
                    results.add(new IntegrityMessage(message, entry, field.getKey()));
                    session.getErrors().clear();
                }
            }
        }
        return results;
    }
}



