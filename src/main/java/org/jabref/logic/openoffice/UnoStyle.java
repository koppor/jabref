package org.jabref.logic.openoffice;

import java.util.Optional;

import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNameContainer;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.style.XStyle;
import com.sun.star.style.XStyleFamiliesSupplier;
import com.sun.star.text.XTextDocument;

/**
 * Styles in the document.
 */
public class UnoStyle {

    private UnoStyle() { }

    private static Optional<XStyle> getStyleFromFamily(XTextDocument doc,
                                                       String familyName,
                                                       String styleName)
        throws
        NoSuchElementException,
        WrappedTargetException {

        XStyleFamiliesSupplier fss = UnoCast.unoQI(XStyleFamiliesSupplier.class, doc);
        XNameAccess fs = UnoCast.unoQI(XNameAccess.class, fss.getStyleFamilies());
        XNameContainer xFamily = UnoCast.unoQI(XNameContainer.class, fs.getByName(familyName));

        try {
            Object s = xFamily.getByName(styleName);
            XStyle xs = (XStyle) UnoCast.unoQI(XStyle.class, s);
            return Optional.ofNullable(xs);
        } catch (NoSuchElementException ex) {
            return Optional.empty();
        }
    }

    public static Optional<XStyle> getParagraphStyle(XTextDocument doc, String styleName)
        throws
        NoSuchElementException,
        WrappedTargetException {
        return getStyleFromFamily(doc, "ParagraphStyles", styleName);
    }

    public static Optional<XStyle> getCharacterStyle(XTextDocument doc, String styleName)
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
}
