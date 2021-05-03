package org.jabref.logic.openoffice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.logic.oostyle.OOFormattedText;

import com.sun.star.beans.Property;
import com.sun.star.beans.PropertyAttribute;
import com.sun.star.beans.PropertyState;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XMultiPropertyStates;
import com.sun.star.beans.XPropertySet;
import com.sun.star.beans.XPropertySetInfo;
import com.sun.star.beans.XPropertyState;
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
    private static final String CHAR_LOCALE = "CharLocale";

    /*
     *  SUPERSCRIPT_VALUE and SUPERSCRIPT_HEIGHT are percents of the normal character height
     */
    private static final short SUPERSCRIPT_VALUE = (short) 33;
    private static final short SUBSCRIPT_VALUE = (short) -10;
    private static final byte SUPERSCRIPT_HEIGHT = (byte) 58;
    private static final byte SUBSCRIPT_HEIGHT = (byte) 58;


    private static final String TAG_NAME_REGEXP = "(?:b|i|em|tt|smallcaps|sup|sub|u|s|p|font|locale)";
    private static final String ATTRIBUTE_NAME_REGEXP = "(?:class|value)";
    private static final String ATTRIBUTE_VALUE_REGEXP = "\"([^\"]*)\"";
    private static final Pattern HTML_TAG =
        Pattern.compile("<(/" + TAG_NAME_REGEXP + ")>"
                        + "|"
                        + "<(" + TAG_NAME_REGEXP + ")"
                        + "((?:\\s+(" + ATTRIBUTE_NAME_REGEXP + ")=" + ATTRIBUTE_VALUE_REGEXP + ")*)"
                        + ">");
    private static final Pattern ATTRIBUTE_PATTERN =
        Pattern.compile("\\s+(" + ATTRIBUTE_NAME_REGEXP + ")=" + ATTRIBUTE_VALUE_REGEXP);

    private OOUtil() {
        // Just to hide the public constructor
    }

    private static Map<String, String> parseAttributes(String s) {
        Map<String, String> res = new HashMap<>();
        if (s == null) {
            return res;
        }
        Matcher m = OOUtil.ATTRIBUTE_PATTERN.matcher(s);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);
            res.put(key, value);
        }
        return res;
    }

    /**
     * Insert a text with formatting indicated by HTML-like tags, into
     * a text at the position given by a cursor.
     *
     * Limitation: understands no entitites. It does not receive any either, unless
     * the user provides it.
     *
     * To limit the damage (and for a bit of self-documenting)
     * {@code TAG_NAME_REGEXP} and {@code ATTRIBUTE_NAME_REGEXP} explicitly lists the
     * values we care about.
     *
     *
     * Notable changes w.r.t insertOOFormattedTextAtCurrentLocation:
     *
     * - new tags:
     *   - &lt;locale value=""&gt;
     *     - earlier was applied from code
     *   - &lt;font class=""&gt;
     *     - earlier was applied from code, for "CitationCharacterFormat"
     *   - &lt;tt&gt;
     *     - earlier: known, but ignored (no effect)
     *     - now: equivalent to &lt;font class="Example"&gt;
     *
     * - closing tags try to properly restore state instead of dictating
     *   an "off" state.
     *
     * - The practical consequence: the user can now format the
     *   citation mark (it is enough to format its start) and the
     *   properties not dictated by the style markup are preserved.
     *
     *    A comparable example: a style with
     *    ReferenceParagraphFormat="JR_bibentry"
     *    JR_bibentry in LibreOffice, paragraph style prescribes "bold" font.
     *    LAYOUT only mentions bold around year.
     *    Which parts of the bibliography entries should come out as bold?
     *
     * @param documentConnection
     * @param position   The cursor giving the insert location. Not modified.
     * @param ootext     The marked-up text to insert.
     * @throws WrappedTargetException
     * @throws PropertyVetoException
     * @throws UnknownPropertyException
     * @throws IllegalArgumentException
     */
    public static void insertOOFormattedTextAtCurrentLocation2(DocumentConnection documentConnection,
                                                               XTextCursor position,
                                                               OOFormattedText ootext)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException {

        final boolean debugThisFun = false;
        if (debugThisFun) {
            System.out.println(ootext.asString());
        }

        String lText = OOFormattedText.toString(ootext);

        XText text = position.getText();
        XTextCursor cursor = text.createTextCursorByRange(position);
        cursor.collapseToEnd();

        Stack<Formatter> formatters = new Stack<>();
        Stack<String> expectEnd = new Stack<>();

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


            String fullTag = m.group();
            String endTagName = m.group(1);
            String startTagName = m.group(2);
            String attributeListPart = m.group(3);
            boolean isStartTag = (endTagName == null) || "".equals(endTagName);
            String tagName = isStartTag ? startTagName : endTagName;
            Objects.requireNonNull(tagName);

            Map<String, String> attributes = parseAttributes(attributeListPart);

            // Handle tags:
            switch (tagName) {
            case "b":
                formatters.push(Bold());
                expectEnd.push("/" + tagName);
                break;
            case "i":
            case "em":
                formatters.push(Italic());
                expectEnd.push("/" + tagName);
                break;
            case "smallcaps":
                formatters.push(SmallCaps());
                expectEnd.push("/" + tagName);
                break;
            case "sup":
                formatters.push(SuperScript());
                expectEnd.push("/" + tagName);
                break;
            case "sub":
                formatters.push(SubScript());
                expectEnd.push("/" + tagName);
                break;
            case "u":
                formatters.push(Underline());
                expectEnd.push("/" + tagName);
                break;
            case "s":
                formatters.push(Strikeout());
                expectEnd.push("/" + tagName);
                break;
            case "/p":
                // nop
                break;
            case "p":
                String cls = attributes.get("class");
                // <p class="standard">
                OOUtil.insertParagraphBreak(text, cursor);
                cursor.collapseToEnd();
                if (cls != null && !cls.equals("")) {
                    try {
                        DocumentConnection.setParagraphStyle(cursor, cls);
                    } catch (UndefinedParagraphFormatException ex) {
                        // ignore silently
                    }
                }
                break;
            case "font":
                formatters.push(SetCharStyle(attributes.get("class")));
                expectEnd.push("/" + tagName);
                break;
            case "tt":
                // Note: "Example" names a character style in LibreOffice.
                formatters.push(SetCharStyle("Example"));
                expectEnd.push("/" + tagName);
                break;
            case "locale":
                // <locale value="zxx">
                // <locale value="en-US">
                formatters.push(SetLocale(attributes.get("value")));
                expectEnd.push("/" + tagName);
                break;
            case "/b":
            case "/i":
            case "/em":
            case "/tt":
            case "/smallcaps":
            case "/sup":
            case "/sub":
            case "/u":
            case "/s":
            case "/font":
            case "/locale":
                formatters.pop().applyEnd(documentConnection, cursor);
                String expected = expectEnd.pop();
                if (!tagName.equals(expected)) {
                    LOGGER.warn(String.format("expected '<%s>', found '<%s>'", expected, tagName));
                }
                break;
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

        if (!expectEnd.empty()) {
            String rest = "";
            for (String s : expectEnd) {
                rest = String.format("<%s>", s) + rest;
            }
            LOGGER.warn(String.format("insertOOFormattedTextAtCurrentLocation2:"
                                      + " expectEnd stack is not empty at the end: %s%n",
                                      rest));
        }
    }

    /**
     * Purpose: in some cases we do not want to inherit direct
     *          formatting from the context.
     *
     *          In particular, when filling the bibliography title and body.
     */
    public static void removeDirectFormatting(XTextCursor cursor) {
        // Probably this is the official solution.
        // (Throws no exceptions)
        XMultiPropertyStates mpss = unoQI(XMultiPropertyStates.class, cursor);
        mpss.setAllPropertiesToDefault();
        // Some are not reset by setAllPropertiesToDefault()
        XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
        XPropertyState propertyState = unoQI(XPropertyState.class, cursor);

        try {
            // Special handling
            propertySet.setPropertyValue(CHAR_STYLE_NAME, "Standard");
            propertyState.setPropertyToDefault(CHAR_STRIKEOUT);
        } catch (UnknownPropertyException |
                 PropertyVetoException |
                 IllegalArgumentException |
                 WrappedTargetException ex) {
            LOGGER.warn("exception caught", ex);
        }

        // Only report those we do not yet know about:
        final Set<String> knownToFail = Set.of(
            /*
            "CharStrikeout",
            "CharEscapement",
            "CharEscapementHeight",
            "CharUnderline",
            "CharCrossedOut",
            */

            "ListAutoFormat",
            "ListId",
            "NumberingIsNumber",
            "NumberingLevel",
            "NumberingRules",
            "NumberingStartValue",
            "ParaChapterNumberingLevel",
            "ParaIsNumberingRestart",
            "ParaStyleName");

        // query again, just in case it matters
        propertySet = unoQI(XPropertySet.class, cursor);
        XPropertySetInfo psi = propertySet.getPropertySetInfo();

        // check the result
        for (Property p : psi.getProperties()) {
            if ((p.Attributes & PropertyAttribute.READONLY) != 0) {
                continue;
            }
            try {
                if (isPropertyDefault(cursor, p.Name)) {
                    continue;
                }
            } catch (UnknownPropertyException ex) {
                // impossible
            }
            /*
             * If something turns up here, then it needs to be added to
             * removeDirectFormatting()
             */
            if (knownToFail.contains(p.Name)) {
                continue;
            }
            LOGGER.warn(String.format("OOUtil.removeDirectFormatting failed on '%s'",
                                      p.Name));
        }
    }

    interface Formatter {
        /*
         * Note: apply may be called multiple times, but should pick up old value
         * at its first call.
         */
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException;

        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException;
    }

    /**
     * unoQI : short for UnoRuntime.queryInterface
     *
     * @return A reference to the requested UNO interface type if available,
     *         otherwise null
     */
    private static <T> T unoQI(Class<T> zInterface,
                               Object object) {
        return UnoRuntime.queryInterface(zInterface, object);
    }

    /**
     * Remove direct formatting of propertyName from propertySet.
     *
     * Observation: while
     * XPropertyState.setPropertyToDefault(propertyName) does reset
     * the property, it also has a side effect (probably bug in LO
     * 6.4.6.2) that it also resets other properties.
     *
     * Wrokaround:
     *  (https://forum.openoffice.org/en/forum/viewtopic.php?f=20&t=105117)
     *
     *     Use setPropertyValue with either result from
     *     getPropertyValue to restore the earlier state or with
     *     result from getPropertyDefault. In this case the property
     *     "CharStyleName" has to be handled specially, by mapping the
     *     received value "" to "Standard".  Hopefully other
     *     properties will not need special handling.
     *
     */
    private static void setPropertyToDefault(XTextCursor cursor, String propertyName)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException {
        // setPropertyToDefault("CharWeight") also removes "CharColor"

        XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
        XPropertyState propertyState = unoQI(XPropertyState.class, cursor);

        // CharStrikeout and CharCrossedOut interact: if we deafult one,
        // we default the other as well.
        Set<String> charStrikeOutSet = Set.of(CHAR_STRIKEOUT, "CharCrossedOut");
        Set<String> charEscapementSet = Set.of("CharAutoEscapement",
                                               CHAR_ESCAPEMENT,
                                               CHAR_ESCAPEMENT_HEIGHT);
        Set<String> charUnderlineSet = Set.of(CHAR_UNDERLINE,
                                              "CharUnderlineColor",
                                              "CharUnderlineHasColor");

        Set<String> namesToDefault = new HashSet<>();
        if (charStrikeOutSet.contains(propertyName)) {
            namesToDefault = charStrikeOutSet;
        } else if (charEscapementSet.contains(propertyName)) {
            namesToDefault = charEscapementSet;
        } else if (charUnderlineSet.contains(propertyName)) {
            namesToDefault = charUnderlineSet;
        } else {
            namesToDefault.add(propertyName);
        }

        if (false) {
            // This is what should simply work.
            // However it loses e.g. font color when propertyName is "CharWeight".
            propertyState.setPropertyToDefault(propertyName);
        } else if (false) {
            // Despite the name getPropertyDefault, storing
            // its result by setPropertyValue is NOT equivalent
            // to setPropertyToDefault().
            //
            // setPropertyToDefault() should remove direct ("hard") formatting,
            // setPropertyValue(getPropertyDefault()) can only do that
            // if getPropertyDefault provides a value with the meaning
            // "use whatever comes from the level above".
            //
            Object value = propertyState.getPropertyDefault(propertyName);
            propertySet.setPropertyValue(propertyName, value);
        } else {
            // Try to remove all, then add all directly formatted again,
            // except the one we are removing.
            XPropertySetInfo psi = propertySet.getPropertySetInfo();

            Map<String, Object> ds = new HashMap<>();
            for (Property p : psi.getProperties()) {
                boolean noRestore = (namesToDefault.contains(p.Name)
                                     || ((p.Attributes & PropertyAttribute.READONLY) != 0)
                                     || (isPropertyDefault(cursor, p.Name)));
                if (noRestore) {
                    continue;
                }
                ds.put(p.Name, propertySet.getPropertyValue(p.Name));
            }

            OOUtil.removeDirectFormatting(cursor);
            if (!isPropertyDefault(cursor, propertyName)) {
                LOGGER.warn(String.format("OOUtil.setPropertyToDefault failed (1) on '%s'",
                                          propertyName));
            }

            // restore those saved
            for (Map.Entry<String, Object> e : ds.entrySet()) {
                propertySet.setPropertyValue(e.getKey(), e.getValue());
                if (!isPropertyDefault(cursor, propertyName)) {
                    LOGGER.warn(String.format("OOUtil.setPropertyToDefault failed (2) on '%s' after '%s'",
                                              propertyName, e.getKey()));
                }
            }
            if (!isPropertyDefault(cursor, propertyName)) {
                /*
                 * If something turns up here, then it needs to be added to
                 * removeDirectFormatting()
                 */
                LOGGER.warn(String.format("OOUtil.setPropertyToDefault failed (3) on '%s'",
                                          propertyName));
            }

        }
    }

    /**
     * We rely on property values being ether DIRECT_VALUE or
     * DEFAULT_VALUE (not AMBIGUOUS_VALUE). If the cursor covers a homogeneous region,
     * or is collapsed, then this is true.
     */
    private static boolean isPropertyDefault(XTextCursor cursor, String propertyName)
        throws
        UnknownPropertyException {
        XPropertyState propertyState = unoQI(XPropertyState.class, cursor);
        PropertyState pst = propertyState.getPropertyState(propertyName);
        if (pst == PropertyState.AMBIGUOUS_VALUE) {
            throw new RuntimeException("PropertyState.AMBIGUOUS_VALUE"
                                       + " (expected properties for a homogeneous cursor)");
        }
        return pst == PropertyState.DEFAULT_VALUE;
    }

    private static Optional<Object> getPropertyValue(XTextCursor cursor, String propertyName)
        throws
        UnknownPropertyException,
        WrappedTargetException {
        XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
        if (isPropertyDefault(cursor, propertyName)) {
            final boolean debugThisFun = false;
            if (debugThisFun) {
                if (CHAR_STRIKEOUT.equals(propertyName)) {
                    System.out.printf("OOUtil.getPropertyValue default: '%s' = '%s'%n",
                                      propertyName,
                                      propertySet.getPropertyValue(propertyName));
                }
            }
            return Optional.empty();
        } else {
            return Optional.of(propertySet.getPropertyValue(propertyName));
        }
    }

    private static <T> void setPropertyValue(XTextCursor cursor, String propertyName, Optional<T> value)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException {
        XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
        if (value.isPresent()) {
            propertySet.setPropertyValue(propertyName, value.get());
        } else {
            setPropertyToDefault(cursor, propertyName);
        }
    }

    static class FontWeight implements Formatter {
        final String propertyName = CHAR_WEIGHT;
        Optional<Float> myWeight;
        Optional<Float> oldWeight;
        boolean started;
        FontWeight(Optional<Float> weight) {
            this.myWeight = weight;
            this.started = false;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            final boolean debugThisFun = false;
            if (!started) {
                oldWeight = getPropertyValue(cursor, CHAR_WEIGHT).map(e -> (Float) e);
                XPropertyState propertyState = unoQI(XPropertyState.class, cursor);
                if (debugThisFun) {
                    // float defaultValue = (float) propertyState.getPropertyDefault(propertyName);
                    System.out.printf("FontWeight.start %s%n", oldWeight);
                }
                started = true;
            }

            if (debugThisFun) {
                System.out.printf("FontWeight.apply %s%n", myWeight);
            }
            setPropertyValue(cursor, CHAR_WEIGHT, myWeight);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            final boolean debugThisFun = false;
            if (debugThisFun) {
                System.out.printf("FontWeight.applyEnd %s%n", oldWeight);
            }
            setPropertyValue(cursor, CHAR_WEIGHT, oldWeight);
        }
    }

    static Formatter Bold() {
        return new FontWeight(Optional.of(com.sun.star.awt.FontWeight.BOLD));
    }

    static Formatter FontWeightDefault() {
        return new FontWeight(Optional.of(com.sun.star.awt.FontWeight.NORMAL));
    }

    static class FontSlant implements Formatter {
        Optional<com.sun.star.awt.FontSlant> mySlant;
        Optional<com.sun.star.awt.FontSlant> oldSlant;
        boolean started;

        FontSlant(Optional<com.sun.star.awt.FontSlant> slant) {
            this.mySlant = slant;
            this.oldSlant = Optional.empty();
            this.started = false;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (!started) {
                oldSlant = getPropertyValue(cursor, CHAR_POSTURE).map(e -> (com.sun.star.awt.FontSlant) e);
                started = true;
            }
            setPropertyValue(cursor, CHAR_POSTURE, mySlant);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            setPropertyValue(cursor, CHAR_POSTURE, oldSlant);
        }
    }

    static Formatter Italic() {
        return new FontSlant(Optional.of(com.sun.star.awt.FontSlant.ITALIC));
    }

    static Formatter FontSlantDefault() {
        return new FontSlant(Optional.of(com.sun.star.awt.FontSlant.NONE));
    }

    /*
     * com.sun.star.style.CaseMap
     */
    static class CaseMap implements Formatter {
        Optional<Short> my;
        Optional<Short> old;
        boolean started;

        CaseMap(Optional<Short> value) {
            this.my = value;
            this.started = false;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (!started) {
                old = getPropertyValue(cursor, CHAR_CASE_MAP).map(e -> (short) e);
                started = true;
            }
            setPropertyValue(cursor, CHAR_CASE_MAP, my);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            setPropertyValue(cursor, CHAR_CASE_MAP, old);
        }
    }

    static Formatter SmallCaps() {
        return new CaseMap(Optional.of(com.sun.star.style.CaseMap.SMALLCAPS));
    }

    static Formatter CaseMapDefault() {
        return new CaseMap(Optional.of(com.sun.star.style.CaseMap.NONE));
    }

    static class CharEscapement implements Formatter {
        Optional<Short> myValue;
        Optional<Byte> myHeight;
        boolean relative;

        Optional<Short> oldValue;
        Optional<Byte> oldHeight;
        boolean started;

        /**
         * Probably either both value and height should be present, or
         * neither, because charEscapementSet (or rather, the
         * corresponding mechanics in LibreOffice) links them
         * together.
         *
         * @param relative Make value and height relative to oldHeight and oldValue.
         *        Needed for e^{x_i} e^{x^2} (i.e. sup or sub within sup or sup)
         *
         */
        CharEscapement(Optional<Short> value, Optional<Byte> height, boolean relative) {
            myValue = value;
            myHeight = height;
            this.relative = relative;
            oldValue = Optional.empty();
            oldHeight = Optional.empty();
            started = false;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (!started) {
                oldValue = getPropertyValue(cursor, CHAR_ESCAPEMENT).map(e -> (short) e);
                oldHeight = getPropertyValue(cursor, CHAR_ESCAPEMENT_HEIGHT).map(e -> (byte) e);
                started = true;
            }
            if (relative && myValue.isPresent() && myHeight.isPresent()) {
                double oh = oldHeight.orElse((byte) 100) * 0.01;
                double xHeight = myHeight.orElse((byte) 100) * oh;
                double ov = oldValue.orElse((short) 0);
                double xValue = myValue.orElse((short) 0) * oh + ov;
                setPropertyValue(cursor, CHAR_ESCAPEMENT, Optional.of((short) Math.round(xValue)));
                setPropertyValue(cursor, CHAR_ESCAPEMENT_HEIGHT, Optional.of((byte) Math.round(xHeight)));
            } else {
                setPropertyValue(cursor, CHAR_ESCAPEMENT, myValue);
                setPropertyValue(cursor, CHAR_ESCAPEMENT_HEIGHT, myHeight);
            }
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            setPropertyValue(cursor, CHAR_ESCAPEMENT, oldValue);
            setPropertyValue(cursor, CHAR_ESCAPEMENT_HEIGHT, oldHeight);
        }
    }

    static Formatter SubScript() {
        return new CharEscapement(Optional.of(SUBSCRIPT_VALUE), Optional.of(SUBSCRIPT_HEIGHT), true);
    }

    static Formatter SuperScript() {
        return new CharEscapement(Optional.of(SUPERSCRIPT_VALUE), Optional.of(SUPERSCRIPT_HEIGHT), true);
    }

    static Formatter CharEscapementDefault() {
        return new CharEscapement(Optional.of((short) 0), Optional.of((byte) 100), false);
    }

    /*
     * com.sun.star.awt.FontUnderline
     */
    static class FontUnderline implements Formatter {
        Optional<Short> myValue;
        Optional<Short> oldValue;
        boolean started;

        FontUnderline(Optional<Short> value) {
            this.myValue = value;
            this.oldValue = Optional.empty();
            this.started = false;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (!started) {
                oldValue = getPropertyValue(cursor, CHAR_UNDERLINE).map(e -> (short) e);
                started = true;
            }
            setPropertyValue(cursor, CHAR_UNDERLINE, myValue);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            setPropertyValue(cursor, CHAR_UNDERLINE, oldValue);
        }
    }

    static Formatter Underline() {
        return new FontUnderline(Optional.of(com.sun.star.awt.FontUnderline.SINGLE));
    }

    static FontUnderline FontUnderlineDefault() {
        return new FontUnderline(Optional.of(com.sun.star.awt.FontUnderline.NONE));
    }

    /*
     * com.sun.star.awt.FontStrikeout
     */
    static class FontStrikeout implements Formatter {
        Optional<Short> myValue;
        Optional<Short> oldValue;
        boolean started;

        FontStrikeout(Optional<Short> value) {
            this.myValue = value;
            this.oldValue = Optional.empty();
            this.started = false;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            if (!started) {
                oldValue = getPropertyValue(cursor, CHAR_STRIKEOUT).map(e -> (short) e);
                started = true;
                final boolean debugThisFun = false;
                if (debugThisFun) {
                    System.out.printf("FontStrikeout.apply.start %s%n", oldValue);
                }
            }
            setPropertyValue(cursor, CHAR_STRIKEOUT, myValue);
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            final boolean debugThisFun = false;
            if (debugThisFun) {
                System.out.printf("FontStrikeout.applyEnd %s%n", oldValue);
            }
            setPropertyValue(cursor, CHAR_STRIKEOUT, oldValue);
        }
    }

    static Formatter Strikeout() {
        return new FontStrikeout(Optional.of(com.sun.star.awt.FontStrikeout.SINGLE));
    }

    static Formatter FontStrikeoutDefault() {
        return new FontStrikeout(Optional.of(com.sun.star.awt.FontStrikeout.NONE));
    }

    /*
     *
     */
    static class CharLocale implements Formatter {
        private Optional<Locale> myLocale;
        private Optional<Locale> oldLocale;
        private boolean started;

        CharLocale(Optional<Locale> locale) {
            this.myLocale = locale;
            this.oldLocale = Optional.empty();
            this.started = false;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            try {
                if (!started) {
                    oldLocale = getPropertyValue(cursor, CHAR_LOCALE).map(e -> (Locale) e);
                    started = true;
                }
                setPropertyValue(cursor, CHAR_LOCALE, myLocale);
            } catch (UnknownPropertyException
                     | PropertyVetoException
                     | IllegalArgumentException
                     | WrappedTargetException ex) {
                LOGGER.warn("CharLocale.apply caught", ex);
            }
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            try {
                setPropertyValue(cursor, CHAR_LOCALE, oldLocale);
            } catch (UnknownPropertyException
                     | PropertyVetoException
                     | IllegalArgumentException
                     | WrappedTargetException ex) {
                LOGGER.warn("CharLocale.applyEnd caught", ex);
            }
        }
    }

    /**
     * Locale from string encoding: language, language-country or language-country-variant
     *
     * @param value Empty or null encodes "remove direct formatting"
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

    /**
     * Set a character style known to OO/LO
     *
     *  Optional.empty() encodes (remove direct formatting) both in
     *  myCharStyle and in oldCharStyle.
     *
     */
    static class CharStyleName implements Formatter {
        private Optional<String> myCharStyle;
        private Optional<String> oldCharStyle;
        private boolean started;

        public CharStyleName(Optional<String> charStyle) {
            this.myCharStyle = charStyle;
            this.oldCharStyle = Optional.empty();
            this.started = false;
        }

        @Override
        public void apply(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            try {
                if (!started) {
                    oldCharStyle = getPropertyValue(cursor, CHAR_STYLE_NAME).map(e -> (String) e);
                    started = true;
                }
                setPropertyValue(cursor, CHAR_STYLE_NAME, myCharStyle);
            } catch (UnknownPropertyException
                     | PropertyVetoException
                     | IllegalArgumentException
                     | WrappedTargetException ex) {
                LOGGER.warn("CharStyleName.apply caught", ex);
            }
        }

        @Override
        public void applyEnd(DocumentConnection documentConnection, XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {

            try {
                setPropertyValue(cursor, CHAR_STYLE_NAME, oldCharStyle);
            } catch (UnknownPropertyException
                     | PropertyVetoException
                     | IllegalArgumentException
                     | WrappedTargetException ex) {
                LOGGER.warn("CharStyleName.applyEnd caught", ex);
            }
        }

    }

    /**
     * @param charStyle null or "" encodes "remove direct formatting",
     * not "do nothing".
     */
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

        for (Formatter f : formatters) {
            f.apply(documentConnection, cursor);
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
