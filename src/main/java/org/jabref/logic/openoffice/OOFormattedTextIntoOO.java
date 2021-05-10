package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import com.sun.star.beans.XMultiPropertySet;
import com.sun.star.beans.XMultiPropertyStates;
import com.sun.star.beans.XPropertySet;
import com.sun.star.beans.XPropertySetInfo;
import com.sun.star.beans.XPropertyState;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.Locale;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.style.CaseMap;
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
public class OOFormattedTextIntoOO {

    private static final Logger LOGGER = LoggerFactory.getLogger(OOFormattedTextIntoOO.class);

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

    private OOFormattedTextIntoOO() {
        // Just to hide the public constructor
    }

    static class MyPropertyStack {

        // Only try to control these. In particular, leave paragraph
        // properties alone.
        static final Set<String> CONTROLLED_PROPERTIES = Set.of(

            /* Used for SuperScript, SubScript.
             *
             * These three are interdependent: changing one may change others.
             */
            "CharEscapement", "CharEscapementHeight", "CharAutoEscapement",

            /* used for Bold */
            "CharWeight",

            /* Used for Italic */
            "CharPosture",

            /* CharWordMode : If this property is TRUE, the underline
             * and strike-through properties are not applied to white
             * spaces.
             */
            // "CharWordMode",

            /* Used for strikeout. These two are interdependent. */
            "CharStrikeout", "CharCrossedOut",

            /* Used for underline */
            "CharUnderline", // "CharUnderlineColor", "CharUnderlineHasColor",

            // "CharOverline", "CharOverlineColor", "CharOverlineHasColor",

            /* Used for lang="zxx" to shut spellchecker. */
            "CharLocale",

            /* Used for CitationCharacterFormat */
            "CharStyleName", //  "CharStyleNames", "CharAutoStyleName",

            /* Used for <smallcaps> and <span style="font-variant: small-caps"> */
            "CharCaseMap"

            /* HyperLink */
            // "HyperLinkName", /* ??? */
            // "HyperLinkURL",  /* empty for thisDocument */
            // "HyperLinkTarget",
            /* HyperLinkTarget : What comes after '#' (location in doc).
             * Can be a bookmark name.
             */
            /* character style names for unvisited  and visited links */
            // "UnvisitedCharStyleName", "VisitedCharStyleName",
            // "HyperLinkEvents",

            // "CharColor",
            // "CharHighlight",
            // "CharBackColor",  "CharBackTransparent",

            // "CharScaleWidth", /* Allows to reduce font width */

            // "CharKerning", "CharAutoKerning",

            // "CharFontCharSet",
            // "CharFontFamily",
            // "CharFontName",
            // "CharFontPitch",
            // "CharFontStyleName",
            // "CharHeight",

            // "CharHidden",

            /*
             * CharInteropGrabBag : **Since LibreOffice 4.3**
             *
             * Grab bag of character properties, used as a string-any
             * map for interim interop purposes.
             *
             * This property is intentionally not handled by the ODF
             * filter. Any member that should be handled there should
             * be first moved out from this grab bag to a separate
             * property.
             */
            // "CharInteropGrabBag",

            // "CharNoHyphenation",

            // "CharContoured", "CharFlash", "CharRelief",

            // "CharRotation", "CharRotationIsFitToLine",

            // "CharShadingValue", "CharShadowFormat", "CharShadowed",

            /* CharBorder */
            // "CharBorderDistance",
            // "CharBottomBorder",          "CharBottomBorderDistance",
            // "CharTopBorder",             "CharTopBorderDistance",
            // "CharRightBorder",           "CharRightBorderDistance",
            // "CharLeftBorder",            "CharLeftBorderDistance",

            // "IsSkipHiddenText",
            // "IsSkipProtectedText",

            /* TextUserDefinedAttributes: Saved into doc, but lost on Load.
             *
             * They might be usable within a session to invisibly attach info
             * available elsewhere, but costly to get to.
             *
             */
            // "TextUserDefinedAttributes",

            // "WritingMode"

            // "CharCombineIsOn", "CharCombinePrefix", "CharCombineSuffix",

            /* Ruby */
            // "RubyAdjust", "RubyCharStyleName", "RubyIsAbove", "RubyPosition", "RubyText",

            // "CharEmphasis", /* emphasis mark in asian texts */

            /* Asian */
            // "CharWeightAsian",
            // "CharPostureAsian",
            // "CharLocaleAsian",
            // "CharFontCharSetAsian",
            // "CharFontFamilyAsian",
            // "CharFontNameAsian",
            // "CharFontPitchAsian",
            // "CharFontStyleNameAsian",
            // "CharHeightAsian",

            /* Complex */
            // "CharWeightComplex",
            // "CharPostureComplex",
            // "CharLocaleComplex",
            // "CharFontCharSetComplex",
            // "CharFontFamilyComplex",
            // "CharFontNameComplex",
            // "CharFontPitchComplex",
            // "CharFontStyleNameComplex",
            // "CharHeightComplex",

            /*
             * Non-character properties
             */
            // "SnapToGrid",
            // "BreakType"
            //
            // "BorderDistance"
            // "BottomBorder" "BottomBorderDistance"
            // "LeftBorder"   "LeftBorderDistance"
            // "RightBorder"  "RightBorderDistance"
            // "TopBorder"    "TopBorderDistance"
            //
            // "ParaBottomMargin"
            // "ParaLeftMargin"
            // "ParaTopMargin"
            // "ParaRightMargin"
            // "ParaContextMargin"
            //
            // "DropCapCharStyleName"
            // "DropCapFormat"
            // "DropCapWholeWord"
            //
            // "ListAutoFormat" "ListId"
            //
            // "NumberingIsNumber"
            // "NumberingLevel"
            // "NumberingRules"
            // "NumberingStartValue"
            // "NumberingStyleName"
            // "OutlineLevel"
            // "ParaChapterNumberingLevel"
            //
            // "PageDescName" "PageNumberOffset"
            //
            // "Rsid" "ParRsid"
            //
            // "ParaAdjust" "ParaAutoStyleName"
            //
            // "ParaBackColor"
            // "ParaBackGraphic"
            // "ParaBackGraphicFilter"
            // "ParaBackGraphicLocation"
            // "ParaBackGraphicURL"
            // "ParaBackTransparent"
            //
            // "ParaExpandSingleWord"
            // "ParaFirstLineIndent"
            //
            // "ParaIsHyphenation"
            // "ParaHyphenationMaxHyphens"
            // "ParaHyphenationMaxLeadingChars"
            // "ParaHyphenationMaxTrailingChars"
            // "ParaHyphenationNoCaps"
            //
            // "ParaInteropGrabBag"
            // "ParaIsAutoFirstLineIndent"
            // "ParaIsCharacterDistance"
            // "ParaIsConnectBorder"
            // "ParaIsForbiddenRules"
            // "ParaIsHangingPunctuation"
            // "ParaIsNumberingRestart"
            // "ParaKeepTogether"
            // "ParaLastLineAdjust"
            // "ParaLineNumberCount" "ParaLineNumberStartValue"
            // "ParaLineSpacing" "ParaOrphans" "ParaRegisterModeActive"
            // "ParaShadowFormat" "ParaSplit"
            // "ParaStyleName" "ParaTabStops"
            // "ParaUserDefinedAttributes" "ParaVertAlignment"
            // "ParaWidows"

            /**/);
        final int goodSize;
        final Map<String, Integer> goodNameToIndex;
        final String[] goodNames;
        final Stack<ArrayList<Optional<Object>>> layers;

