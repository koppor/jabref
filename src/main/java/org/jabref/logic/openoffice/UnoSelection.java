package org.jabref.logic.openoffice;

import java.util.Optional;

import com.sun.star.lang.XServiceInfo;
import com.sun.star.text.XTextDocument;
import com.sun.star.view.XSelectionSupplier;

/**
 * Selection in the document.
 */
public class UnoSelection {

    private UnoSelection() { }

    private static Optional<XSelectionSupplier> getSelectionSupplier(XTextDocument doc) {
        return (Optional.ofNullable(doc)
                .map(e -> UnoTextDocument.getCurrentController(e))
                .flatMap(e -> UnoCast.optUnoQI(XSelectionSupplier.class, e)));
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
                .flatMap(e -> UnoCast.optUnoQI(XServiceInfo.class, e)));
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
}
