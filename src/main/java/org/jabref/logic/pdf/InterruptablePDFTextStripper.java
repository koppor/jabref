package org.jabref.logic.pdf;

import javafx.beans.property.ReadOnlyBooleanProperty;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

public class InterruptablePDFTextStripper extends PDFTextStripper {
    private final ReadOnlyBooleanProperty shutdownSignal;

    public InterruptablePDFTextStripper(ReadOnlyBooleanProperty shutdownSignal) {
        super();
        this.shutdownSignal = shutdownSignal;
    }

    @Override
    public void processPage(PDPage page) throws IOException {
        if (shutdownSignal.get()) {
            return;
        }

        super.processPage(page);
    }
}
