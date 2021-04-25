package org.jabref.logic.openoffice;

/**
 * Exception used to indicate that the plugin attempted to set a character format that is
 * not defined in the current OpenOffice document.
 */
public class CreationException extends Exception {

    public CreationException(String message) {
        super(message);
    }

}
