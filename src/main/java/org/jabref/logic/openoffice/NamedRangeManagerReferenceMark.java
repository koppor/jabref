package org.jabref.logic.openoffice;

import java.util.List;
import java.util.Optional;

import org.jabref.model.openoffice.CreationException;
import org.jabref.model.openoffice.NamedRange;
import org.jabref.model.openoffice.NamedRangeManager;
import org.jabref.model.openoffice.NoDocumentException;

import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;

public class NamedRangeManagerReferenceMark implements NamedRangeManager {

    @Override
    public NamedRange create(XTextDocument doc,
                             String refMarkName,
                             XTextCursor position,
                             boolean insertSpaceAfter,
                             boolean withoutBrackets)
        throws
        CreationException {
        return NamedRangeReferenceMark.create(doc,
                                              refMarkName,
                                              position,
                                              insertSpaceAfter,
                                              withoutBrackets);
    }

    @Override
    public List<String> getUsedNames(XTextDocument doc)
        throws
        NoDocumentException {
        return UnoReferenceMark.getListOfNames(doc);
    }

    @Override
    public Optional<NamedRange> getFromDocument(XTextDocument doc,
                                                String refMarkName)
        throws
        NoDocumentException,
        WrappedTargetException {
        return (NamedRangeReferenceMark
                .getFromDocument(doc, refMarkName)
                .map(x -> x));
    }
}

