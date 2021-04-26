package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.logic.oostyle.OOFormattedText;

import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.Locale;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.ControlCharacter;
import com.sun.star.text.XText;
import com.sun.star.text.XTextCursor;
import com.sun.star.uno.UnoRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for processing OO Writer documents.
 */
@AllowedToUseAwt("Requires AWT for changing document properties")
public class OOUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(OOUtil.class);

    private static final String CHAR_STRIKEOUT = "CharStrikeout";
    private static final String CHAR_UNDERLINE = "CharUnderline";
    private static final String PARA_STYLE_NAME = "ParaStyleName";
    private static final String CHAR_CASE_MAP = "CharCaseMap";
    private static final String CHAR_POSTURE = "CharPosture";
    private static final String CHAR_WEIGHT = "CharWeight";
    private static final String CHAR_ESCAPEMENT_HEIGHT = "CharEscapementHeight";
    private static final String CHAR_ESCAPEMENT = "CharEscapement";
    private static final String CHAR_STYLE_NAME = "CharStyleName";

    public enum Formatting {
        BOLD,
        ITALIC,
        SMALLCAPS,
        SUPERSCRIPT,
        SUBSCRIPT,
        UNDERLINE,
        STRIKEOUT,
        MONOSPACE
    }

    private static final Pattern HTML_TAG =
        Pattern.compile("</?[a-z]+>|<(p|font|locale)\\s+(class|value)=\"([^\"]+)\">");

    private OOUtil() {
        // Just to hide the public constructor
    }

    /**
     * Insert a text with formatting indicated by HTML-like tags, into a text at the position given by a cursor.
     *
     * @param position   The cursor giving the insert location. Not modified.
     * @param ootext    The marked-up text to insert.
     * @throws WrappedTargetException
     * @throws PropertyVetoException
     * @throws UnknownPropertyException
     * @throws IllegalArgumentException
     */
    public static void insertOOFormattedTextAtCurrentLocation(DocumentConnection documentConnection,
                                                              XTextCursor position,
                                                              OOFormattedText ootext)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException {

        String lText = OOFormattedText.toString(ootext);

        XText text = position.getText();
        XTextCursor cursor = text.createTextCursorByRange(position);

        List<Formatting> formatting = new ArrayList<>();
        Stack<String> charstyleStack = new Stack<>();
        Stack<Locale> localeStack = new Stack<>();
        charstyleStack.push(DocumentConnection.getCharStyle(cursor));
        localeStack.push(DocumentConnection.getCharLocale(cursor));

        // We need to extract formatting. Use a simple regexp search iteration:
        int piv = 0;
        Matcher m = OOUtil.HTML_TAG.matcher(lText);
        while (m.find()) {
            String currentSubstring = lText.substring(piv, m.start());
            if (!currentSubstring.isEmpty()) {
                OOUtil.insertTextAtCurrentLocation(documentConnection,
                                                   cursor,
                                                   currentSubstring,
                                                   formatting,
                                                   Optional.of(charstyleStack.peek()),
                                                   Optional.of(localeStack.peek()));
            }
            String tag = m.group();
            String xtag = m.group(1);
            String xvar = m.group(2);
            String xval = m.group(3);
            // Handle tags:
            if ("<b>".equals(tag)) {
                formatting.add(Formatting.BOLD);
            } else if ("</b>".equals(tag)) {
                formatting.remove(Formatting.BOLD);
            } else if ("<i>".equals(tag) || "<em>".equals(tag)) {
                formatting.add(Formatting.ITALIC);
            } else if ("</i>".equals(tag) || "</em>".equals(tag)) {
                formatting.remove(Formatting.ITALIC);
            } else if ("<tt>".equals(tag)) {
                formatting.add(Formatting.MONOSPACE);
            } else if ("</tt>".equals(tag)) {
                formatting.remove(Formatting.MONOSPACE);
            } else if ("<smallcaps>".equals(tag)) {
                formatting.add(Formatting.SMALLCAPS);
            } else if ("</smallcaps>".equals(tag)) {
                formatting.remove(Formatting.SMALLCAPS);
            } else if ("<sup>".equals(tag)) {
                formatting.add(Formatting.SUPERSCRIPT);
            } else if ("</sup>".equals(tag)) {
                formatting.remove(Formatting.SUPERSCRIPT);
            } else if ("<sub>".equals(tag)) {
                formatting.add(Formatting.SUBSCRIPT);
            } else if ("</sub>".equals(tag)) {
                formatting.remove(Formatting.SUBSCRIPT);
            } else if ("<u>".equals(tag)) {
                formatting.add(Formatting.UNDERLINE);
            } else if ("</u>".equals(tag)) {
                formatting.remove(Formatting.UNDERLINE);
            } else if ("<s>".equals(tag)) {
                formatting.add(Formatting.STRIKEOUT);
            } else if ("</s>".equals(tag)) {
                formatting.remove(Formatting.STRIKEOUT);
            } else if ("</p>".equals(tag)) {
                // nop
            } else if ("p".equals(xtag) || "<p>".equals(tag)) {
                // <p class="standard">
                OOUtil.insertParagraphBreak(text, cursor);
                cursor.collapseToEnd();
                if ("class".equals(xvar) && xval != null && !xval.equals("")) {
                    try {
                        DocumentConnection.setParagraphStyle(cursor, xval);
                    } catch (UndefinedParagraphFormatException ex) {
                        // ignore silently
                    }
                }
            } else if ("font".equals(xtag) || "<font>".equals(tag)) {
                // <font class="standard">
                if ("class".equals(xvar) && xval != null && !xval.equals("")) {
                    charstyleStack.push(xval);
                } else {
                    charstyleStack.push("");
                }
            } else if ("</font>".equals(tag)) {
                charstyleStack.pop();
            } else if ("locale".equals(xtag)) {
                // <locale value="zxx">
                // <locale value="en-US">
                if ("value".equals(xvar) && xval != null && !xval.equals("")) {
                    String[] parts = xval.split("-");
                    String language = (parts.length > 0) ? parts[0] : "";
                    String country = (parts.length > 1) ? parts[1] : "";
                    String variant = (parts.length > 2) ? parts[2] : "";
                    Locale l = new Locale(language, country, variant);
                    localeStack.push(l);
                } else {
                    localeStack.push(localeStack.peek());
                }
            } else if ("</locale>".equals(tag)) {
                localeStack.pop();
            }

            piv = m.end();
        }

        if (piv < lText.length()) {
            OOUtil.insertTextAtCurrentLocation(documentConnection,
                                               cursor,
                                               lText.substring(piv),
                                               formatting,
                                               Optional.of(charstyleStack.peek()),
                                               Optional.of(localeStack.peek()));
        }
    }

    public static void insertParagraphBreak(XText text, XTextCursor cursor) throws IllegalArgumentException {
        text.insertControlCharacter(cursor, ControlCharacter.PARAGRAPH_BREAK, true);
    }

    public static void insertTextAtCurrentLocation(DocumentConnection documentConnection,
                                                   XTextCursor cursor,
                                                   String string,
                                                   List<Formatting> formatting,
                                                   Optional<String> charStyle,
                                                   Optional<Locale> locale)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException {
        XText text = cursor.getText();
        text.insertString(cursor, string, true);
        formatTextInCursor(documentConnection,
                           cursor,
                           formatting,
                           charStyle,
                           locale);

        cursor.collapseToEnd();
    }

    public static void formatTextInCursor(DocumentConnection documentConnection,
                                          XTextCursor cursor,
                                          List<Formatting> formatting,
                                          Optional<String> charStyle,
                                          Optional<Locale> locale)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException {

        // Access the property set of the cursor, and set the currently selected text
        // (which is the string we just inserted) to be bold
        XPropertySet xCursorProps = UnoRuntime.queryInterface(
                XPropertySet.class, cursor);

        if (formatting.contains(Formatting.BOLD)) {
            xCursorProps.setPropertyValue(CHAR_WEIGHT,
                    com.sun.star.awt.FontWeight.BOLD);
        } else {
            xCursorProps.setPropertyValue(CHAR_WEIGHT,
                    com.sun.star.awt.FontWeight.NORMAL);
        }

        if (formatting.contains(Formatting.ITALIC)) {
            xCursorProps.setPropertyValue(CHAR_POSTURE,
                    com.sun.star.awt.FontSlant.ITALIC);
        } else {
            xCursorProps.setPropertyValue(CHAR_POSTURE,
                    com.sun.star.awt.FontSlant.NONE);
        }

        if (formatting.contains(Formatting.SMALLCAPS)) {
            xCursorProps.setPropertyValue(CHAR_CASE_MAP,
                    com.sun.star.style.CaseMap.SMALLCAPS);
        } else {
            xCursorProps.setPropertyValue(CHAR_CASE_MAP,
                    com.sun.star.style.CaseMap.NONE);
        }

        // TODO: the <monospace> tag doesn't work
        /*
        if (formatting.contains(Formatting.MONOSPACE)) {
            xCursorProps.setPropertyValue("CharFontPitch",
                            com.sun.star.awt.FontPitch.FIXED);
        }
        else {
            xCursorProps.setPropertyValue("CharFontPitch",
                            com.sun.star.awt.FontPitch.VARIABLE);
        } */

        if (formatting.contains(Formatting.SUBSCRIPT)) {
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT,
                                          (short) -10);
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT_HEIGHT,
                    (byte) 58);
        } else if (formatting.contains(Formatting.SUPERSCRIPT)) {
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT,
                                          (short) 33);
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT_HEIGHT,
                    (byte) 58);
        } else {
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT,
                    (byte) 0);
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT_HEIGHT,
                    (byte) 100);
        }

        if (formatting.contains(Formatting.UNDERLINE)) {
            xCursorProps.setPropertyValue(CHAR_UNDERLINE, com.sun.star.awt.FontUnderline.SINGLE);
        } else {
            xCursorProps.setPropertyValue(CHAR_UNDERLINE, com.sun.star.awt.FontUnderline.NONE);
        }

        if (formatting.contains(Formatting.STRIKEOUT)) {
            xCursorProps.setPropertyValue(CHAR_STRIKEOUT, com.sun.star.awt.FontStrikeout.SINGLE);
        } else {
            xCursorProps.setPropertyValue(CHAR_STRIKEOUT, com.sun.star.awt.FontStrikeout.NONE);
        }

        if (locale.isPresent()) {
            try {
                xCursorProps.setPropertyValue("CharLocale", locale.get());
            } catch (UnknownPropertyException
                     | PropertyVetoException
                     | IllegalArgumentException
                     | WrappedTargetException ex) {
                // silently
            }
        }

        if (charStyle.isPresent() && !charStyle.get().equals("")) {
            if (documentConnection
                .getInternalNameOfCharacterStyle(charStyle.get()).isPresent()) {
                try {
                    xCursorProps.setPropertyValue(CHAR_STYLE_NAME, charStyle.get());
                } catch (UnknownPropertyException
                         | PropertyVetoException
                         | IllegalArgumentException
                         | WrappedTargetException ex) {
                    // silently
                }
            }
            // otherwise: ignore silently. Assume character style was already tested elsewhere.
        }

    }

    /**
     *  Get the text belonging to cursor with up to
     *  charBefore and charAfter characters of context.
     *
     *  The actual context may be smaller than requested.
     *
     *  @param documentConnection
     *  @param cursor
     *  @param charBefore Number of characters requested.
     *  @param charAfter  Number of characters requested.
     *  @param htmlMarkup If true, the text belonging to the
     *                    reference mark is surrounded by bold html tag.
     *
     * TODO: This method could go to OOUtil, except that it refers to
     * DocumentConnection, which is in "gui". DocumentConnection
     * should go to "logic" as well.  As well as many others, ...
     *
     * TODO: there is also a potential distinction between OO-specific
     *  parts and those that could participate in for example a Word
     *  panel. Is the GUI itself dependent on using OO/LO? Hard to
     *  tell.
     */
    public static String getCursorStringWithContext(DocumentConnection documentConnection,
                                                    XTextCursor cursor,
                                                    int charBefore,
                                                    int charAfter,
                                                    boolean htmlMarkup)
        throws
        WrappedTargetException,
        NoDocumentException,
        CreationException {

        String citPart = cursor.getString();

        // extend cursor range left
        int flex = 8;
        for (int i = 0; i < charBefore; i++) {
            try {
                cursor.goLeft((short) 1, true);
                // If we are close to charBefore and see a space,
                // then cut here. Might avoid cutting a word in half.
                if ((i >= (charBefore - flex))
                    && Character.isWhitespace(cursor.getString().charAt(0))) {
                    break;
                }
            } catch (IndexOutOfBoundsException ex) {
                LOGGER.warn("Problem going left", ex);
            }
        }

        int lengthWithBefore = cursor.getString().length();
        int addedBefore = lengthWithBefore - citPart.length();

        cursor.collapseToStart();
        for (int i = 0; i < (charAfter + lengthWithBefore); i++) {
            try {
                cursor.goRight((short) 1, true);
                if (i >= ((charAfter + lengthWithBefore) - flex)) {
                    String strNow = cursor.getString();
                    if (Character.isWhitespace(strNow.charAt(strNow.length() - 1))) {
                        break;
                    }
                }
            } catch (IndexOutOfBoundsException ex) {
                LOGGER.warn("Problem going right", ex);
            }
        }

        String result = cursor.getString();
        if (htmlMarkup) {
            result = (result.substring(0, addedBefore)
                      + "<b>" + citPart + "</b>"
                      + result.substring(lengthWithBefore));
        }
        return result.trim();
    }

}
