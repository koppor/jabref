package org.jabref.logic.layout;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

class LayoutHelperTest {

    private final LayoutFormatterPreferences layoutFormatterPreferences =
            mock(LayoutFormatterPreferences.class);
    private final JournalAbbreviationRepository abbreviationRepository =
            mock(JournalAbbreviationRepository.class);

    @Test
    void backslashDoesNotTriggerException() {
        StringReader stringReader = new StringReader("\\");
        LayoutHelper layoutHelper =
                new LayoutHelper(stringReader, layoutFormatterPreferences, abbreviationRepository);
        assertThrows(IOException.class, layoutHelper::getLayoutFromText);
    }

    @Test
    void unbalancedBeginEndIsParsed() throws Exception {
        StringReader stringReader = new StringReader("\\begin{doi}, DOI: \\doi");
        LayoutHelper layoutHelper =
                new LayoutHelper(stringReader, layoutFormatterPreferences, abbreviationRepository);
        Layout layout = layoutHelper.getLayoutFromText();
        assertNotNull(layout);
    }

    @Test
    void minimalExampleWithDoiGetsParsed() throws Exception {
        StringReader stringReader = new StringReader("\\begin{doi}, DOI: \\doi\\end{doi}");
        LayoutHelper layoutHelper =
                new LayoutHelper(stringReader, layoutFormatterPreferences, abbreviationRepository);
        Layout layout = layoutHelper.getLayoutFromText();
        assertNotNull(layout);
    }
}
