package org.jabref.gui.slr;

import org.jabref.model.study.Study;

import java.nio.file.Path;
import java.util.Objects;

public class SlrStudyAndDirectory {
    private final Study study;
    private final Path studyDirectory;

    public SlrStudyAndDirectory(Study study, Path studyDirectory) {
        this.study = Objects.requireNonNull(study);
        this.studyDirectory = Objects.requireNonNull(studyDirectory);
    }

    public Path getStudyDirectory() {
        return studyDirectory;
    }

    public Study getStudy() {
        return study;
    }
}
