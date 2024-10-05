package org.jabref.model.entry.identifier;

import org.jabref.model.entry.field.Field;

import java.net.URI;
import java.util.Optional;

public interface Identifier {

    /**
     * Returns the identifier.
     */
    String getNormalized();

    Field getDefaultField();

    Optional<URI> getExternalURI();
}
