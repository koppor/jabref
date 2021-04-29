package org.jabref.logic.openoffice;

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

    private static final Pattern HTML_TAG =
        Pattern.compile("</?[a-z]+>|<(p|font|locale)\\s+(class|value)=\"([^\"]+)\">");

    private OOUtil() {
        // Just to hide the public constructor
    }

    /**
     * Insert a text with formatting indicated by HTML-like tags, into
     * a text at the position given by a cursor.
     *
     * @param documentConnection
     * @param position   The cursor giving the insert location. Not modified.
     * @param ootext     The marked-up text to insert.
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
        cursor.collapseToEnd();

        Stack<Formatter> formatters = new Stack<>();

        // We need to extract formatting. Use a simple regexp search iteration:
        int piv = 0;
        Matcher m = OOUtil.HTML_TAG.matcher(lText);
        while (m.find()) {
            String currentSubstring = lText.substring(piv, m.start());
            if (!currentSubstring.isEmpty()) {
                text.insertString(cursor, currentSubstring, true);
            }
            OOUtil.formatTextInCursor(documentConnection,
                                      cursor,
                                      formatters);
            cursor.collapseToEnd();
            XPropertySet xCursorProps = UnoRuntime.queryInterface(XPropertySet.class, cursor);
            String tag = m.group();
            String xtag = m.group(1);
            String xvar = m.group(2);
            String xval = m.group(3);
            // Handle tags:
            if ("<b>".equals(tag)) {
                formatters.push(Bold());
            } else if ("</b>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
            } else if ("<i>".equals(tag) || "<em>".equals(tag)) {
                formatters.push(Italic());
            } else if ("</i>".equals(tag) || "</em>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
            } else if ("<tt>".equals(tag)) {
                // nop
            } else if ("</tt>".equals(tag)) {
                // nop
            } else if ("<smallcaps>".equals(tag)) {
                formatters.push(SmallCaps());
            } else if ("</smallcaps>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
            } else if ("<sup>".equals(tag)) {
                formatters.push(SuperScript());
            } else if ("</sup>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
            } else if ("<sub>".equals(tag)) {
                formatters.push(SubScript());
            } else if ("</sub>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
            } else if ("<u>".equals(tag)) {
                formatters.push(Underline());
            } else if ("</u>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
            } else if ("<s>".equals(tag)) {
                formatters.push(Strikeout());
            } else if ("</s>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
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
                if ("class".equals(xvar)) {
                    formatters.push(SetCharStyle(xval));
                } else {
                    formatters.push(SetCharStyle(null));
                }
            } else if ("</font>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
            } else if ("locale".equals(xtag)) {
                // <locale value="zxx">
                // <locale value="en-US">
                if ("value".equals(xvar)) {
                    formatters.push(SetLocale(xval));
                } else {
                    formatters.push(SetLocale(null));
                }
            } else if ("</locale>".equals(tag)) {
                formatters.pop().applyEnd(documentConnection, xCursorProps);
            }

            piv = m.end();
        }

        if (piv < lText.length()) {
            text.insertString(cursor, lText.substring(piv), true);
        }
        OOUtil.formatTextInCursor(documentConnection,
                                  cursor,
                                  formatters);
        cursor.collapseToEnd();
    }

    interface Formatter {
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException;

        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException;
    }

    static class FontWeight implements Formatter {
        float oldWeight;
        float myWeight;
        FontWeight(float weight) {
            this.myWeight = weight;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            oldWeight = (float) xCursorProps.getPropertyValue(CHAR_WEIGHT);
            xCursorProps.setPropertyValue(CHAR_WEIGHT, myWeight);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            xCursorProps.setPropertyValue(CHAR_WEIGHT, oldWeight);
        }
    }

    static Formatter Bold() {
        return new FontWeight(com.sun.star.awt.FontWeight.BOLD);
    }

    static Formatter FontWeightDefault() {
        return new FontWeight(com.sun.star.awt.FontWeight.NORMAL);
    }

    static class FontSlant implements Formatter {
        com.sun.star.awt.FontSlant oldSlant;
        com.sun.star.awt.FontSlant mySlant;

        FontSlant(com.sun.star.awt.FontSlant slant) {
            this.mySlant = slant;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            oldSlant = (com.sun.star.awt.FontSlant) xCursorProps.getPropertyValue(CHAR_POSTURE);
            xCursorProps.setPropertyValue(CHAR_POSTURE, mySlant);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            xCursorProps.setPropertyValue(CHAR_POSTURE, oldSlant);
        }
    }

    static Formatter Italic() {
        return new FontSlant(com.sun.star.awt.FontSlant.ITALIC);
    }

    static Formatter FontSlantDefault() {
        return new FontSlant(com.sun.star.awt.FontSlant.NONE);
    }

    /*
     * com.sun.star.style.CaseMap
     */
    static class CaseMap implements Formatter {
        short old;
        short my;

        CaseMap(short value) {
            this.my = value;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            old = (short) xCursorProps.getPropertyValue(CHAR_CASE_MAP);
            xCursorProps.setPropertyValue(CHAR_CASE_MAP, my);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            xCursorProps.setPropertyValue(CHAR_CASE_MAP, old);
        }
    }

    static Formatter SmallCaps() {
        return new CaseMap(com.sun.star.style.CaseMap.SMALLCAPS);
    }

    static Formatter CaseMapDefault() {
        return new CaseMap(com.sun.star.style.CaseMap.NONE);
    }

    static class CharEscapement implements Formatter {
        short oldValue;
        byte oldHeight;

        short myValue;
        byte myHeight;

        CharEscapement(short value, byte height) {
            myValue = value;
            myHeight = height;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            oldValue = (short) xCursorProps.getPropertyValue(CHAR_ESCAPEMENT);
            oldHeight = (byte) xCursorProps.getPropertyValue(CHAR_ESCAPEMENT_HEIGHT);

            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT, myValue);
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT_HEIGHT, myHeight);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT, oldValue);
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT_HEIGHT, oldHeight);
        }
    }

    static Formatter SubScript() {
        return new CharEscapement((short) -10, (byte) 58);
    }

    static Formatter SuperScript() {
        return new CharEscapement((short) 33, (byte) 58);
    }

    static Formatter CharEscapementDefault() {
        return new CharEscapement((short) 0, (byte) 100);
    }

    /*
     * com.sun.star.awt.FontUnderline
     */
    static class FontUnderline implements Formatter {
        short oldValue;
        short myValue;

        FontUnderline(short value) {
            this.myValue = value;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            oldValue = (short) xCursorProps.getPropertyValue(CHAR_UNDERLINE);
            xCursorProps.setPropertyValue(CHAR_UNDERLINE, myValue);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            xCursorProps.setPropertyValue(CHAR_UNDERLINE, oldValue);
        }
    }

    static Formatter Underline() {
        return new FontUnderline(com.sun.star.awt.FontUnderline.SINGLE);
    }

    static FontUnderline FontUnderlineDefault() {
        return new FontUnderline(com.sun.star.awt.FontUnderline.NONE);
    }

    /*
     * com.sun.star.awt.FontStrikeout
     */
    static class FontStrikeout implements Formatter {
        short oldValue;
        short myValue;

        FontStrikeout(short value) {
            this.myValue = value;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            oldValue = (short) xCursorProps.getPropertyValue(CHAR_STRIKEOUT);
            xCursorProps.setPropertyValue(CHAR_STRIKEOUT, myValue);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            xCursorProps.setPropertyValue(CHAR_STRIKEOUT, oldValue);
        }
    }

    static Formatter Strikeout() {
        return new FontStrikeout(com.sun.star.awt.FontStrikeout.SINGLE);
    }

    static Formatter FontStrikeoutDefault() {
        return new FontStrikeout(com.sun.star.awt.FontStrikeout.NONE);
    }

    /*
     *
     */
    static class CharLocale implements Formatter {
        private Optional<Locale> myLocale;
        private Optional<Locale> oldLocale;

        CharLocale(Optional<Locale> locale) {
            this.myLocale = locale;
            this.oldLocale = Optional.empty();
        }

        @Override
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (myLocale.isPresent()) {
                try {
                    Locale old = (Locale) xCursorProps.getPropertyValue("CharLocale");
                    oldLocale = Optional.of(old);
                    xCursorProps.setPropertyValue("CharLocale", myLocale.get());
                } catch (UnknownPropertyException
                         | PropertyVetoException
                         | IllegalArgumentException
                         | WrappedTargetException ex) {
                    // silently
                }
            }
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (oldLocale.isPresent()) {
                try {
                    xCursorProps.setPropertyValue("CharLocale", oldLocale.get());
                } catch (UnknownPropertyException
                         | PropertyVetoException
                         | IllegalArgumentException
                         | WrappedTargetException ex) {
                    // silently
                }
            }
        }
    }

    /*
     * Locale from string encoding: language, language-country or language-country-variant
     */
    static Formatter SetLocale(String value) {
        if (value == null || "".equals(value)) {
            return new CharLocale(Optional.empty());
        }

        String[] parts = value.split("-");
        String language = (parts.length > 0) ? parts[0] : "";
        String country = (parts.length > 1) ? parts[1] : "";
        String variant = (parts.length > 2) ? parts[2] : "";
        Locale l = new Locale(language, country, variant);
        return new CharLocale(Optional.of(l));
    }

    /*
     * Set a character style known to OO/LO
     */
    static class CharStyleName implements Formatter {
        private Optional<String> myCharStyle;
        private Optional<String> oldCharStyle;

        public CharStyleName(Optional<String> charStyle) {
            this.myCharStyle = charStyle;
            this.oldCharStyle = Optional.empty();
        }

        @Override
        public void apply(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (myCharStyle.isPresent() && !myCharStyle.get().equals("")) {
                if (documentConnection
                    .getInternalNameOfCharacterStyle(myCharStyle.get()).isPresent()) {
                    try {
                        String old = (String) xCursorProps.getPropertyValue(CHAR_STYLE_NAME);
                        oldCharStyle = Optional.of(old);
                        xCursorProps.setPropertyValue(CHAR_STYLE_NAME, myCharStyle.get());
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

        @Override
        public void applyEnd(DocumentConnection documentConnection, XPropertySet xCursorProps)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (oldCharStyle.isPresent() && !oldCharStyle.get().equals("")) {
                if (documentConnection
                    .getInternalNameOfCharacterStyle(oldCharStyle.get()).isPresent()) {
                    try {
                        xCursorProps.setPropertyValue(CHAR_STYLE_NAME, oldCharStyle.get());
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
    }

    static Formatter SetCharStyle(String charStyle) {
        if (charStyle == null || "".equals(charStyle)) {
            return new CharStyleName(Optional.empty());
        }
        return new CharStyleName(Optional.of(charStyle));
    }

    /**
     * Apply Formatters on the stack.
     *
     * @param documentConnection passed to each Formatter
     * @param cursor Marks the text to format
     * @param formatters Formatters to apply (normally extracted from OOFormattedText)
     */
    public static void formatTextInCursor(DocumentConnection documentConnection,
                                          XTextCursor cursor,
                                          Stack<Formatter> formatters)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException {

        XPropertySet xCursorProps = UnoRuntime.queryInterface(XPropertySet.class, cursor);

        // Set properties we do not want to inherit from the context
        // and are not controlled by formatters.
        if (reset != null) {
            for (Formatter f : reset) {
                f.apply(documentConnection, xCursorProps);
            }
        }

        for (Formatter f : formatters) {
            f.apply(documentConnection, xCursorProps);
        }
    }

    public static void insertParagraphBreak(XText text, XTextCursor cursor)
        throws IllegalArgumentException {
        text.insertControlCharacter(cursor, ControlCharacter.PARAGRAPH_BREAK, true);
    }

    public static void insertTextAtCurrentLocation(XTextCursor cursor,
                                                   String string)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException {
        XText text = cursor.getText();
        text.insertString(cursor, string, true);
        cursor.collapseToEnd();
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
