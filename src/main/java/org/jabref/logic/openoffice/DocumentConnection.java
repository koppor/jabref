package org.jabref.logic.openoffice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.sun.star.beans.IllegalTypeException;
import com.sun.star.beans.NotRemoveableException;
import com.sun.star.beans.Property;
import com.sun.star.beans.PropertyExistException;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertyContainer;
import com.sun.star.beans.XPropertySet;
import com.sun.star.beans.XPropertySetInfo;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XEnumerationAccess;
import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNameContainer;
import com.sun.star.container.XNamed;
import com.sun.star.document.XDocumentProperties;
import com.sun.star.document.XDocumentPropertiesSupplier;
import com.sun.star.document.XRedlinesSupplier;
import com.sun.star.document.XUndoManager;
import com.sun.star.document.XUndoManagerSupplier;
import com.sun.star.frame.XController;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.lang.DisposedException;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.style.XStyle;
import com.sun.star.style.XStyleFamiliesSupplier;
import com.sun.star.text.ReferenceFieldPart;
import com.sun.star.text.ReferenceFieldSource;
import com.sun.star.text.XBookmarksSupplier;
import com.sun.star.text.XFootnote;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.text.XReferenceMarksSupplier;
import com.sun.star.text.XText;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextRangeCompare;
import com.sun.star.text.XTextSection;
import com.sun.star.text.XTextSectionsSupplier;
import com.sun.star.text.XTextViewCursor;
import com.sun.star.text.XTextViewCursorSupplier;
import com.sun.star.uno.Any;
import com.sun.star.uno.Type;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.InvalidStateException;
import com.sun.star.util.XRefreshable;
import com.sun.star.view.XSelectionSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document-connection related variables.
 */
