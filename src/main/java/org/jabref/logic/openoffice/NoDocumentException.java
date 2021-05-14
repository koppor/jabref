package org.jabref.logic.openoffice;

public class NoDocumentException extends Exception {

    public NoDocumentException(String message) {
        super(message);
    }

    public NoDocumentException() {
        super("Not connected to a document");
    }
}
