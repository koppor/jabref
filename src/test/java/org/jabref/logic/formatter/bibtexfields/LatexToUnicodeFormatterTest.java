package org.jabref.logic.formatter.bibtexfields;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatexToUnicodeFormatterTest {

    final LatexToUnicodeFormatter formatter = new LatexToUnicodeFormatter();

    @Test
    void formatExample() {
        assertEquals("Mönch", formatter.format(formatter.getExampleInput()));
    }

    @ParameterizedTest
    @CsvSource({
            "aaa, aaa",
            "ä, {\\\"{a}}",
            "Ä, {\\\"{A}}",
            "\\mbox{-}, \\mbox{-}",
            // See https://github.com/JabRef/jabref/pull/1464
            "\uD835\uDC61\uD835\uDC52\uD835\uDC65\uD835\uDC61, \\textit{text}",
            "$, \\$",
            "σ, $\\sigma$",
            "A 32 mA ΣΔ-modulator, A 32~{mA} {$\\Sigma\\Delta$}-modulator",
            // See #1464
            "χ, $\\chi$",
            // Bug #1264
            "Š, {\\v{S}}",
            "ï, \\\"{i}",
            // this might look strange in the test, but is actually a correct translation and renders identically to the above example in the UI
            "ı̈, \\\"{\\i}",
            "ï, {\\\"{i}}",
            "Ï, \\\"{I}",
            "Łęski, \\L\\k{e}ski",
            // doubleCombiningAccents
            "ώ, $\\acute{\\omega}$",
            "ḩ, {\\c{h}}",
            // This is not a standard LaTeX command. It is debatable why we should convert this.
            // "a͍, \\spreadlips{a}",
            // unknown command
            "\\aaaa, \\aaaa",
            // unknown command
            "\\aaaa{bbbb}, \\aaaa{bbbb}",
            // unknown command
            "\\aaaa{}, \\aaaa{}",
            "Montaña, Monta\\~{n}a",
            "Maliński, Mali\\'{n}ski",
            "MaliŃski, Mali\\'{N}ski",
            "Maliński, Mali\\'nski",
            "MaliŃski, Mali\\'Nski",
            "Mali'nski, Mali'nski",
            "Mali'Nski, Mali'Nski",
            "L'oscillation, L'oscillation",
            "O'Connor, O'Connor",
            "Lorem ipsum_lorem ipsum, Lorem ipsum_lorem ipsum",
            "Lorem ipsum_(lorem ipsum), Lorem ipsum_{lorem ipsum}",
            "1ˢᵗ, 1\\textsuperscript{st}",
            "2ⁿᵈ, 2\\textsuperscript{nd}",
            "3ʳᵈ, 3\\textsuperscript{rd}",
            "4ᵗʰ, 4\\textsuperscript{th}",
            "9ᵗʰ, 9\\textsuperscript{th}",
            // Sanskrit
            "Puṇya-pattana-vidyā-pı̄ṭhādhi-kṛtaiḥ prā-kaśyaṃ nı̄taḥ, Pu\\d{n}ya-pattana-vidy{\\={a}}-p{\\i{\\={}}}\\d{t}h{\\={a}}dhi-k\\d{r}tai\\d{h} pr{\\={a}}-ka{{\\'{s}}}ya\\d{m} n{\\i{\\={}}}ta\\d{h}"
    })
    void test(String expected, String input) {
        assertEquals(expected, formatter.format(input));
    }
}
