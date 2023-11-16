package org.jabref.logic.importer.util;

import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.identifier.*;
import org.jabref.model.strings.StringUtil;

import java.util.Objects;
import java.util.Optional;

public class IdentifierParser {
    private final BibEntry entry;

    public IdentifierParser(BibEntry entry) {
        Objects.requireNonNull(entry);
        this.entry = entry;
    }

    public Optional<? extends Identifier> parse(Field field) {
        String fieldValue = entry.getField(field).orElse("");

        if (StringUtil.isBlank(fieldValue)) {
            return Optional.empty();
        }

        if (StandardField.DOI == field) {
            return DOI.parse(fieldValue);
        } else if (StandardField.ISBN == field) {
            return ISBN.parse(fieldValue);
        } else if (StandardField.EPRINT == field) {
            return parseEprint(fieldValue);
        } else if (StandardField.MR_NUMBER == field) {
            return MathSciNetId.parse(fieldValue);
        }

        return Optional.empty();
    }

    private Optional<? extends Identifier> parseEprint(String eprint) {
        Optional<String> eprintTypeOpt = entry.getField(StandardField.EPRINTTYPE);
        Optional<String> archivePrefixOpt = entry.getField(StandardField.ARCHIVEPREFIX);

        String eprintType = eprintTypeOpt.or(() -> archivePrefixOpt).orElse("");
        if ("arxiv".equalsIgnoreCase(eprintType)) {
            return ArXivIdentifier.parse(eprint);
        } else if ("ark".equalsIgnoreCase(eprintType)) {
            return ARK.parse(eprint);
        }

        return Optional.empty();
    }
}
