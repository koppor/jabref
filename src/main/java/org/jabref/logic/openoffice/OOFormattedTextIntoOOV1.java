package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jabref.architecture.AllowedToUseAwt;
import org.jabref.model.oostyle.OOFormattedText;

import com.sun.star.awt.FontSlant;
import com.sun.star.awt.FontStrikeout;
import com.sun.star.awt.FontUnderline;
import com.sun.star.awt.FontWeight;
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
import com.sun.star.style.CaseMap;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.text.XText;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.uno.UnoRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interpret OOFormattedText into an OpenOffice or LibreOffice writer
 * document.
 *
 * On the question of what should it understand: apart from what
 * it can do now, probably whatever a CSL style with HTML output
 * emits.
 *
 * Seems slow with approx 40 citations.
 *
 * Idea:
 *
 *   - Use setString, not insertString. This removes character formats,
 *     probably no need for workaround about setPropertyToDefault.
 *   - Calculate current set of character properties in java.
 *   - Use interface allowing to set multiple properties in a single call.
 *
 */
@AllowedToUseAwt("Requires AWT for changing document properties")
public class OOFormattedTextIntoOOV1 {

    private static final Logger LOGGER = LoggerFactory.getLogger(OOFormattedTextIntoOO.class);

    /**
     *  "ParaStyleName" is an OpenOffice Property name.
     */
    private static final String PARA_STYLE_NAME = "ParaStyleName";

    /*
     * Character property names used in multiple locations below.
     */
    private static final String CHAR_ESCAPEMENT_HEIGHT = "CharEscapementHeight";
    private static final String CHAR_ESCAPEMENT = "CharEscapement";
    private static final String CHAR_STYLE_NAME = "CharStyleName";
    private static final String CHAR_UNDERLINE = "CharUnderline";
    private static final String CHAR_STRIKEOUT = "CharStrikeout";

    /*
     *  SUPERSCRIPT_VALUE and SUPERSCRIPT_HEIGHT are percents of the normal character height
     */
    private static final short CHAR_ESCAPEMENT_VALUE_DEFAULT = (short) 0;
    private static final short SUPERSCRIPT_VALUE = (short) 33;
    private static final short SUBSCRIPT_VALUE = (short) -10;
    private static final byte CHAR_ESCAPEMENT_HEIGHT_DEFAULT = (byte) 100;
    private static final byte SUPERSCRIPT_HEIGHT = (byte) 58;
    private static final byte SUBSCRIPT_HEIGHT = (byte) 58;

    private static final String TAG_NAME_REGEXP =
        "(?:b|i|em|tt|smallcaps|sup|sub|u|s|p|span|oo:referenceToPageNumberOfReferenceMark)";
    private static final String ATTRIBUTE_NAME_REGEXP =
        "(?:oo:ParaStyleName|oo:CharStyleName|lang|style|target)";
    private static final String ATTRIBUTE_VALUE_REGEXP = "\"([^\"]*)\"";
    private static final Pattern HTML_TAG =
        Pattern.compile("<(/" + TAG_NAME_REGEXP + ")>"
                        + "|"
                        + "<(" + TAG_NAME_REGEXP + ")"
                        + "((?:\\s+(" + ATTRIBUTE_NAME_REGEXP + ")=" + ATTRIBUTE_VALUE_REGEXP + ")*)"
                        + ">");
    private static final Pattern ATTRIBUTE_PATTERN =
        Pattern.compile("\\s+(" + ATTRIBUTE_NAME_REGEXP + ")=" + ATTRIBUTE_VALUE_REGEXP);

    private OOFormattedTextIntoOOV1() {
        // Just to hide the public constructor
    }

