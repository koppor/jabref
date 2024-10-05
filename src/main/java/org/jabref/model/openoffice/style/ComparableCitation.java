package org.jabref.model.openoffice.style;

import org.jabref.model.openoffice.ootext.OOText;

import java.util.Optional;

/**
 * When sorting citations (in a group), we also consider pageInfo. Otherwise we sort citations as cited keys.
 */
public interface ComparableCitation extends ComparableCitedKey {
    Optional<OOText> getPageInfo();
}
