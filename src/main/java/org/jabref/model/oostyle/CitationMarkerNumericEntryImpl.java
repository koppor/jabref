package org.jabref.model.oostyle;

import java.util.Optional;

import org.jabref.model.openoffice.Tuple3;

/*
 * Minimal implementation for CitationMarkerNumericEntry
 */
public class CitationMarkerNumericEntryImpl implements CitationMarkerNumericEntry {

    /*
     * The number encoding "this entry is unresolved" for the constructor.
     */
    public final static int UNRESOLVED_ENTRY_NUMBER = 0;

    private String citationKey;
    private Optional<Integer> num;
    private Optional<OOText> pageInfo;

    public CitationMarkerNumericEntryImpl(String citationKey, int num, Optional<OOText> pageInfo) {
        this.citationKey = citationKey;
        this.num = (num == UNRESOLVED_ENTRY_NUMBER
                    ? Optional.empty()
                    : Optional.of(num));
        this.pageInfo = Citation.normalizePageInfo(pageInfo);
    }

    @Override
    public String getCitationKey() {
        return citationKey;
    }

    @Override
    public Optional<Integer> getNumber() {
        return num;
    }

    @Override
    public Optional<OOText> getPageInfo() {
        return pageInfo;
    }

    public static CitationMarkerNumericEntry from(Tuple3<String, Integer, Optional<OOText>> x) {
        return new CitationMarkerNumericEntryImpl(x.a, x.b, x.c);
    }

    /*
     * pageInfo is String and may be null
     */
    public static CitationMarkerNumericEntry fromRaw(Tuple3<String, Integer, String> x) {
        Optional<OOText> pageInfo = Optional.ofNullable(OOText.fromString(x.c));
        return new CitationMarkerNumericEntryImpl(x.a, x.b, pageInfo);
    }
}