        MyPropertyStack(XTextCursor cursor)
            throws UnknownPropertyException {

            XPropertySet propertySet = unoQI(XPropertySet.class, cursor);
            XPropertySetInfo psi = propertySet.getPropertySetInfo();

            this.goodNameToIndex = new HashMap<>();
            int nextIndex = 0;
            for (Property p : psi.getProperties()) {
                if ((p.Attributes & PropertyAttribute.READONLY) != 0) {
                    continue;
                }
                if (!CONTROLLED_PROPERTIES.contains(p.Name)) {
                    continue;
                }
                this.goodNameToIndex.put(p.Name, nextIndex);
                nextIndex++;
            }

            this.goodSize = nextIndex;

            this.goodNames = new String[goodSize];
            for (Map.Entry<String, Integer> kv : goodNameToIndex.entrySet()) {
                goodNames[ kv.getValue() ] = kv.getKey();
            }

            // XMultiPropertySet.setPropertyValues()
            // requires alphabetically sorted property names.
            Arrays.sort(goodNames);
            for (int i = 0; i < goodSize; i++) {
                this.goodNameToIndex.put(goodNames[i], i);
            }

            /*
            for (int i = 0; i < goodSize; i++) {
                System.out.printf(" '%s'", goodNames[i]);
            }
            System.out.printf("%n");
            */

            // This throws:
            // XPropertyAccess xPropertyAccess = unoQI(XPropertyAccess.class, cursor);
            // if (xPropertyAccess == null) {
            //     throw new RuntimeException("MyPropertyStack: xPropertyAccess is null");
            // }

            // we could use:
            // import com.sun.star.beans.XMultiPropertyStates;
            XMultiPropertyStates mpss = unoQI(XMultiPropertyStates.class, cursor);
            PropertyState[] propertyStates = mpss.getPropertyStates(goodNames);

            XMultiPropertySet mps = unoQI(XMultiPropertySet.class, cursor);
            Object[] initialValues = mps.getPropertyValues(goodNames);

            ArrayList<Optional<Object>> initialValuesOpt = new ArrayList<>(goodSize);

            for (int i = 0; i < goodSize; i++) {
                if (propertyStates[i] == PropertyState.DIRECT_VALUE) {
                    initialValuesOpt.add(Optional.of(initialValues[i]));
                } else {
                    initialValuesOpt.add(Optional.empty());
                }
            }

            this.layers = new Stack<>();
            this.layers.push(initialValuesOpt);
        }