public class DocumentConnection {
    /** https://wiki.openoffice.org/wiki/Documentation/BASIC_Guide/
     *  Structure_of_Text_Documents#Character_Properties
     *  "CharStyleName" is an OpenOffice Property name.
     */
    private static final String CHAR_STYLE_NAME = "CharStyleName";
    private static final String PARA_STYLE_NAME = "ParaStyleName";
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentConnection.class);


    private XTextDocument xTextDocument;

    public DocumentConnection(XTextDocument xTextDocument) {
        this.xTextDocument = xTextDocument;
    }

    /**
     * Get a reference to the body text of the document
     */
    public XText getXText() {
        return xTextDocument.getText();
    }

    public XTextDocument asXTextDocument() {
        return xTextDocument;
    }

    public Optional<XTextDocument> asOptionalXTextDocument() {
        return Optional.ofNullable(xTextDocument);
    }

    private static Optional<XDocumentProperties> getXDocumentProperties(XTextDocument doc) {
        return (Optional.ofNullable(doc)
                .map(e -> unoQI(XDocumentPropertiesSupplier.class, e))
                .map(e -> e.getDocumentProperties()));
    }

    public static boolean getRecordChanges(XTextDocument doc)
        throws
        UnknownPropertyException,
        WrappedTargetException {
        XPropertySet ps = unoQI(XPropertySet.class, doc);
        // https://wiki.openoffice.org/wiki/Documentation/DevGuide/Text/Settings
        // "Properties of com.sun.star.text.TextDocument"
        if (ps == null) {
            throw new RuntimeException("getRecordChanges: ps is null");
        }
        return (boolean) ps.getPropertyValue("RecordChanges");
    }

    private static XRedlinesSupplier getRedlinesSupplier(XTextDocument doc) {
        return unoQI(XRedlinesSupplier.class, doc);
    }

    public static int countRedlines(XTextDocument doc) {
        XRedlinesSupplier rs = getRedlinesSupplier(doc);
        XEnumerationAccess ea = rs.getRedlines();
        XEnumeration e = ea.createEnumeration();
        if (e == null) {
            return 0;
        } else {
            int count = 0;
            while (e.hasMoreElements()) {
                try {
                    e.nextElement();
                    count++;
                } catch (NoSuchElementException | WrappedTargetException ex) {
                    break;
                }
            }
            return count;
        }
    }

    private static Optional<XStyle> getStyleFromFamily(XTextDocument doc,
                                                       String familyName,
                                                       String styleName)
        throws
        NoSuchElementException,
        WrappedTargetException {

        XStyleFamiliesSupplier fss = unoQI(XStyleFamiliesSupplier.class, doc);
        XNameAccess fs = unoQI(XNameAccess.class, fss.getStyleFamilies());
        XNameContainer xFamily = unoQI(XNameContainer.class, fs.getByName(familyName));

        try {
            Object s = xFamily.getByName(styleName);
            XStyle xs = (XStyle) unoQI(XStyle.class, s);
            return Optional.ofNullable(xs);
        } catch (NoSuchElementException ex) {
            return Optional.empty();
        }
    }

    private static Optional<XStyle> getParagraphStyle(XTextDocument doc, String styleName)
        throws
        NoSuchElementException,
        WrappedTargetException {
        return getStyleFromFamily(doc, "ParagraphStyles", styleName);
    }

    private static Optional<XStyle> getCharacterStyle(XTextDocument doc, String styleName)
        throws
        NoSuchElementException,
        WrappedTargetException {
        return getStyleFromFamily(doc, "CharacterStyles", styleName);
    }

    public static Optional<String> getInternalNameOfParagraphStyle(XTextDocument doc, String name)
        throws
        NoSuchElementException,
        WrappedTargetException {
        return (getParagraphStyle(doc, name)
                .map(e -> e.getName()));
    }

    public static Optional<String> getInternalNameOfCharacterStyle(XTextDocument doc, String name)
        throws
        NoSuchElementException,
        WrappedTargetException {
        return (getCharacterStyle(doc, name)
                .map(e -> e.getName()));
    }

    private static Optional<XSelectionSupplier> getSelectionSupplier(XTextDocument doc) {
        return (Optional.ofNullable(doc)
                .map(e -> getCurrentController(e))
                .flatMap(e -> optUnoQI(XSelectionSupplier.class, e)));
    }

    private static Optional<XModel> asXModel(XTextDocument doc) {
        return optUnoQI(XModel.class, doc);
    }

    private static Optional<XController> getCurrentController(XTextDocument doc) {
        return asXModel(doc).map(e -> e.getCurrentController());
    }

    /**
     * @return may be Optional.empty(), or some type supporting XServiceInfo
     *
     *
     * So far it seems the first thing we have to do
     * with a selection is to decide what do we have.
     *
     * One way to do that is accessing its XServiceInfo interface.
     *
     * Experiments using printServiceInfo with cursor in various
     * positions in the document:
     *
     * With cursor within the frame, in text:
     * *** xserviceinfo.getImplementationName: "SwXTextRanges"
     *      "com.sun.star.text.TextRanges"
     *
     * With cursor somewhere else in text:
     * *** xserviceinfo.getImplementationName: "SwXTextRanges"
     *      "com.sun.star.text.TextRanges"
     *
     * With cursor in comment (also known as "annotation"):
     * *** XSelectionSupplier is OK
     * *** Object initialSelection is null
     * *** xserviceinfo is null
     *
     * With frame selected:
     * *** xserviceinfo.getImplementationName: "SwXTextFrame"
     *     "com.sun.star.text.BaseFrame"
     *     "com.sun.star.text.TextContent"
     *     "com.sun.star.document.LinkTarget"
     *     "com.sun.star.text.TextFrame"
     *     "com.sun.star.text.Text"
     *
     * With cursor selecting an inserted image:
     * *** XSelectionSupplier is OK
     * *** Object initialSelection is OK
     * *** xserviceinfo is OK
     * *** xserviceinfo.getImplementationName: "SwXTextGraphicObject"
     *      "com.sun.star.text.BaseFrame"
     *      "com.sun.star.text.TextContent"
     *      "com.sun.star.document.LinkTarget"
     *      "com.sun.star.text.TextGraphicObject"
     */
    public static Optional<XServiceInfo> getSelectionAsXServiceInfo(XTextDocument doc) {
        return (getSelectionSupplier(doc)
                .flatMap(e -> Optional.ofNullable(e.getSelection()))
                .flatMap(e -> optUnoQI(XServiceInfo.class, e)));
    }

    /**
     * Select the object represented by {@code newSelection} if it is
     * known and selectable in this {@code XSelectionSupplier} object.
     *
     * Presumably result from {@code XSelectionSupplier.getSelection()} is
     * usually OK. It also accepted
     * {@code XTextRange newSelection = documentConnection.xText.getStart();}
     *
     */
    public static void select(XTextDocument doc, Object newSelection) {
        getSelectionSupplier(doc).ifPresent(e -> e.select(newSelection));
    }

    public static Optional<XUndoManager> getXUndoManager(XTextDocument doc) {
        // https://www.openoffice.org/api/docs/common/ref/com/sun/star/document/XUndoManager.html
        return (optUnoQI(XUndoManagerSupplier.class, doc)
                .map(e -> e.getUndoManager()));
    }

    /**
     * Each call to enterUndoContext must be paired by a call to
     * leaveUndoContext, otherwise, the document's undo stack is
     * left in an inconsistent state.
     */
    public static void enterUndoContext(XTextDocument doc, String title) {
        Optional<XUndoManager> um = getXUndoManager(doc);
        if (um.isPresent()) {
            um.get().enterUndoContext(title);
        }
    }

    public static void leaveUndoContext(XTextDocument doc) {
        Optional<XUndoManager> um = getXUndoManager(doc);
        if (um.isPresent()) {
            try {
                um.get().leaveUndoContext();
            } catch (InvalidStateException ex) {
                throw new RuntimeException("leaveUndoContext reported InvalidStateException");
            }
        }
    }

    /**
     * Disable screen refresh.
     *
     * Must be paired with unlockControllers()
     *
     * https://www.openoffice.org/api/docs/common/ref/com/sun/star/frame/XModel.html
     *
     * While there is at least one lock remaining, some
     * notifications for display updates are not broadcasted.
     */
    public static void lockControllers(XTextDocument doc) {
        XModel model = asXModel(doc).orElseThrow(RuntimeException::new);
        model.lockControllers();
    }

    public static void unlockControllers(XTextDocument doc) {
        XModel model = asXModel(doc).orElseThrow(RuntimeException::new);
        model.unlockControllers();
    }

    public static boolean hasControllersLocked(XTextDocument doc) {
        XModel model = asXModel(doc).orElseThrow(RuntimeException::new);
        return model.hasControllersLocked();
    }

    /**
     *  @return True if we cannot reach the current document.
     */
    public static boolean documentConnectionMissing(XTextDocument doc) {

        boolean missing = false;
        if (doc == null) {
            missing = true;
        }

        // Attempt to check document is really available
        if (!missing) {
            try {
                getReferenceMarks(doc);
            } catch (NoDocumentException ex) {
                missing = true;
            }
        }
        return missing;
    }

    public static Optional<XPropertySet> asXPropertySet(XFrame frame) {
        return optUnoQI(XPropertySet.class, frame);
    }

    public static Optional<Object> XPropertySetGetProperty(XPropertySet propertySet, String property)
        throws
        WrappedTargetException {
        Objects.requireNonNull(propertySet);
        Objects.requireNonNull(property);
        try {
            return Optional.ofNullable(propertySet.getPropertyValue(property));
        } catch (UnknownPropertyException e) {
            return Optional.empty();
        }
    }

    /**
     *  @param doc The XTextDocument we want the title for. Null allowed.
     *  @return The title or Optional.empty()
     */
    public static Optional<String> getDocumentTitle(XTextDocument doc) {

        if (doc == null) {
            return Optional.empty();
        }

        XFrame frame = doc.getCurrentController().getFrame();
        Optional<XPropertySet> propertySet = asXPropertySet(frame);
        if (propertySet.isEmpty()) {
            return Optional.empty();
        }

        try {
            Optional<Object> frameTitleObj = XPropertySetGetProperty(propertySet.get(), "Title");
            if (frameTitleObj.isEmpty()) {
                return Optional.empty();
            }
            String frameTitleString = String.valueOf(frameTitleObj.get());
            return Optional.ofNullable(frameTitleString);
        } catch (WrappedTargetException e) {
            LOGGER.warn("Could not get document title", e);
            return Optional.empty();
        }
    }

    public static Optional<XPropertyContainer>
    getUserDefinedPropertiesAsXPropertyContainer(XTextDocument doc) {
        return getXDocumentProperties(doc).map(e -> e.getUserDefinedProperties());
    }

    public static Optional<XPropertySet> asXPropertySet(XPropertyContainer xPropertyContainer) {
        return optUnoQI(XPropertySet.class, xPropertyContainer);
    }

    public static List<String> getPropertyNames(Property[] properties) {
        Objects.requireNonNull(properties);
        return (Arrays.stream(properties)
                .map(p -> p.Name)
                .collect(Collectors.toList()));
    }

    public static List<String> getPropertyNames(XPropertySetInfo propertySetInfo) {
        return getPropertyNames(propertySetInfo.getProperties());
    }

    public static List<String> getPropertyNames(XPropertySet propertySet) {
        return getPropertyNames(propertySet.getPropertySetInfo());
    }

    public static List<String> getPropertyNames(XPropertyContainer propertyContainer) {
        return (asXPropertySet(propertyContainer)
                .map(DocumentConnection::getPropertyNames)
                .orElse(new ArrayList<>()));
    }

    public static List<String> getUserDefinedPropertiesNames(XTextDocument doc) {
        return (getUserDefinedPropertiesAsXPropertyContainer(doc)
                .map(e -> getPropertyNames(e))
                .orElse(new ArrayList<>()));
    }

    /**
     * @param property Name of a custom document property in the
     *        current document.
     *
     * @return The value of the property or Optional.empty()
     *
     * These properties are used to store extra data about
     * individual citation. In particular, the `pageInfo` part.
     *
     */
    public static Optional<String> getUserDefinedStringPropertyValue(XTextDocument doc, String property)
        throws
        WrappedTargetException {

        Optional<XPropertySet> ps = (getUserDefinedPropertiesAsXPropertyContainer(doc)
                                     .flatMap(DocumentConnection::asXPropertySet));
        if (ps.isEmpty()) {
            throw new RuntimeException("getting UserDefinedProperties as XPropertySet failed");
        }
        try {
            String v = ps.get().getPropertyValue(property).toString();
            return Optional.ofNullable(v);
        } catch (UnknownPropertyException ex) {
            return Optional.empty();
        }
    }

    /**
     * @param property Name of a custom document property in the
     *        current document. Created if does not exist yet.
     *
     * @param value The value to be stored.
     */
    public static void setOrCreateUserDefinedStringPropertyValue(XTextDocument doc,
                                                                 String property,
                                                                 String value)
        throws
        NotRemoveableException,
        PropertyExistException,
        IllegalTypeException,
        IllegalArgumentException,
        PropertyVetoException,
        WrappedTargetException {

        Objects.requireNonNull(property);
        Objects.requireNonNull(value);

        Optional<XPropertyContainer> xPropertyContainer =
            getUserDefinedPropertiesAsXPropertyContainer(doc);

        if (xPropertyContainer.isEmpty()) {
            throw new RuntimeException("getUserDefinedPropertiesAsXPropertyContainer failed");
        }

        Optional<XPropertySet> ps = xPropertyContainer.flatMap(DocumentConnection::asXPropertySet);
        if (ps.isEmpty()) {
            throw new RuntimeException("asXPropertySet failed");
        }

        XPropertySetInfo psi = ps.get().getPropertySetInfo();

        if (psi.hasPropertyByName(property)) {
            try {
                ps.get().setPropertyValue(property, value);
                return;
            } catch (UnknownPropertyException ex) {
                // fall through to addProperty
            }
        }

        xPropertyContainer.get().addProperty(property,
                                             com.sun.star.beans.PropertyAttribute.REMOVEABLE,
                                             new Any(Type.STRING, value));
    }

    /**
     * @param property Name of a custom document property in the
     *        current document.
     */
    public static void removeUserDefinedProperty(XTextDocument doc, String property)
        throws
        NotRemoveableException,
        PropertyExistException,
        IllegalTypeException,
        IllegalArgumentException {

        Objects.requireNonNull(property);

        Optional<XPropertyContainer> xPropertyContainer =
            getUserDefinedPropertiesAsXPropertyContainer(doc);

        if (xPropertyContainer.isEmpty()) {
            throw new RuntimeException("getUserDefinedPropertiesAsXPropertyContainer failed");
        }

        try {
            xPropertyContainer.get().removeProperty(property);
        } catch (UnknownPropertyException ex) {
            LOGGER.warn(String.format("removeUserDefinedProperty(%s)"
                                      + " This property was not there to remove",
                                      property));
        }
    }

    /**
     * @throws NoDocumentException If cannot get reference marks
     *
     * Note: also used by `documentConnectionMissing` to test if
     * we have a working connection.
     *
     */
    public static XNameAccess getReferenceMarks(XTextDocument doc)
        throws
        NoDocumentException {

        XReferenceMarksSupplier supplier = unoQI(XReferenceMarksSupplier.class, doc);

        try {
            return supplier.getReferenceMarks();
        } catch (DisposedException ex) {
            throw new NoDocumentException("getReferenceMarks failed with" + ex);
        }
    }

    /**
     * Provides access to bookmarks by name.
     */
    public static XNameAccess getBookmarks(XTextDocument doc)
        throws
        NoDocumentException {

        XBookmarksSupplier supplier = unoQI(XBookmarksSupplier.class, doc);
        try {
            return supplier.getBookmarks();
        } catch (DisposedException ex) {
            throw new NoDocumentException("getBookmarks failed with" + ex);
        }
    }

    /**
     *  @return An XNameAccess to find sections by name.
     */
    public static XNameAccess getTextSections(XTextDocument doc)
        throws
        NoDocumentException {

        XTextSectionsSupplier supplier = unoQI(XTextSectionsSupplier.class, doc);
        try {
            return supplier.getTextSections();
        } catch (DisposedException ex) {
            throw new NoDocumentException("getTextSections failed with" + ex);
        }
    }

    /**
     * Names of all reference marks.
     *
     * Empty list for nothing.
     */
    public static List<String> getReferenceMarkNames(XTextDocument doc)
        throws NoDocumentException {

        XNameAccess nameAccess = getReferenceMarks(doc);
        String[] names = nameAccess.getElementNames();
        if (names == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(names);
    }

    /**
     * @return null if name not found, or if the result does not
     *         support the XTextContent interface.
     */
    public static Optional<XTextContent> nameAccessGetTextContentByName(XNameAccess nameAccess,
                                                                        String name)
        throws
        WrappedTargetException {
        try {
            return optUnoQI(XTextContent.class, nameAccess.getByName(name));
        } catch (NoSuchElementException ex) {
            return Optional.empty();
        }
    }

    /**
     * Create a text cursor for a textContent.
     *
     * @return null if mark is null, otherwise cursor.
     *
     */
    public static Optional<XTextCursor> getTextCursorOfTextContent(XTextContent mark) {
        if (mark == null) {
            return Optional.empty();
        }
        XTextRange markAnchor = mark.getAnchor();
        if (markAnchor == null) {
            return Optional.empty();
        }
        return Optional.of(markAnchor.getText().createTextCursorByRange(markAnchor));
    }

    /**
     * Remove the named reference mark.
     *
     * Removes both the text and the mark itself.
     */
    public static void removeReferenceMark(XTextDocument doc, String name)
        throws
        WrappedTargetException,
        NoDocumentException,
        NoSuchElementException {

        XNameAccess xReferenceMarks = getReferenceMarks(doc);

        if (xReferenceMarks.hasByName(name)) {
            Optional<XTextContent> mark = nameAccessGetTextContentByName(xReferenceMarks, name);
            if (mark.isEmpty()) {
                return;
            }
            doc.getText().removeTextContent(mark.get());
        }
    }

    /**
     * Get the cursor positioned by the user.
     *
     */
    public static Optional<XTextViewCursor> getViewCursor(XTextDocument doc) {
        return (getCurrentController(doc)
                .flatMap(e -> optUnoQI(XTextViewCursorSupplier.class, e))
                .map(e -> e.getViewCursor()));
    }

    /**
     * Get the XTextRange corresponding to the named bookmark.
     *
     * @param name The name of the bookmark to find.
     * @return The XTextRange for the bookmark, or null.
     */
    public static Optional<XTextRange> getBookmarkRange(XTextDocument doc, String name)
        throws
        WrappedTargetException,
        NoDocumentException {

        XNameAccess nameAccess = getBookmarks(doc);
        return (nameAccessGetTextContentByName(nameAccess, name)
                .map(e -> e.getAnchor()));
    }

    /**
     *  @return reference mark as XTextContent, Optional.empty if not found.
     */
    public static Optional<XTextContent> getReferenceMarkAsTextContent(XTextDocument doc, String name)
        throws
        NoDocumentException,
        WrappedTargetException {

        XNameAccess nameAccess = getReferenceMarks(doc);
        return nameAccessGetTextContentByName(nameAccess, name);
    }

    /**
     *  XTextRange for the named reference mark, Optional.empty if not found.
     */
    public static Optional<XTextRange> getReferenceMarkRange(XTextDocument doc, String name)
        throws
        NoDocumentException,
        WrappedTargetException {
        return (getReferenceMarkAsTextContent(doc, name)
                .map(e -> e.getAnchor()));
    }

    /**
     * Insert a new instance of a service at the provided cursor
     * position.
     *
     * @param service For example
     *                 "com.sun.star.text.ReferenceMark",
     *                 "com.sun.star.text.Bookmark" or
     *                 "com.sun.star.text.TextSection".
     *
     *                 Passed to this.asXMultiServiceFactory().createInstance(service)
     *                 The result is expected to support the
     *                 XNamed and XTextContent interfaces.
     *
     * @param name     For the ReferenceMark, Bookmark, TextSection.
     *                 If the name is already in use, LibreOffice
     *                 may change the name.
     *
     * @param range   Marks the location or range for
     *                the thing to be inserted.
     *
     * @param absorb ReferenceMark, Bookmark and TextSection can
     *               incorporate a text range. If absorb is true,
     *               the text in the range becomes part of the thing.
     *               If absorb is false,  the thing is
     *               inserted at the end of the range.
     *
     * @return The XNamed interface, in case we need to check the actual name.
     *
     */
    private static XNamed insertNamedTextContent(XTextDocument doc,
                                                 String service,
                                                 String name,
                                                 XTextRange range,
                                                 boolean absorb)
        throws
        CreationException {

        XMultiServiceFactory msf = unoQI(XMultiServiceFactory.class, doc);

        Object xObject;
        try {
            xObject = msf.createInstance(service);
        } catch (Exception e) {
            throw new CreationException(e.getMessage());
        }

        XNamed xNamed = unoQI(XNamed.class, xObject);
        xNamed.setName(name);

        // get XTextContent interface
        XTextContent xTextContent = unoQI(XTextContent.class, xObject);
        range.getText().insertTextContent(range, xTextContent, absorb);
        return xNamed;
    }

    /**
     * Insert a new reference mark at the provided cursor
     * position.
     *
     * The text in the cursor range will be the text with gray
     * background.
     *
     * Note: LibreOffice 6.4.6.2 will create multiple reference marks
     *       with the same name without error or renaming.
     *       Its GUI does not allow this,
     *       but we can create them programmatically.
     *       In the GUI, clicking on any of those identical names
     *       will move the cursor to the same mark.
     *
     * @param name     For the reference mark.
     * @param range    Cursor marking the location or range for
     *                 the reference mark.
     */
    public static XNamed insertReferenceMark(XTextDocument doc,
                                             String name,
                                             XTextRange range,
                                             boolean absorb)
        throws
        CreationException {
        return insertNamedTextContent(doc,
                                      "com.sun.star.text.ReferenceMark",
                                      name,
                                      range,
                                      absorb);
    }

    /**
     * Insert a bookmark with the given name at the cursor provided,
     * or with another name if the one we asked for is already in use.
     *
     * In LibreOffice the another name is in "{name}{number}" format.
     *
     * @param name     For the bookmark.
     * @param range    Cursor marking the location or range for
     *                 the bookmark.
     * @param absorb   Shall we incorporate range?
     *
     * @return The XNamed interface of the bookmark.
     *
     *         result.getName() should be checked by the
     *         caller, because its name may differ from the one
     *         requested.
     */
    public static XNamed insertBookmark(XTextDocument doc,
                                        String name,
                                        XTextRange range,
                                        boolean absorb)
        throws
        IllegalArgumentException,
        CreationException {

        return insertNamedTextContent(doc,
                                      "com.sun.star.text.Bookmark",
                                      name,
                                      range,
                                      absorb);
    }

    /**
     * Insert a clickable cross-reference to a reference mark,
     * with a label containing the target's page number.
     *
     * May need a documentConnection.refresh() after, to update
     * the text shown.
     */
    public static void insertReferenceToPageNumberOfReferenceMark(XTextDocument doc,
                                                                  String referenceMarkName,
                                                                  XTextRange cursor)
        throws
        CreationException,
        UnknownPropertyException,
        PropertyVetoException,
        WrappedTargetException {

        // based on: https://wiki.openoffice.org/wiki/Documentation/DevGuide/Text/Reference_Marks
        XMultiServiceFactory msf = unoQI(XMultiServiceFactory.class, doc);
        // Create a 'GetReference' text field to refer to the reference mark we just inserted,
        // and get it's XPropertySet interface
        XPropertySet xFieldProps;
        try {
            String name = "com.sun.star.text.textfield.GetReference";
            xFieldProps = (XPropertySet) unoQI(XPropertySet.class,
                                               msf.createInstance(name));
        } catch (Exception e) {
            throw new CreationException(e.getMessage());
        }

        // Set the SourceName of the GetReference text field to the referenceMarkName
        xFieldProps.setPropertyValue("SourceName", referenceMarkName);

        // specify that the source is a reference mark (could also be a footnote,
        // bookmark or sequence field)
        xFieldProps.setPropertyValue("ReferenceFieldSource",
                                     new Short(ReferenceFieldSource.REFERENCE_MARK));

        // We want the reference displayed as page number
        xFieldProps.setPropertyValue("ReferenceFieldPart",
                                     new Short(ReferenceFieldPart.PAGE));

        // Get the XTextContent interface of the GetReference text field
        XTextContent xRefContent = (XTextContent) unoQI(XTextContent.class, xFieldProps);

        // Insert the text field
        doc.getText().insertTextContent(cursor.getEnd(), xRefContent, false);
    }

    /**
     * Update TextFields, etc.
     */
    public static void refresh(XTextDocument doc) {
        // Refresh the document
        XRefreshable xRefresh = unoQI(XRefreshable.class, doc);
        xRefresh.refresh();
    }

    /**
     *  Create a text section with the provided name and insert it at
     *  the provided cursor.
     *
     *  @param name  The desired name for the section.
     *  @param range The location to insert at.
     *
     *  If an XTextSection by that name already exists,
     *  LibreOffice (6.4.6.2) creates a section with a name different from
     *  what we requested, in "Section {number}" format.
     */
    public static XNamed insertTextSection(XTextDocument doc,
                                           String name,
                                           XTextRange range,
                                           boolean absorb)
        throws
        IllegalArgumentException,
        CreationException {

        return insertNamedTextContent(doc,
                                      "com.sun.star.text.TextSection",
                                      name,
                                      range,
                                      absorb);
    }

    /**
     *  Get an XTextSection by name.
     */
    public static Optional<XTextSection> getTextSectionByName(XTextDocument doc,
                                                              String name)
        throws
        NoSuchElementException,
        WrappedTargetException {

        XTextSectionsSupplier supplier = unoQI(XTextSectionsSupplier.class, doc);

        return Optional.ofNullable((XTextSection)
                                   ((Any) supplier.getTextSections().getByName(name))
                                   .getObject());
    }

    /**
     *  If original is in a footnote, return a range containing
     *  the corresponding footnote marker.
     *
     *  Returns Optional.empty if not in a footnote.
     */
    public static Optional<XTextRange> getFootnoteMarkRange(XTextRange original) {
        XFootnote footer = unoQI(XFootnote.class, original.getText());
        if (footer != null) {
            // If we are inside a footnote,
            // find the linking footnote marker:
            // The footnote's anchor gives the correct position in the text:
            return Optional.ofNullable(footer.getAnchor());
        }
        return Optional.empty();
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

    /**
     * Test if two XTextRange values are comparable (i.e. they share
     * the same getText()).
     */
    public static boolean comparableRanges(XTextRange a,
                                           XTextRange b) {
        return a.getText() == b.getText();
    }

    /**
     * @return follows OO conventions, the opposite of java conventions:
     *  1 if (a &lt; b), 0 if same start, (-1) if (b &lt; a)
     */
    private static int ooCompareRegionStarts(XTextRange a,
                                             XTextRange b) {
        if (!comparableRanges(a, b)) {
            throw new RuntimeException("ooCompareRegionStarts: got incomparable regions");
        }
        final XTextRangeCompare compare = unoQI(XTextRangeCompare.class,
                                                a.getText());
        return compare.compareRegionStarts(a, b);
    }

    /**
     * @return follows OO conventions, the opposite of java conventions:
     *  1 if  (a &lt; b), 0 if same start, (-1) if (b &lt; a)
     */
    private static int ooCompareRegionEnds(XTextRange a,
                                           XTextRange b) {
        if (!comparableRanges(a, b)) {
            throw new RuntimeException("ooCompareRegionEnds: got incomparable regions");
        }
        final XTextRangeCompare compare = unoQI(XTextRangeCompare.class,
                                                a.getText());
        return compare.compareRegionEnds(a, b);
    }

    /**
     * @return follows java conventions
     *
     * 1 if  (a &gt; b); (-1) if (a &lt; b)
     */
    public static int javaCompareRegionStarts(XTextRange a,
                                              XTextRange b) {
        return (-1) * ooCompareRegionStarts(a, b);
    }

    /**
     * @return follows java conventions
     *
     * 1 if  (a &gt; b); (-1) if (a &lt; b)
     */
    public static int javaCompareRegionEnds(XTextRange a,
                                            XTextRange b) {
        return (-1) * ooCompareRegionEnds(a, b);
    }

    /**
     * unoQI : short for UnoRuntime.queryInterface
     *
     * @return A reference to the requested UNO interface type if available,
     *         otherwise null
     */
    private static <T> T unoQI(Class<T> zInterface, Object object) {
        return UnoRuntime.queryInterface(zInterface, object);
    }

    private static <T> Optional<T> optUnoQI(Class<T> zInterface, Object object) {
        return Optional.ofNullable(UnoRuntime.queryInterface(zInterface, object));
    }

} // end DocumentConnection
