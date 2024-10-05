package org.jabref.logic.integrity;

import org.jabref.model.database.BibDatabase;

import java.util.List;

@FunctionalInterface
public interface DatabaseChecker {
    List<IntegrityMessage> check(BibDatabase database);
}
