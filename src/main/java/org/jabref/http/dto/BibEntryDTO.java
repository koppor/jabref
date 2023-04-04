package org.jabref.http.dto;

import java.util.HashMap;
import java.util.Map;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.SharedBibEntryData;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.InternalField;
import org.jabref.model.entry.types.EntryType;

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record BibEntryDTO(SharedBibEntryData sharingMetadata, EntryType type, String citationKey, Map<Field, String> content, String userComments) implements Comparable<BibEntryDTO> {

    public static final Logger LOGGER = LoggerFactory.getLogger(BibEntryDTO.class);

    public BibEntryDTO(BibEntry bibEntry) {
        this(bibEntry.getSharedBibEntryData(),
                bibEntry.getType(),
                bibEntry.getCitationKey().orElse(""),
                removeCitationKey(bibEntry.getFieldMap()),
                bibEntry.getUserComments());
    }

    private static Map<Field, String> removeCitationKey(Map<Field, String> content) {
        Map<Field, String> copy = new HashMap<>(content);
        copy.remove(InternalField.KEY_FIELD);
        return copy;
    }

    @Override
    public int compareTo(BibEntryDTO o) {
        int sharingComparison = sharingMetadata.compareTo(o.sharingMetadata);
        if (sharingComparison != 0) {
            return sharingComparison;
        }
        LOGGER.error("Comparing equal DTOs");
        return 0;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("sharingMetadata", sharingMetadata)
                          .add("content", content)
                          .add("userComments", userComments)
                          .toString();
    }
}
