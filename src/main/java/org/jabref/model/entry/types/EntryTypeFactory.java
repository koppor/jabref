package org.jabref.model.entry.types;

import org.jabref.model.entry.BibEntryType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class EntryTypeFactory {

    private EntryTypeFactory() {}

    /**
     * Checks whether two EntryTypeFactory are equal
     * based on the equality of the type names and on the equality of the required and optional field lists
     *
     * @return returns true if the two compared entry types have the same name and equal required and optional fields
     */
    public static boolean nameAndFieldsAreEqual(BibEntryType type1, BibEntryType type2) {
        if ((type1 == null) && (type2 == null)) {
            return true;
        } else if ((type1 == null) || (type2 == null)) {
            return false;
        } else {
            return Objects.equals(type1.getType(), type2.getType())
                    && Objects.equals(type1.getRequiredFields(), type2.getRequiredFields())
                    && Objects.equals(type1.getOptionalFields(), type2.getOptionalFields())
                    && Objects.equals(
                            type1.getDetailOptionalFields(), type2.getDetailOptionalFields());
        }
    }

    public static boolean isExclusiveBiblatex(EntryType type) {
        return isBiblatex(type) && !isBibtex(type);
    }

    private static boolean isBibtex(EntryType type) {
        return BibtexEntryTypeDefinitions.ALL.stream()
                .anyMatch(bibEntryType -> bibEntryType.getType().equals(type));
    }

    private static boolean isBiblatex(EntryType type) {
        return BiblatexEntryTypeDefinitions.ALL.stream()
                .anyMatch(bibEntryType -> bibEntryType.getType().equals(type));
    }

    public static EntryType parse(String typeName) {
        List<EntryType> types =
                new ArrayList<>(Arrays.<EntryType>asList(StandardEntryType.values()));
        types.addAll(Arrays.<EntryType>asList(IEEETranEntryType.values()));
        types.addAll(Arrays.<EntryType>asList(BiblatexSoftwareEntryType.values()));
        types.addAll(Arrays.<EntryType>asList(BiblatexApaEntryType.values()));
        types.addAll(Arrays.<EntryType>asList(SystematicLiteratureReviewStudyEntryType.values()));

        return types.stream()
                .filter(type -> type.getName().equals(typeName.toLowerCase(Locale.ENGLISH)))
                .findFirst()
                .orElse(new UnknownEntryType(typeName));
    }
}