    /**
     * Insert a text with formatting indicated by HTML-like tags, into
     * a text at the position given by a cursor.
     *
     * Limitation: understands no entities. It does not receive any either, unless
     * the user provides it.
     *
     * To limit the damage {@code TAG_NAME_REGEXP} and {@code ATTRIBUTE_NAME_REGEXP}
     * explicitly lists the values we care about.
     *
     * Notable changes w.r.t insertOOFormattedTextAtCurrentLocation:
     *
     * - new tags:
     *
     *   - &lt;span lang="zxx"&gt;
     *     - earlier was applied from code
     *
     *   - &lt;span oo:CharStyleName="CharStylename"&gt;
     *     - earlier was applied from code, for "CitationCharacterFormat"
     *
     *   - &lt;p&gt; start new paragraph
     *     - earlier was applied from code
     *
     *   - &lt;p oo:ParaStyleName="ParStyleName"&gt; : start new paragraph and apply ParStyleName
     *     - earlier was applied from code
     *
     *   - &lt;tt&gt;
     *     - earlier: known, but ignored
     *     - now: equivalent to &lt;span oo:CharStyleName="Example"&gt;
     *   - &lt;oo:referenceToPageNumberOfReferenceMark&gt; (self-closing)
     *
     * - closing tags try to properly restore state instead of dictating
     *   an "off" state.
     *
     * - The practical consequence: the user can format
     *   citation marks (it is enough to format its start) and the
     *   properties not dictated by the style are preserved.
     *
     *    A comparable example: a style with
     *    ReferenceParagraphFormat="JR_bibentry"
     *    JR_bibentry in LibreOffice, paragraph style prescribes "bold" font.
     *    LAYOUT only mentions bold around year.
     *    Which parts of the bibliography entries should come out as bold?
     *
     * @param position   The cursor giving the insert location. Not modified.
     * @param ootext     The marked-up text to insert.
     */
    public static void write(DocumentConnection documentConnection,
                             XTextCursor position,
                             OOFormattedText ootext)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException,
        CreationException {

        XTextDocument doc = documentConnection.asXTextDocument();

        String lText = OOFormattedText.toString(ootext);

        System.out.println(lText);

        XText text = position.getText();
        XTextCursor cursor = text.createTextCursorByRange(position);
        cursor.collapseToEnd();

        Stack<Formatter> formatters = new Stack<>();
        Stack<String> expectEnd = new Stack<>();

        // We need to extract formatting. Use a simple regexp search iteration:
        int piv = 0;
        Matcher m = HTML_TAG.matcher(lText);
        while (m.find()) {

            String currentSubstring = lText.substring(piv, m.start());
            if (!currentSubstring.isEmpty()) {
                text.insertString(cursor, currentSubstring, true);
            }
            formatTextInCursor(cursor, formatters);
            cursor.collapseToEnd();

            String fullTag = m.group();
            String endTagName = m.group(1);
            String startTagName = m.group(2);
            String attributeListPart = m.group(3);
            boolean isStartTag = (endTagName == null) || "".equals(endTagName);
            String tagName = isStartTag ? startTagName : endTagName;
            Objects.requireNonNull(tagName);

            List<Pair<String, String>> attributes = parseAttributes(attributeListPart);

            // Handle tags:
            switch (tagName) {
            case "b":
                formatters.push(setCharWeight(FontWeight.BOLD));
                expectEnd.push("/" + tagName);
                break;
            case "i":
            case "em":
                formatters.push(setCharPosture(FontSlant.ITALIC));
                expectEnd.push("/" + tagName);
                break;
            case "smallcaps":
                formatters.push(setCharCaseMap(CaseMap.SMALLCAPS));
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
                formatters.push(setCharUnderline(FontUnderline.SINGLE));
                expectEnd.push("/" + tagName);
                break;
            case "s":
                formatters.push(setCharStrikeout(FontStrikeout.SINGLE));
                expectEnd.push("/" + tagName);
                break;
            case "/p":
                // nop
                break;
            case "p":
                OOUtil.insertParagraphBreak(text, cursor);
                cursor.collapseToEnd();
                for (Pair<String, String> kv : attributes) {
                    String key = kv.getKey();
                    String value = kv.getValue();
                    switch (key) {
                    case "oo:ParaStyleName":
                        // <p oo:ParaStyleName="Standard">
                        if (value != null && !value.equals("")) {
                            try {
                                setParagraphStyle(cursor, value);
                            } catch (UndefinedParagraphFormatException ex) {
                                // ignore silently
                            }
                        }
                        break;
                    default:
                        LOGGER.warn(String.format("Unexpected attribute '%s' for <%s>", key, tagName));
                        break;
                    }
                }
                break;
            case "oo:referenceToPageNumberOfReferenceMark":
                for (Pair<String, String> kv : attributes) {
                    String key = kv.getKey();
                    String value = kv.getValue();
                    switch (key) {
                    case "target":
                        DocumentConnection
                            .insertReferenceToPageNumberOfReferenceMark(doc, value, cursor);
                        break;
                    default:
                        LOGGER.warn(String.format("Unexpected attribute '%s' for <%s>", key, tagName));
                        break;
                    }
                }
                break;
            case "tt":
                // Note: "Example" names a character style in LibreOffice.
                formatters.push(setCharStyleName("Example"));
                expectEnd.push("/" + tagName);
                break;
            case "span":
                Formatters fs = new Formatters();
                for (Pair<String, String> kv : attributes) {
                    String key = kv.getKey();
                    String value = kv.getValue();
                    switch (key) {
                    case "oo:CharStyleName":
                        // <span oo:CharStyleName="Standard">
                        fs.add(setCharStyleName(value));
                        break;
                    case "lang":
                        // <span lang="zxx">
                        // <span lang="en-US">
                        fs.add(setCharLocale(value));
                        break;
                    case "style":
                        // In general we may need to parse value
                        if (value.equals("font-variant: small-caps")) {
                            fs.add(setCharCaseMap(CaseMap.SMALLCAPS));
                            break;
                        }
                        LOGGER.warn(String.format("Unexpected value %s for attribute '%s' for <%s>",
                                                  value, key, tagName));
                        break;
                    default:
                        LOGGER.warn(String.format("Unexpected attribute '%s' for <%s>", key, tagName));
                        break;
                    }
                }
                formatters.push(fs);
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
            case "/span":
                formatters.pop().applyEnd(cursor);
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
        formatTextInCursor(cursor, formatters);
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

        XMultiPropertyStates mpss = unoQI(XMultiPropertyStates.class, cursor);

        /*
         * Now that we have setAllPropertiesToDefault, check which properties
         * are not set to default and try to correct what we can and seem necessary.
         *
         * Note: tested with LibreOffice : 6.4.6.2
         */

        XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
        XPropertyState propertyState = unoQI(XPropertyState.class, cursor);

        try {
            // Special handling
            propertySet.setPropertyValue(CHAR_STYLE_NAME, "Standard");
            // propertySet.setPropertyValue("CharCaseMap", CaseMap.NONE);
            propertyState.setPropertyToDefault("CharCaseMap");
        } catch (UnknownPropertyException |
                 PropertyVetoException |
                 IllegalArgumentException |
                 WrappedTargetException ex) {
            LOGGER.warn("exception caught", ex);
        }

        mpss.setAllPropertiesToDefault();

        // Only report those we do not yet know about
        final Set<String> knownToFail = Set.of("ListAutoFormat",
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
                throw new RuntimeException("Unexpected UnknownPropertyException");
            }
            if (knownToFail.contains(p.Name)) {
                continue;
            }
            LOGGER.warn(String.format("OOFormattedTextIntoOO.removeDirectFormatting failed on '%s'",
                                      p.Name));
        }
    }

    private static class Pair<K, V> {
        K key;
        V value;
        public Pair(K k, V v) {
            key = k;
            value = v;
        }

        K getKey() {
            return key;
        }

        V getValue() {
            return value;
        }
    }

    private static List<Pair<String, String>> parseAttributes(String s) {
        List<Pair<String, String>> res = new ArrayList<>();
        if (s == null) {
            return res;
        }
        Matcher m = ATTRIBUTE_PATTERN.matcher(s);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);
            res.add(new Pair<String, String>(key, value));
        }
        return res;
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
     * For example setPropertyToDefault("CharWeight") also removes "CharColor"
     *
     * Workaround:
     *  (https://forum.openoffice.org/en/forum/viewtopic.php?f=20&t=105117)
     *
     *     Use setPropertyValue with either result from
     *     getPropertyValue to restore the earlier state or with
     *     result from getPropertyDefault.
     *
     *     In either case the property "CharStyleName" has to be handled
     *     specially, by mapping the received value "" to "Standard".
     *     Hopefully other properties will not need special handling.
     *
     * Well, they do. Some properties interact: setting one to
     * non-default also sets the other to non-default. Fortunately
     * these interactions appear meaningful. For example setting
     * "CharCrossedOut" to non-default also modifies "CharStrikeout".
     * For the strategy applied here (remove all, then restore all
     * except the one we wanted to default) it means we have to avoid
     * restoring those that would, as a side-effect set to non-default
     * what we promised to set to default. I did not investigate
     * potential asymmetries of these interactions, the code below implements
     * symmetric behaviour. This means that for example for
     * propertyName == "CharCrossedOut" we do not restore
     * "CharStrikeout", although the problematic behaviour observed was
     * that restoring "CharCrossedOut" changes "CharStrikeout" from its default.
     *
     */
    private static void setPropertyToDefault(XTextCursor cursor, String propertyName)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException {

        if (false) {
            // This is what should "simply work".
            // However it loses e.g. font color when propertyName is "CharWeight".
            XPropertyState propertyState = unoQI(XPropertyState.class, cursor);
            propertyState.setPropertyToDefault(propertyName);
        } else if (false) {

            // Despite the name getPropertyDefault, storing
            // its result by setPropertyValue is NOT (always) equivalent
            // to setPropertyToDefault().
            //
            // setPropertyToDefault() should remove direct ("hard")
            // formatting.
            //
            // setPropertyValue(getPropertyDefault()) can only do that
            // if getPropertyDefault provides a value with the meaning
            // "use whatever comes from the level above".
            //
            // If the value from getPropertyDefault dictates any
            // concrete value for the property and "whatever comes
            // from the level above" happens to be a different value,
            // then the two behaviours yield different results.

            XPropertyState propertyState = unoQI(XPropertyState.class, cursor);
            Object value = propertyState.getPropertyDefault(propertyName);
            XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
            propertySet.setPropertyValue(propertyName, value);
        } else {

            // Try to remove all, then add all directly formatted again,
            // except the one we are removing. And those that would override
            // what we try to achieve.

            /* https://wiki.openoffice.org/wiki/Documentation/DevGuide/Text/Formatting
             * at the bottom lists interdependent sets:
             *
             * Those marked [-] we probably will not touch, [X] handled, [?] maybe will touch.
             *
             * - [-] ParaRightMargin, ParaLeftMargin, ParaFirstLineIndent, ParaIsAutoFirstLineIndent
             * - [-] ParaTopMargin, ParaBottomMargin
             * - [-] ParaGraphicURL/Filter/Location, ParaBackColor, ParaBackTransparent
             * - [-] ParaIsHyphenation, ParaHyphenationMaxLeadingChars/MaxTrailingChars/MaxHyphens
             * - [-] Left/Right/Top/BottomBorder, Left/Right/Top/BottomBorderDistance, BorderDistance
             * - [-] DropCapFormat, DropCapWholeWord, DropCapCharStyleName
             * - [-] PageDescName, PageNumberOffset, PageStyleName
             *
             * - [-] CharCombineIsOn, CharCombinePrefix, CharCombineSuffix
             * - [-] RubyText, RubyAdjust, RubyCharStyleName, RubyIsAbove
             *
             * - [X] CharStrikeOut, CharCrossedOut
             * - [X] CharEscapement, CharAutoEscapement, CharEscapementHeight
             * - [X] CharUnderline, CharUnderlineColor, CharUnderlineHasColor
             *
             * - [?] CharFontName, CharFontStyleName, CharFontFamily, CharFontPitch
             * - [?] HyperLinkURL/Name/Target, UnvisitedCharStyleName, VisitedCharStyleName
             *
             */

            // CharStrikeout and CharCrossedOut interact: if we default one,
            // we default the other as well.
            Set<String> charStrikeOutSet = Set.of(CHAR_STRIKEOUT,
                                                  "CharCrossedOut");
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
            XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
            XPropertySetInfo psi = propertySet.getPropertySetInfo();

            // Remember those we shall we restore later
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

            // Remove all
            removeDirectFormatting(cursor);

            boolean failed = false;
            if (!isPropertyDefault(cursor, propertyName)) {
                LOGGER.warn(String.format("OOFormattedTextIntoOO.setPropertyToDefault:"
                                          + " removeDirectFormatting failed to reset '%s'",
                                          propertyName));
                failed = true;
            }

            // Restore those saved
            for (Map.Entry<String, Object> e : ds.entrySet()) {
                propertySet.setPropertyValue(e.getKey(), e.getValue());
                if (!failed && !isPropertyDefault(cursor, propertyName)) {
                    LOGGER.warn(String.format("OOFormattedTextIntoOO.setPropertyToDefault('%s')"
                                              + " setPropertyValue('%s') caused loss of default state",
                                              propertyName, e.getKey()));
                    failed = true;
                }
            }

            if (!failed && !isPropertyDefault(cursor, propertyName)) {
                LOGGER.warn(String.format("OOFormattedTextIntoOO.setPropertyToDefault('%s') failed",
                                          propertyName));
            }

        }
    }

    /**
     * We rely on property values being either DIRECT_VALUE or
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

    /**
     * @return Optional.empty() if the property is not directly formatted.
     */
    private static Optional<Object> getPropertyValue(XTextCursor cursor, String propertyName)
        throws
        UnknownPropertyException,
        WrappedTargetException {
        if (isPropertyDefault(cursor, propertyName)) {
            return Optional.empty();
        } else {
            XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
            return Optional.of(propertySet.getPropertyValue(propertyName));
        }
    }

    /**
     * @param value Optional.empty() means instruction to remove direct formatting.
     */
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

    /**
     *  At each tag we adjust the current stack of formatters-to-apply
     *  stack, then run it.
     */
    private interface Formatter {
        /**
         * Note: apply may be called multiple times, but should pick up old value only
         * at its first call.
         */
        public void apply(XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException;

        /**
         *  Closing tags call applyEnd directly, so applyEnd is only called once.
         *
         *  It should restore the state to that seen by the first call to apply.
         *
         */
        public void applyEnd(XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException;
    }

    /**
     * Apply Formatters on the stack.
     *
     * @param cursor Marks the text to format
     * @param formatters Formatters to apply (normally extracted from OOFormattedText)
     */
    private static void formatTextInCursor(XTextCursor cursor,
                                           Stack<Formatter> formatters)
        throws
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException,
        IllegalArgumentException,
        NoSuchElementException {

        for (Formatter f : formatters) {
            f.apply(cursor);
        }
    }

    private static class Formatters implements Formatter {
        List<Formatter> parts;
        public Formatters() {
            parts = new ArrayList<>();
        }

        public void add(Formatter f) {
            parts.add(f);
        }

        @Override
        public void apply(XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            for (Formatter f : parts) {
                f.apply(cursor);
            }
        }

        @Override
        public void applyEnd(XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            for (int i = parts.size() - 1; i >= 0; i--) {
                Formatter f = parts.get(i);
                f.applyEnd(cursor);
            }
        }
    }

    private static class SimpleFormatter<T> implements Formatter {
        final String propertyName;
        Optional<T> myValue;
        Optional<T> oldValue;
        boolean started;

        SimpleFormatter(String propertyName, Optional<T> value) {
            this.propertyName = propertyName;
            this.myValue = value;
            this.oldValue = Optional.empty();
            this.started = false;
        }

        @Override
        public void apply(XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {

            if (!started) {
                oldValue = getPropertyValue(cursor, propertyName).map(e -> (T) e);
                XPropertyState propertyState = unoQI(XPropertyState.class, cursor);
                started = true;
            }

            setPropertyValue(cursor, propertyName, myValue);
        }

        @Override
        public void applyEnd(XTextCursor cursor)
            throws
            UnknownPropertyException,
            PropertyVetoException,
            WrappedTargetException,
            IllegalArgumentException,
            NoSuchElementException {
            setPropertyValue(cursor, propertyName, oldValue);
        }
    }

    private static Formatter setCharWeight(float value) {
        return new SimpleFormatter<Float>("CharWeight", Optional.of(value));
    }

    private static Formatter setCharPosture(FontSlant value) {
        return new SimpleFormatter<FontSlant>("CharPosture", Optional.of(value));
    }

    private static Formatter setCharCaseMap(short value) {
        return new SimpleFormatter<Short>("CharCaseMap", Optional.of(value));
    }

    // com.sun.star.awt.FontUnderline
    private static Formatter setCharUnderline(short value) {
        return new SimpleFormatter<Short>(CHAR_UNDERLINE, Optional.of(value));
    }

    // com.sun.star.awt.FontStrikeout
    private static Formatter setCharStrikeout(short value) {
        return new SimpleFormatter<Short>(CHAR_STRIKEOUT, Optional.of(value));
    }

    // CharStyleName
    private static Formatter setCharStyleName(String value) {
        return new SimpleFormatter<String>(CHAR_STYLE_NAME, Optional.ofNullable(value));
    }

    // Locale
    private static Formatter setCharLocale(Locale value) {
        return new SimpleFormatter<Locale>("CharLocale", Optional.of(value));
    }

    /**
     * Locale from string encoding: language, language-country or language-country-variant
     */
    private static Formatter setCharLocale(String value) {
        if (value == null || "".equals(value)) {
            throw new RuntimeException("setCharLocale \"\" or null");
        }
        String[] parts = value.split("-");
        String language = (parts.length > 0) ? parts[0] : "";
        String country = (parts.length > 1) ? parts[1] : "";
        String variant = (parts.length > 2) ? parts[2] : "";
        Locale l = new Locale(language, country, variant);
        return setCharLocale(l);
    }

    /*
     * SuperScript and SubScript
     */
    private static class CharEscapement implements Formatter {
        Optional<Short> myValue;
        Optional<Byte> myHeight;
        boolean relative;

        Optional<Short> oldValue;
        Optional<Byte> oldHeight;
        boolean started;

        /**
         * @param relative Make value and height relative to oldHeight and oldValue.
         *        Needed for e^{x_i} e^{x^2} (i.e. sup or sub within sup or sup)
         *
         */
        CharEscapement(Optional<Short> value, Optional<Byte> height, boolean relative) {
            this.myValue = value;
            this.myHeight = height;
            this.relative = relative;
            this.oldValue = Optional.empty();
            this.oldHeight = Optional.empty();
            this.started = false;
        }

        @Override
        public void apply(XTextCursor cursor)
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
            if (relative && (myValue.isPresent() || myHeight.isPresent())) {
                double oh = oldHeight.orElse(CHAR_ESCAPEMENT_HEIGHT_DEFAULT) * 0.01;
                double xHeight = myHeight.orElse(CHAR_ESCAPEMENT_HEIGHT_DEFAULT) * oh;
                double ov = oldValue.orElse(CHAR_ESCAPEMENT_VALUE_DEFAULT);
                double xValue = myValue.orElse(CHAR_ESCAPEMENT_VALUE_DEFAULT) * oh + ov;
                setPropertyValue(cursor, CHAR_ESCAPEMENT, Optional.of((short) Math.round(xValue)));
                setPropertyValue(cursor, CHAR_ESCAPEMENT_HEIGHT, Optional.of((byte) Math.round(xHeight)));
            } else {
                setPropertyValue(cursor, CHAR_ESCAPEMENT, myValue);
                setPropertyValue(cursor, CHAR_ESCAPEMENT_HEIGHT, myHeight);
            }
        }

        @Override
        public void applyEnd(XTextCursor cursor)
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

    private static Formatter SubScript() {
        return new CharEscapement(Optional.of(SUBSCRIPT_VALUE), Optional.of(SUBSCRIPT_HEIGHT), true);
    }

    private static Formatter SuperScript() {
        return new CharEscapement(Optional.of(SUPERSCRIPT_VALUE), Optional.of(SUPERSCRIPT_HEIGHT), true);
    }

    public static void setParagraphStyle(XTextCursor cursor,
                                         String parStyle)
        throws
        UndefinedParagraphFormatException {
        XParagraphCursor parCursor = unoQI(XParagraphCursor.class, cursor);
        XPropertySet props = unoQI(XPropertySet.class, parCursor);
        try {
            props.setPropertyValue(PARA_STYLE_NAME, parStyle);
        } catch (UnknownPropertyException
                 | PropertyVetoException
                 | IllegalArgumentException
                 | WrappedTargetException ex) {
            throw new UndefinedParagraphFormatException(parStyle);
        }
    }

}