        void pushLayer(List<Pair<String, Object>> settings) {
            ArrayList<Optional<Object>> oldLayer = layers.peek();
            ArrayList<Optional<Object>> newLayer = new ArrayList<>(oldLayer);
            for (Pair<String, Object> kv : settings) {
                String name = kv.getKey();
                Integer i = goodNameToIndex.get(name);
                if (i == null) {
                    LOGGER.warn(String.format("pushLayer: '%s' is not in goodNameToIndex", name));
                    continue;
                }
                Object newValue = kv.getValue();
                newLayer.set(i, Optional.ofNullable(newValue));
            }
            layers.push(newLayer);
        }

        void popLayer() {
            if (layers.size() <= 1) {
                LOGGER.warn("popLayer: underflow");
                return;
            }
            layers.pop();
        }

        void apply(XTextCursor cursor) {
            // removeDirectFormatting(cursor);
            XMultiPropertySet mps = unoQI(XMultiPropertySet.class, cursor);
            XMultiPropertyStates mpss = unoQI(XMultiPropertyStates.class, cursor);
            ArrayList<Optional<Object>> topLayer = layers.peek();
            try {
                // select values to be set
                ArrayList<String> names = new ArrayList<>(goodSize);
                ArrayList<Object> values = new ArrayList<>(goodSize);
                ArrayList<String> delNames = new ArrayList<>(goodSize);
                for (int i = 0; i < goodSize; i++) {
                    if (topLayer.get(i).isPresent()) {
                        names.add(goodNames[i]);
                        values.add(topLayer.get(i).get());
                    } else {
                        delNames.add(goodNames[i]);
                    }
                }
                // namesArray must be alphabetically sorted.
                String[] namesArray = names.toArray(new String[names.size()]);
                String[] delNamesArray = delNames.toArray(new String[names.size()]);
                mpss.setPropertiesToDefault(delNamesArray);
                mps.setPropertyValues(namesArray, values.toArray());
            } catch (UnknownPropertyException ex) {
                LOGGER.warn("UnknownPropertyException in MyPropertyStack.apply");
            } catch (PropertyVetoException ex) {
                LOGGER.warn("PropertyVetoException in MyPropertyStack.apply");
            } catch (IllegalArgumentException ex) {
                LOGGER.warn("IllegalArgumentException in MyPropertyStack.apply");
            } catch (WrappedTargetException ex) {
                LOGGER.warn("WrappedTargetException in MyPropertyStack.apply");
            }
        }

