package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.logic.layout.Layout;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.Field;
import org.jabref.model.entry.field.UnknownField;

import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.ControlCharacter;
import com.sun.star.text.XText;
import com.sun.star.text.XTextCursor;
import com.sun.star.uno.UnoRuntime;

/**
 * Utility methods for processing OO Writer documents.
 */
@AllowedToUseAwt("Requires AWT for changing document properties")
public class OOUtil {

    private static final String CHAR_STRIKEOUT = "CharStrikeout";
    private static final String CHAR_UNDERLINE = "CharUnderline";
    private static final String PARA_STYLE_NAME = "ParaStyleName";
    private static final String CHAR_CASE_MAP = "CharCaseMap";
    private static final String CHAR_POSTURE = "CharPosture";
    private static final String CHAR_WEIGHT = "CharWeight";
    private static final String CHAR_ESCAPEMENT_HEIGHT = "CharEscapementHeight";
    private static final String CHAR_ESCAPEMENT = "CharEscapement";

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

    private static final Pattern HTML_TAG = Pattern.compile("</?[a-z]+>");

    private static final Field UNIQUEFIER_FIELD = new UnknownField("uniq");

    private OOUtil() {
        // Just to hide the public constructor
    }

    /**
     * Format the reference part of a bibliography entry using a Layout.
     *
     * The label (if any) and paragraph style are not added here.
     *
     * param parStyle   The name of the paragraph style to use.
     *
     *                  Not used here, for now we leave application of
     *                  the paragraph style to the caller.
     *
     * @param layout     The Layout to format the reference with.
     * @param entry      The entry to insert.
     * @param database   The database the entry belongs to.
     * @param uniquefier Uniqiefier letter, if any, to append to the entry's year.
     *
     * @return OOFormattedText suitable for insertOOFormattedTextAtCurrentLocation()
     */
    public static String formatFullReference(Layout layout,
                                             BibEntry entry,
                                             BibDatabase database,
                                             String uniquefier)
        throws
    // TODO: are any of these thrown?
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException {

        // Backup the value of the uniq field, just in case the entry already has it:
        Optional<String> oldUniqVal = entry.getField(UNIQUEFIER_FIELD);

        // Set the uniq field with the supplied uniquefier:
        if (uniquefier == null) {
            entry.clearField(UNIQUEFIER_FIELD);
        } else {
            entry.setField(UNIQUEFIER_FIELD, uniquefier);
        }

        // Do the layout for this entry:
        String formattedText = layout.doLayout(entry, database);

        // Afterwards, reset the old value:
        if (oldUniqVal.isPresent()) {
            entry.setField(UNIQUEFIER_FIELD, oldUniqVal.get());
        } else {
            entry.clearField(UNIQUEFIER_FIELD);
        }
        return formattedText;
    }

    /**
     * Insert a text in OOFormattedText
     * (where character formatting is indicated by HTML-like tags), into
     * at the position given by an {@code XTextCursor}.
     *
     * Process {@code ltext} in chunks between HTML-tags, while
     * updating current formatting state at HTML-tags.
     *
     * @param cursor   The cursor giving the insert location. Not modified.
     * @param lText    The marked-up text to insert.
     * @throws WrappedTargetException
     * @throws PropertyVetoException
     * @throws UnknownPropertyException
     * @throws IllegalArgumentException
     */
    public static void insertOOFormattedTextAtCurrentLocation(XTextCursor cursor,
                                                              String lText)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException {

        XText text = cursor.getText();
        // copy the cursor
        XTextCursor myCursor = cursor.getText().createTextCursorByRange(cursor);

        List<Formatting> formatting = new ArrayList<>();
        // We need to extract formatting. Use a simple regexp search iteration:
        int piv = 0;
        Matcher m = OOUtil.HTML_TAG.matcher(lText);
        while (m.find()) {
            String currentSubstring = lText.substring(piv, m.start());
            if (!currentSubstring.isEmpty()) {
                OOUtil.insertTextAtCurrentLocation(text, myCursor, currentSubstring, formatting);
            }
            String tag = m.group();
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
            }

            piv = m.end();
        }

        if (piv < lText.length()) {
            OOUtil.insertTextAtCurrentLocation(text, myCursor, lText.substring(piv), formatting);
        }
    }

    public static void insertParagraphBreak(XText text, XTextCursor cursor)
        throws
        IllegalArgumentException {
        text.insertControlCharacter(cursor, ControlCharacter.PARAGRAPH_BREAK, true);
        cursor.collapseToEnd();
    }

    /**
     * Set cursor range content to {@code string}, apply {@code
     * formatting} to it, {@code cursor.collapseToEnd()}.
     *
     * Insert {@code string} into {@code text} at {@code cursor}, while removing the content
     * of the cursor's range. The cursor's content is {@code string} now.
     *
     * Apply character direct formatting from {@code formatting}.
     * Features not in {@code formatting} are removed by setting to NONE.
     *
     * Finally: {@code cursor.collapseToEnd();}
     */
    public static void insertTextAtCurrentLocation(XText text, XTextCursor cursor, String string,
                                                   List<Formatting> formatting)
            throws UnknownPropertyException, PropertyVetoException, WrappedTargetException,
            IllegalArgumentException {

        text.insertString(cursor, string, true);
        // Access the property set of the cursor, and set the currently selected text
        // (which is the string we just inserted) to be bold
        XPropertySet xCursorProps = UnoRuntime.queryInterface(XPropertySet.class, cursor);

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

        /*
         * short CharEscapement.
         * specifies the percentage by which to raise/lower superscript/subscript characters.
         * Negative values denote subscripts and positive values superscripts.
         *
         * byte CharEscapementHeight
         * This is the relative height used for subscript or superscript characters in units of percent.
         * The value 100 denotes the original height of the characters.
         *
         * From LibreOffice:
         *     SuperScript: 33 and 58
         *     SubScript:   10 and 58
         *
         */
        if (formatting.contains(Formatting.SUBSCRIPT)) {
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT, (short) -10);
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT_HEIGHT, (byte) 58);
        } else if (formatting.contains(Formatting.SUPERSCRIPT)) {
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT, (short) 33);
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT_HEIGHT, (byte) 58);
        } else {
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT, (short) 0);
            xCursorProps.setPropertyValue(CHAR_ESCAPEMENT_HEIGHT, (byte) 100);
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
        cursor.collapseToEnd();
    }

    public static Object getProperty(Object o, String property)
            throws UnknownPropertyException, WrappedTargetException {
        XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, o);
        return props.getPropertyValue(property);
    }
}
