package org.jabref.model.oostyle;

import java.util.Optional;

/*
 * When sorting citations (in a group), we also consider pageInfo.
 * Otherwise we sort citations as cited keys.
 */
public interface ComparableCitation extends ComparableCitedKey {
    public Optional<OOFormattedText> getPageInfo();
}
