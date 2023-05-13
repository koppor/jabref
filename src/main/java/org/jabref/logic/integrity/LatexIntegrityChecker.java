package org.jabref.logic.integrity;

import org.jabref.logic.l10n.Localization;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import uk.ac.ed.ph.snuggletex.*;
import uk.ac.ed.ph.snuggletex.definitions.CoreErrorGroup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LatexIntegrityChecker implements EntryChecker {

    @Override
    public List<IntegrityMessage> check(BibEntry entry) {
        List<IntegrityMessage> results = new ArrayList<>();

        SnuggleEngine engine = new SnuggleEngine();
        SnuggleSession session = engine.createSession();
        session.getConfiguration().setFailingFast(true);

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
                // exclude Dom building errors as this functionality is not used, and we do not have the translations
                if (!errorCode.getErrorGroup().equals(CoreErrorGroup.TDE)) {
                    //todo filter for specific errors
                    String message = Localization.lang(errorCode.getName(), error.getArguments());
                    results.add(new IntegrityMessage(message, entry, field.getKey()));
                    session.getErrors().clear();
                }
            }
        }
        return results;
    }
}



