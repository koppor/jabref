package org.jabref.logic.openoffice;

import java.util.List;

import org.jabref.logic.JabRefException;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.logic.oostyle.OOProcess;
import org.jabref.model.database.BibDatabase;

import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextDocument;

/*
 * Update document: citation marks and bibliography
 */
public class Update {

    /*
     * @return unresolvedKeys
     */
    public static List<String> updateDocument(XTextDocument doc,
                                              OOFrontend fr,
                                              List<BibDatabase> databases,
                                              OOBibStyle style,
                                              FunctionalTextViewCursor fcursor,
                                              boolean doUpdateBibliography,
                                              boolean alwaysAddCitedOnPages)
        throws
        CreationException,
        JabRefException,
        NoDocumentException,
        NoSuchElementException,
        PropertyVetoException,
        UnknownPropertyException,
        WrappedTargetException,
        com.sun.star.lang.IllegalArgumentException {

        final boolean useLockControllers = true;

        fr.imposeGlobalOrder(doc, fcursor);
        OOProcess.ProduceCitationMarkersResult x =
            OOProcess.produceCitationMarkers(fr.citationGroups, databases, style);

        try {
            if (useLockControllers) {
                UnoScreenRefresh.lockControllers(doc);
            }

            UpdateCitationMarkers.applyNewCitationMarkers(doc, fr, x.citMarkers, style);

            if (doUpdateBibliography) {
                UpdateBibliography.rebuildBibTextSection(doc,
                                                         fr,
                                                         x.getBibliography(),
                                                         style,
                                                         alwaysAddCitedOnPages);
            }
            return x.getUnresolvedKeys();
        } finally {
            if (useLockControllers && UnoScreenRefresh.hasControllersLocked(doc)) {
                UnoScreenRefresh.unlockControllers(doc);
            }
        }
    }
}
