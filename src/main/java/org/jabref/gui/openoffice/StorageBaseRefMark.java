package org.jabref.gui.openoffice;

import java.util.List;
import java.util.Optional;

import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XText;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StorageBaseRefMark implements StorageBase.NamedRange {

    private static final String ZERO_WIDTH_SPACE = "\u200b";

    // for debugging we may want visible bracket
    private static final boolean
    REFERENCE_MARK_USE_INVISIBLE_BRACKETS = true; // !debug;

    public static final String
    REFERENCE_MARK_LEFT_BRACKET = REFERENCE_MARK_USE_INVISIBLE_BRACKETS ? ZERO_WIDTH_SPACE : "<";

    public static final String
    REFERENCE_MARK_RIGHT_BRACKET = REFERENCE_MARK_USE_INVISIBLE_BRACKETS ? ZERO_WIDTH_SPACE : ">";

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageBaseRefMark.class);

    private String id; /* reference mark name */

    private StorageBaseRefMark(String id) {
        this.id = id;
    }

    String getId() {
        return id;
    }

    /**
     *  Insert {@code n} spaces in a way that reference
     *  marks just before or just after the cursor are not affected.
     *
     *  This is based on the observation, that starting two
     *  new paragraphs separates us from a reference mark on either side.
     *
     *  The pattern used is:
     *  {@code safeInsertSpaces(n): para, para, left, space(n), right-delete, left(n), left-delete}
     *
     *  @param position Where to insert (at position.getStart())
     *  @param n  Number of spaces to insert.
     *
     *  @return a new cursor, covering the just-inserted spaces.
     *
     *  This could be generalized to insert arbitrary text safely
     *  between two reference marks. But we do not need that now.
     */
    private static XTextCursor
    safeInsertSpacesBetweenReferenceMarks(XTextRange position, int n) {
        // Start with an empty cursor at position.getStart();
        XText text = position.getText();
        XTextCursor cursor = text.createTextCursorByRange(position.getStart());
        text.insertString(cursor, "\r\r", false); // para, para
        cursor.goLeft((short) 1, false); // left
        text.insertString(cursor, " ".repeat(n), false); // space(n)
        cursor.goRight((short) 1, true);
        cursor.setString(""); // right-delete
        cursor.goLeft((short) n, false); // left(n)
        cursor.goLeft((short) 1, true);
        cursor.setString(""); // left-delete
        cursor.goRight((short) n, true);
        return cursor;
    }

    private static void createReprInDocument(DocumentConnection documentConnection,
                                             String refMarkName,
                                             XTextCursor position,
                                             boolean insertSpaceAfter,
                                             boolean withoutBrackets)
        throws
        CreationException {
        // The cursor we received: we push it before us.
        position.collapseToEnd();

        XTextCursor cursor = safeInsertSpacesBetweenReferenceMarks(position.getEnd(), 2);

        // cursors before the first and after the last space
        XTextCursor cursorBefore = cursor.getText().createTextCursorByRange(cursor.getStart());
        XTextCursor cursorAfter = cursor.getText().createTextCursorByRange(cursor.getEnd());

        cursor.collapseToStart();
        cursor.goRight((short) 1, false);
        // now we are between two spaces

        final String left = StorageBaseRefMark.REFERENCE_MARK_LEFT_BRACKET;
        final String right = StorageBaseRefMark.REFERENCE_MARK_RIGHT_BRACKET;
        final short leftLength = (short) left.length();
        final short rightLength = (short) right.length();
        String bracketedContent = (withoutBrackets
                                   ? ""
                                   : left + right);

        cursor.getText().insertString(cursor,
                                      bracketedContent,
                                      true);

        documentConnection.insertReferenceMark(refMarkName,
                                               cursor,
                                               true /* absorb */);

        cursorBefore.goRight((short) 1, true);
        cursorBefore.setString("");
        if (!insertSpaceAfter) {
            cursorAfter.goLeft((short) 1, true);
            cursorAfter.setString("");
        }
    }

    private static StorageBaseRefMark create(DocumentConnection documentConnection,
                                            String refMarkName,
                                            XTextCursor position,
                                            boolean insertSpaceAfter,
                                            boolean withoutBrackets)
        throws
        CreationException {
        createReprInDocument(documentConnection,
                             refMarkName,
                             position,
                             insertSpaceAfter,
                             withoutBrackets);
        return new StorageBaseRefMark(refMarkName);
    }

    /**
     * @return Optional.empty if there is no corresponding range.
     */
    private static Optional<StorageBaseRefMark> getFromDocument(DocumentConnection documentConnection,
                                                                String refMarkName)
        throws
        NoDocumentException,
        WrappedTargetException {
        return (documentConnection.getReferenceMarkRange(refMarkName)
                .map(e -> new StorageBaseRefMark(refMarkName)));
    }

    /*
     * Remove it from the document.
     *
     * See: removeCitationGroups
     */
    @Override
    public void removeFromDocument(DocumentConnection documentConnection)
        throws
        WrappedTargetException,
        NoDocumentException,
        NoSuchElementException {
        documentConnection.removeReferenceMark(this.getName());
    }

    @Override
    public String getName() {
        return id;
    }

    /*
     * ranges controlled by citation groups should not overlap with each other.
     *
     * @return Optional.empty if the reference mark is missing.
     *
     * See: getReferenceMarkRange
     */
    @Override
    public Optional<XTextRange> getMarkRange(DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException {
        String name = this.getName();
        return documentConnection.getReferenceMarkRange(name);
    }

    /**
     * Cursor for the reference marks as is, not prepared for filling,
     * but does not need cleanFillCursorForCitationGroup either.
     *
     * @return null if reference mark is missing from the document,
     *         otherwise an XTextCursor for getMarkRange
     *
     * See: getRawCursorForCitationGroup
     */
    @Override
    public Optional<XTextCursor> getRawCursor(DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {

        String name = this.getName();
        XTextCursor full = null;

        Optional<XTextContent> markAsTextContent = (documentConnection
                                                    .getReferenceMarkAsTextContent(name));

        if (markAsTextContent.isEmpty()) {
            String msg = String.format("getRawCursor: markAsTextContent(%s).isEmpty()", name);
            LOGGER.warn(msg);
        }

        full = DocumentConnection.getTextCursorOfTextContent(markAsTextContent.get());
        if (full == null) {
            String msg = "getRawCursor: full == null";
            LOGGER.warn(msg);
            return Optional.empty();
        }
        return Optional.ofNullable(full);
    }

    /**
     * See: getFillCursorForCitationGroup
     */
    @Override
    public XTextCursor getFillCursor(DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {

        String name = this.getName();

        final boolean debugThisFun = false;
        final String left = StorageBaseRefMark.REFERENCE_MARK_LEFT_BRACKET;
        final String right = StorageBaseRefMark.REFERENCE_MARK_RIGHT_BRACKET;
        final short leftLength = (short) left.length();
        final short rightLength = (short) right.length();

        XTextCursor full = null;
        String fullText = null;
        for (int i = 1; i <= 2; i++) {
            XTextContent markAsTextContent =
                documentConnection.getReferenceMarkAsTextContent(name).orElse(null);

            if (markAsTextContent == null) {
                String msg = String.format("getFillCursor:"
                                           + " markAsTextContent(%s) == null (attempt %d)",
                                           name,
                                           i);
                throw new RuntimeException(msg);
            }

            full = DocumentConnection.getTextCursorOfTextContent(markAsTextContent);
            if (full == null) {
                String msg = String.format("getFillCursor: full == null (attempt %d)", i);
                throw new RuntimeException(msg);
            }

            fullText = full.getString();

            if (debugThisFun) {
                System.out.printf("getFillCursor: fulltext = '%s'%n", fullText);
            }

            if (fullText.length() >= 2) {
                break;
            } else {
                // (fullText.length() < 2)
                if (i == 2) {
                    String msg = String.format("getFillCursor:"
                                               + " (fullText.length() < 2) (attempt %d)",
                                               i);
                    throw new RuntimeException(msg);
                }
                // too short, recreate
                if (debugThisFun) {
                    System.out.println("getFillCursor: too short, recreate");
                }
                full.setString("");
                try {
                    documentConnection.removeReferenceMark(name);
                } catch (NoSuchElementException ex) {
                    String msg = String.format("getFillCursor got NoSuchElementException"
                                               + " for '%s'",
                                               name);
                    LOGGER.warn(msg);
                }
                createReprInDocument(documentConnection,
                                     name,
                                     full,
                                     false, /* insertSpaceAfter */
                                     false  /* withoutBrackets */);
            }
        }

        if (full == null) {
            throw new RuntimeException("getFillCursorFor: full == null (after loop)");
        }
        if (fullText == null) {
            throw new RuntimeException("getFillCursor: fullText == null (after loop)");
        }

        // we have at least two characters inside
        XTextCursor alpha = full.getText().createTextCursorByRange(full);
        alpha.collapseToStart();
        XTextCursor omega = full.getText().createTextCursorByRange(full);
        omega.collapseToEnd();

        XTextCursor beta = full.getText().createTextCursorByRange(full);
        beta.collapseToStart();
        beta.goRight((short) 1, false);
        beta.goRight((short) (fullText.length() - 2), true);
        beta.setString(left + right);
        beta.collapseToEnd();
        beta.goLeft(rightLength, false);
        // drop the initial character
        alpha.goRight((short) 1, true);
        alpha.setString("");
        // drop the last character
        omega.goLeft((short) 1, true);
        omega.setString("");
        return beta;
    }

    /**
     * Remove brackets, but if the result would become empty, leave
     * them; if the result would be a single characer, leave the left bracket.
     *
     * See: cleanFillCursorForCitationGroup
     */
    @Override
    public void cleanFillCursor(DocumentConnection documentConnection)
        throws
        NoDocumentException,
        WrappedTargetException,
        CreationException {

        // alwaysRemoveBrackets : full compatibility with JabRef 5.2:
        // brackets are temporary, only exist between getFillCursor
        // and cleanFillCursor.
        final boolean alwaysRemoveBrackets = true;
        // removeBracketsFromEmpty is intended to force removal if we
        //       are working on an "Empty citation" (INVISIBLE_CIT).
        final boolean removeBracketsFromEmpty = false;

        final String left = REFERENCE_MARK_LEFT_BRACKET;
        final String right = REFERENCE_MARK_RIGHT_BRACKET;
        final short leftLength = (short) left.length();
        final short rightLength = (short) right.length();

        String name = this.getName();

        XTextCursor full = this.getRawCursor(documentConnection).orElseThrow(RuntimeException::new);
        final String fullText = full.getString();
        final int fullTextLength = fullText.length();

        XTextCursor alpha = full.getText().createTextCursorByRange(full);
        alpha.collapseToStart();

        XTextCursor beta = full.getText().createTextCursorByRange(full);
        beta.collapseToStart();
        beta.goRight(leftLength, false);

        XTextCursor omega = full.getText().createTextCursorByRange(full);
        omega.collapseToEnd();

        if (!fullText.startsWith(left)) {
            String msg = String.format("cleanFillCursor:"
                                       + " (%s) does not start with REFERENCE_MARK_LEFT_BRACKET",
                                       name);
            throw new RuntimeException(msg);
        }

        if (!fullText.endsWith(right)) {
            String msg = String.format("cleanFillCursor:"
                                       + " (%s) does not end with REFERENCE_MARK_RIGHT_BRACKET",
                                       name);
            throw new RuntimeException(msg);
        }

        final int contentLength = (fullTextLength - (leftLength + rightLength));
        if (contentLength < 0) {
            String msg = String.format("cleanFillCursor: length(%s) < 0", name);
            throw new RuntimeException(msg);
        }

        boolean removeRight = ((contentLength >= 1)
                               || ((contentLength == 0) && removeBracketsFromEmpty)
                               || alwaysRemoveBrackets);

        boolean removeLeft = ((contentLength >= 2)
                              || ((contentLength == 0) && removeBracketsFromEmpty)
                              || alwaysRemoveBrackets);

        if (removeRight) {
            omega.goLeft(rightLength, true);
            omega.setString("");
        }

        if (removeLeft) {
            alpha.goRight(leftLength, true);
            alpha.setString("");
        }
    }

    private static List<String> getUsedNames(DocumentConnection documentConnection)
        throws
        NoDocumentException {
        return documentConnection.getReferenceMarkNames();
    }

    public static class Manager implements StorageBase.NamedRangeManager {
        @Override
        public StorageBase.NamedRange create(DocumentConnection documentConnection,
                                             String refMarkName,
                                             XTextCursor position,
                                             boolean insertSpaceAfter,
                                             boolean withoutBrackets)
            throws
            CreationException {
            return StorageBaseRefMark.create(documentConnection,
                                             refMarkName,
                                             position,
                                             insertSpaceAfter,
                                             withoutBrackets);
        }

        @Override
        public List<String> getUsedNames(DocumentConnection documentConnection)
            throws
            NoDocumentException {
            return StorageBaseRefMark.getUsedNames(documentConnection);
        }

        @Override
        public Optional<StorageBase.NamedRange> getFromDocument(DocumentConnection documentConnection,
                                                                String refMarkName)
            throws
            NoDocumentException,
            WrappedTargetException {
            return (StorageBaseRefMark
                    .getFromDocument(documentConnection, refMarkName)
                    .map(x -> x));
        }
    }
}
