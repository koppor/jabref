package org.jabref.model.openoffice.rangeoverlap;

public enum RangeOverlapKind {
    /** They share a boundary */
    TOUCH,

    /** They share some characters */
    OVERLAP,

    /** They cover the same XTextRange */
    EQUAL_RANGE
}

