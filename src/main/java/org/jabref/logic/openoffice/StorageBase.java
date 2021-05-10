package org.jabref.logic.openoffice;

import java.util.List;
import java.util.Optional;

import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;

public class StorageBase {

    interface HasName {
        public String getName();
    }

    interface HasTextRange {

        /**
         * @return Optional.empty if the mark is missing from the document.
         */
        public Optional<XTextRange> getMarkRange(DocumentConnection documentConnection)
            throws
            NoDocumentException,
            WrappedTargetException;

        /**
         * Cursor for the reference marks as is, not prepared for filling,
         * but does not need cleanFillCursorForCitationGroup either.
         */
        public Optional<XTextCursor> getRawCursor(DocumentConnection documentConnection)
            throws
            NoDocumentException,
            WrappedTargetException,
            CreationException;

        /**
         * Get a cursor for filling in text.
         *
         * Must be followed by cleanFillCursor()
         */
        public XTextCursor getFillCursor(DocumentConnection documentConnection)
            throws
            NoDocumentException,
            WrappedTargetException,
            CreationException;

        /**
         * Remove brackets, but if the result would become empty, leave
         * them; if the result would be a single characer, leave the left bracket.
         *
         */
        public void cleanFillCursor(DocumentConnection documentConnection)
            throws
            NoDocumentException,
            WrappedTargetException,
            CreationException;

        /**
         *  Note: create is in NamedRangeManager
         */
        public void removeFromDocument(XTextDocument doc)
            throws
            WrappedTargetException,
            NoDocumentException,
            NoSuchElementException;
    }

    public interface NamedRange extends HasName, HasTextRange {
        // nothing new here
    }

    interface NamedRangeManager {
        public NamedRange create(DocumentConnection documentConnection,
                                 String refMarkName,
                                 XTextCursor position,
                                 boolean insertSpaceAfter,
                                 boolean withoutBrackets)
            throws
            CreationException;

        public List<String> getUsedNames(XTextDocument doc)
            throws
            NoDocumentException;

        public Optional<NamedRange> getFromDocument(XTextDocument doc,
                                                    String refMarkName)
            throws
            NoDocumentException,
            WrappedTargetException;
    }
}
