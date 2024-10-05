package org.jabref.logic.pdf;

import org.jabref.model.pdf.FileAnnotation;

import java.nio.file.Path;
import java.util.List;

public interface AnnotationImporter {

    List<FileAnnotation> importAnnotations(final Path path);
}
