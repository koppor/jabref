package org.jabref.logic.openoffice;

/**
 * Exception used to indicate that the plugin attempted to set a character format that is
 * not defined in the current OpenOffice document.
 */
public class NoDocumentException extends Exception {

    public NoDocumentException(String message) {
        super(message);
    }

}
