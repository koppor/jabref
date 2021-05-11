package org.jabref.logic.openoffice;

import java.util.Optional;

import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextViewCursor;
import com.sun.star.text.XTextViewCursorSupplier;

public class UnoCursor {

    private UnoCursor() { }

    /**
     * Get the cursor positioned by the user.
     */
    public static Optional<XTextViewCursor> getViewCursor(XTextDocument doc) {
        return (UnoTextDocument.getCurrentController(doc)
                .flatMap(e -> UnoCast.optUnoQI(XTextViewCursorSupplier.class, e))
                .map(e -> e.getViewCursor()));
    }

    /**
     * Create a text cursor for a textContent.
     *
     * @return null if mark is null, otherwise cursor.
     *
     */
    public static Optional<XTextCursor> getTextCursorOfTextContentAnchor(XTextContent mark) {
        if (mark == null) {
            return Optional.empty();
        }
        XTextRange markAnchor = mark.getAnchor();
        if (markAnchor == null) {
            return Optional.empty();
        }
        return Optional.of(markAnchor.getText().createTextCursorByRange(markAnchor));
    }
}
