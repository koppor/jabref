package org.jabref.logic.integrity;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import uk.ac.ed.ph.snuggletex.ErrorCode;
import uk.ac.ed.ph.snuggletex.SnuggleEngine;
import uk.ac.ed.ph.snuggletex.SnuggleInput;
import uk.ac.ed.ph.snuggletex.SnuggleSession;
import uk.ac.ed.ph.snuggletex.definitions.CoreErrorGroup;
import uk.ac.ed.ph.snuggletex.definitions.CorePackageDefinitions;

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
        for (Map.Entry<Field, String> field : entry.getFieldMap().entrySet()) {
            SnuggleInput input = new SnuggleInput(field.getValue());
            try {
                session.parseInput(input);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (!session.getErrors().isEmpty()) {
                ErrorCode code = session.getErrors().get(0).getErrorCode();
                // filter only for tokenization errors
                if (code.getErrorGroup().equals(CoreErrorGroup.TTE)) {
                    String message = CorePackageDefinitions.getPackage().getErrorMessageBundle().getString(code.getName());
                    results.add(new IntegrityMessage(message, entry, field.getKey()));
                    session.getErrors().clear();
                }
            }
        }
        return results;
    }
}



