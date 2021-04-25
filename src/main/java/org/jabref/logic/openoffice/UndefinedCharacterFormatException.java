package org.jabref.logic.openoffice;

/**
 * Exception used to indicate that the plugin attempted to set a character format that is
 * not defined in the current OpenOffice document.
 */
public class UndefinedCharacterFormatException extends Exception {

    private final String formatName;

    public UndefinedCharacterFormatException(String formatName) {
        super();
        this.formatName = formatName;
    }

    public String getFormatName() {
        return formatName;
    }
}