        // Relative CharEscapement needs to know current values.
        Optional<Object> getPropertyValue(String name) {
            if (goodNameToIndex.containsKey(name)) {
                int i = goodNameToIndex.get(name);
                ArrayList<Optional<Object>> topLayer = layers.peek();
                Optional<Object> value = topLayer.get(i);
                return value;
            }
            return Optional.empty();
        }
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

        final boolean useSetString = true;

        String lText = OOFormattedText.toString(ootext);

        // System.out.println(lText);

        XText text = position.getText();
        XTextCursor cursor = text.createTextCursorByRange(position);
        cursor.collapseToEnd();

        MyPropertyStack formatStack = new MyPropertyStack(cursor);

        // Stack<Formatter> formatters = new Stack<>();

        Stack<String> expectEnd = new Stack<>();

        // We need to extract formatting. Use a simple regexp search iteration:
        int piv = 0;
        Matcher m = HTML_TAG.matcher(lText);
        while (m.find()) {

            String currentSubstring = lText.substring(piv, m.start());
            if (!currentSubstring.isEmpty()) {
                if (useSetString) {
                    cursor.setString(currentSubstring);
                } else {
                    text.insertString(cursor, currentSubstring, true);
                }
            }
            formatStack.apply(cursor);
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
                formatStack.pushLayer(setCharWeight(FontWeight.BOLD));
                expectEnd.push("/" + tagName);
                break;
            case "i":
            case "em":
                formatStack.pushLayer(setCharPosture(FontSlant.ITALIC));
                expectEnd.push("/" + tagName);
                break;
            case "smallcaps":
                formatStack.pushLayer(setCharCaseMap(CaseMap.SMALLCAPS));
                expectEnd.push("/" + tagName);
                break;
            case "sup":
                formatStack.pushLayer(SuperScript(formatStack));
                expectEnd.push("/" + tagName);
                break;
            case "sub":
                formatStack.pushLayer(SubScript(formatStack));
                expectEnd.push("/" + tagName);
                break;
            case "u":
                formatStack.pushLayer(setCharUnderline(FontUnderline.SINGLE));
                expectEnd.push("/" + tagName);
                break;
            case "s":
                formatStack.pushLayer(setCharStrikeout(FontStrikeout.SINGLE));
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
                            // LOGGER.warn(String.format("oo:ParaStyleName=\"%s\" found", value));
                            try {
                                DocumentConnection.setParagraphStyle(cursor, value);
                            } catch (UndefinedParagraphFormatException ex) {
                                LOGGER.warn(String.format("oo:ParaStyleName=\"%s\" failed with"
                                                          + " UndefinedParagraphFormatException", value));
                            }
                        } else {
                            LOGGER.warn(String.format("oo:ParaStyleName inherited"));
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
                formatStack.pushLayer(setCharStyleName("Example"));
                expectEnd.push("/" + tagName);
                break;
            case "span":
                List<Pair<String, Object>> settings = new ArrayList<>();
                for (Pair<String, String> kv : attributes) {
                    String key = kv.getKey();
                    String value = kv.getValue();
                    switch (key) {
                    case "oo:CharStyleName":
                        // <span oo:CharStyleName="Standard">
                        settings.addAll(setCharStyleName(value));
                        break;
                    case "lang":
                        // <span lang="zxx">
                        // <span lang="en-US">
                        settings.addAll(setCharLocale(value));
                        break;
                    case "style":
                        // In general we may need to parse value
                        if (value.equals("font-variant: small-caps")) {
                            settings.addAll(setCharCaseMap(CaseMap.SMALLCAPS));
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
                formatStack.pushLayer(settings);
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
                formatStack.popLayer();
                String expected = expectEnd.pop();
                if (!tagName.equals(expected)) {
                    LOGGER.warn(String.format("expected '<%s>', found '<%s>' after '%s'",
                                              expected,
                                              tagName,
                                              currentSubstring));
                }
                break;
            }

            piv = m.end();
        }

        if (piv < lText.length()) {
            if (useSetString) {
                cursor.setString(lText.substring(piv));
            } else {
                text.insertString(cursor, lText.substring(piv), true);
            }
        }
        formatStack.apply(cursor);
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

    /*
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

    private static List<Pair<String, Object>> setCharWeight(float value) {
        List<Pair<String, Object>> settings = new ArrayList<>();
        settings.add(new Pair("CharWeight", (Float) value));
        return settings;
    }

    private static List<Pair<String, Object>> setCharPosture(FontSlant value) {
        List<Pair<String, Object>> settings = new ArrayList<>();
        settings.add(new Pair("CharPosture", (Object) value));
        return settings;
    }

    private static List<Pair<String, Object>> setCharCaseMap(short value) {
        List<Pair<String, Object>> settings = new ArrayList<>();
        settings.add(new Pair("CharCaseMap", (Short) value));
        return settings;
    }

    // com.sun.star.awt.FontUnderline
    private static List<Pair<String, Object>> setCharUnderline(short value) {
        List<Pair<String, Object>> settings = new ArrayList<>();
        settings.add(new Pair(CHAR_UNDERLINE, (Short) value));
        return settings;
    }

    // com.sun.star.awt.FontStrikeout
    private static List<Pair<String, Object>> setCharStrikeout(short value) {
        List<Pair<String, Object>> settings = new ArrayList<>();
        settings.add(new Pair(CHAR_STRIKEOUT, (Short) value));
        return settings;
    }

    // CharStyleName
    private static List<Pair<String, Object>> setCharStyleName(String value) {
        List<Pair<String, Object>> settings = new ArrayList<>();
        if (value != null && value != "") {
            settings.add(new Pair(CHAR_STYLE_NAME, value));
        } else {
            LOGGER.warn("setCharStyleName: received null or empty value");
        }
        return settings;
    }

    // Locale
    private static List<Pair<String, Object>> setCharLocale(Locale value) {
        List<Pair<String, Object>> settings = new ArrayList<>();
        settings.add(new Pair("CharLocale", (Object) value));
        return settings;
    }

    /**
     * Locale from string encoding: language, language-country or language-country-variant
     */
    private static List<Pair<String, Object>> setCharLocale(String value) {
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
    private static List<Pair<String, Object>> CharEscapement(Optional<Short> value,
                                                             Optional<Byte> height,
                                                             boolean relative,
                                                             MyPropertyStack formatStack) {
        List<Pair<String, Object>> settings = new ArrayList<>();
        Optional<Short> oldValue = (formatStack
                                    .getPropertyValue(CHAR_ESCAPEMENT)
                                    .map(e -> (short) e));

        Optional<Byte> oldHeight = (formatStack
                                    .getPropertyValue(CHAR_ESCAPEMENT_HEIGHT)
                                    .map(e -> (byte) e));

        if (relative && (value.isPresent() || height.isPresent())) {
            double oh = oldHeight.orElse(CHAR_ESCAPEMENT_HEIGHT_DEFAULT) * 0.01;
            double xHeight = height.orElse(CHAR_ESCAPEMENT_HEIGHT_DEFAULT) * oh;
            double ov = oldValue.orElse(CHAR_ESCAPEMENT_VALUE_DEFAULT);
            double xValue = value.orElse(CHAR_ESCAPEMENT_VALUE_DEFAULT) * oh + ov;
            short newValue = (short) Math.round(xValue);
            byte newHeight = (byte) Math.round(xHeight);
            if (value.isPresent()) {
                settings.add(new Pair(CHAR_ESCAPEMENT, (Short) newValue));
            }
            if (height.isPresent()) {
                settings.add(new Pair(CHAR_ESCAPEMENT_HEIGHT, (Byte) newHeight));
            }
        } else {
            if (value.isPresent()) {
                settings.add(new Pair(CHAR_ESCAPEMENT, (Short) value.get()));
            }
            if (height.isPresent()) {
                settings.add(new Pair(CHAR_ESCAPEMENT_HEIGHT, (Byte) height.get()));
            }
        }
        return settings;
    }

    private static List<Pair<String, Object>> SubScript(MyPropertyStack formatStack) {
        return CharEscapement(Optional.of(SUBSCRIPT_VALUE),
                              Optional.of(SUBSCRIPT_HEIGHT),
                              true,
                              formatStack);
    }

    private static List<Pair<String, Object>> SuperScript(MyPropertyStack formatStack) {
        return CharEscapement(Optional.of(SUPERSCRIPT_VALUE),
                              Optional.of(SUPERSCRIPT_HEIGHT),
                              true,
                              formatStack);
    }

}
